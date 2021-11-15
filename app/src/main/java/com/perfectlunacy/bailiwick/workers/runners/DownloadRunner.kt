package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostFile
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.lang.Exception
import kotlin.io.path.Path

class DownloadRunner(val context: Context, val db: BailiwickDatabase, val ipfs: IPFS) {
    companion object {
        const val TAG = "DownloadRunner"
    }

    fun run() {
        // Wait for IPFS connection
        while(!ipfs.isConnected()) {
            Log.i(UploadRunner.TAG, "Waiting for ipfs to connect")
            Thread.sleep(500)
        }

        Log.i(TAG, "Refreshing ${peers.count()} peers")
        peers.forEach { peerId ->
            Log.i(TAG, "Refreshing peer: $peerId")
            iteration(peerId)
        }
    }

    val peers: List<PeerId>
        get() = db.userDao().all().map { it.peerId }

    private fun iteration(peerId: PeerId) {
        downloadIdentity(peerId)

        downloadManifest(peerId)
    }

    private fun downloadIdentity(peerId: PeerId) {
        if(db.identityDao().identitiesFor(peerId).isEmpty()) {
            Log.i(TAG, "Looking for identity for $peerId")
            val cipher = Keyring.encryptorForPeer(db.keyDao(), peerId, ValidatorFactory.jsonValidator())
            val pair = IpfsDeserializer.fromBailiwickFile(
                cipher,
                ipfs,
                peerId,
                db.sequenceDao(),
                "identity.json",
                IpfsIdentity::class.java)

            if (pair == null) {
                Log.w(TAG, "Could not download public identity for $peerId")
                return
            }

            val pubId = pair.first
            val cid = pair.second

            db.identityDao().insert(Identity(cid, peerId, pubId.name, pubId.profilePicCid))
        }
    }

    private fun downloadManifest(peerId: PeerId) {
        val manifestCipher = Keyring.encryptorForPeer(db.keyDao(), peerId, ValidatorFactory.jsonValidator())
        val maniPair = IpfsDeserializer.fromBailiwickFile(
            manifestCipher,
            ipfs,
            peerId,
            db.sequenceDao(),
            "manifest.json",
            IpfsManifest::class.java)

        if (maniPair == null) {
            Log.w(TAG, "Failed to locate Manifest for peer $peerId")
            return
        }

        val validator = ValidatorFactory.jsonValidator()
        val cipher = Keyring.encryptorForPeer(db.keyDao(), peerId, validator)
        val rsa = MultiCipher(listOf(RsaWithAesEncryptor(ipfs.privateKey, ipfs.publicKey), cipher), validator)
        val manifest = maniPair.first
        Log.i(TAG, "Trying ${manifest.actions.count()} Actions")
        manifest.actions.forEach { actionCid ->
            downloadAction(actionCid, peerId, rsa)
        }

        Log.i(TAG, "Trying ${manifest.feeds.count()} feeds")
        manifest.feeds.forEach { feedCid ->
            downloadFeedAndPosts(feedCid, peerId, cipher)
        }
    }

    private fun downloadAction(actionCid: ContentId, peerId: PeerId, cipher: Encryptor) {
        val actionPair = IpfsDeserializer.fromCid(cipher, ipfs, actionCid, IpfsAction::class.java) ?: return
        val action = actionPair.first

        Log.i(TAG, "Downloaded action! Processing")

        when(ActionType.valueOf(action.type)) {
            ActionType.Delete -> TODO()
            ActionType.UpdateKey -> {
                Log.i(TAG, "Processing UpdateKey action from $peerId")
                Keyring.storeAesKey(db.keyDao(), peerId, action.metadata["key"]!!)
            }
            ActionType.Introduce -> TODO()
        }
    }

    private fun downloadFeedAndPosts(feedCid: ContentId, peerId: PeerId, cipher: Encryptor) {
        val feedPair = IpfsDeserializer.fromCid(cipher, ipfs, feedCid, IpfsFeed::class.java) ?: return
        val feed = feedPair.first

        var identity = db.identityDao().findByCid(feed.identity)
        if (identity == null) {
            // Download the identity if we haven't already
            val idCipher = Keyring.encryptorForPeer(
                db.keyDao(),
                peerId,
                ValidatorFactory.jsonValidator(IpfsIdentity::class.java)
            )

            val ipfsIdentity = IpfsDeserializer.fromCid(idCipher, ipfs, feed.identity, IpfsIdentity::class.java)!!.first

            identity = Identity(feed.identity, peerId, ipfsIdentity.name, ipfsIdentity.profilePicCid)
            identity.id = db.identityDao().insert(identity)
        }

        Log.i(TAG, "Trying ${feed.posts.count()} posts")
        for (postCid in feed.posts) {
            downloadPost(postCid, identity, cipher)
        }
    }

    private fun downloadPost(postCid: ContentId, identity: Identity, cipher: Encryptor) {
        var post = db.postDao().findByCid(postCid)
        if (post != null) {
            Log.i(TAG, "Already downloaded post $postCid!")
            return
        }

        val ipfsPostPair = try {
            IpfsDeserializer.fromCid(cipher, ipfs, postCid, IpfsPost::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Post $postCid", e)
            return
        }

        if (ipfsPostPair == null) {
            Log.e(TAG, "Failed to process post $postCid")
            return
        }

        val ipfsPost = ipfsPostPair.first

        Log.i(TAG, "Downloaded post!")

        post = Post(
            identity.id,
            postCid,
            ipfsPost.timestamp,
            ipfsPost.parent_cid,
            ipfsPost.text,
            ipfsPost.signature
        )

        post.id = db.postDao().insert(post)

        ipfsPost.files.forEach {
            db.postFileDao().insert(PostFile(post.id, it.cid, it.mimeType))
        }

        db.postFileDao().filesFor(post.id).forEach { postFile ->
            val f = Path(context.filesDir.path, "bwcache", postFile.fileCid).toFile()
            if (!f.exists()) {
                // We need to download this file attachment
                val data = ipfs.getData(
                    postFile.fileCid,
                    600
                ) // TODO: InputStream to support larger files.
                Log.i(TAG, "Downloaded post file! Saving to bwcache")
                BufferedOutputStream(FileOutputStream(f)).use {
                    it.write(data)
                }
            }
        }
    }
}