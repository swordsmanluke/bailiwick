package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
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
import kotlin.io.path.Path

class DownloadRunner(val context: Context, val db: BailiwickDatabase, val ipfs: IPFS) {
    companion object {
        const val TAG = "RefreshRunner"
    }

    fun run() {
        Log.i(TAG, "Refreshing ${peers.count()} peers")
        peers.forEach { peerId ->
            Log.i(TAG, "Refreshing peer: $peerId")
            iteration(peerId)
        }
    }

    val peers: List<PeerId>
        get() = db.userDao().all().map { it.peerId }

    private fun iteration(peerId: PeerId) {
        ipfs.enhanceSwarm(peerId) // Try to connect this peer to our swarm
        Log.i(TAG, "Added $peerId to our swarm... maybe")

        val manifest = IpfsDeserializer
            .fromBailiwickFile(
                NoopEncryptor(),
                ipfs,
                peerId,
                "manifest.json",
                Manifest::class.java)

        if(manifest == null) {
            Log.w(TAG,"Failed to locate Manifest for peer $peerId")
            return
        }

        val validator = ValidatorFactory.jsonValidator()
        val cipher = Keyring.encryptorForPeer(db.keyDao(), peerId, validator)

        Log.i(TAG, "Trying ${manifest.feeds.count()} feeds")
        manifest.feeds.forEach { feedCid ->
            downloadFeedAndPosts(feedCid, peerId, cipher)
        }

        manifest.actions.forEach { actionCid ->
            downloadAction(actionCid, peerId, cipher)
        }
    }

    private fun downloadAction(actionCid: ContentId, peerId: PeerId, cipher: Encryptor) {
        val action = IpfsDeserializer.fromCid(cipher, ipfs, actionCid, IpfsAction::class.java) ?: return

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
        val feed = IpfsDeserializer.fromCid(cipher, ipfs, feedCid, IpfsFeed::class.java) ?: return

        var identity = db.identityDao().findByCid(feed.identity)
        if (identity == null) {
            // Download the identity if we haven't already
            val idCipher = Keyring.encryptorForPeer(
                db.keyDao(),
                peerId,
                ValidatorFactory.jsonValidator(IpfsIdentity::class.java)
            )
            val ipfsIdentity =
                IpfsDeserializer.fromCid(idCipher, ipfs, feed.identity, IpfsIdentity::class.java)!!
            identity =
                Identity(feed.identity, peerId, ipfsIdentity.name, ipfsIdentity.profilePicCid)
            identity.id = db.identityDao().insert(identity)
        }

        Log.i(TAG, "Trying ${feed.posts.count()} posts")
        for (postCid in feed.posts) {
            downloadPost(postCid, identity, cipher)
        }
    }

    private fun downloadPost(postCid: ContentId, identity: Identity, cipher: Encryptor) {
        var post = db.postDao().findByCid(postCid)
        if (post == null) {
            val ipfsPost = IpfsDeserializer.fromCid(cipher, ipfs, postCid, IpfsPost::class.java)
                ?: return

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