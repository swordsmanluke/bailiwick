package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import kotlin.io.path.Path

class RefreshRunner(val context: Context, val peers: List<PeerId>, val db: BailiwickDatabase, val ipfs: IPFS, val ipns: IpnsCacheDao) {
    companion object {
        const val TAG = "RefreshRunner"
    }

    fun run() {
        peers.forEach { peerId ->
            iteration(peerId)
        }
    }

    private fun iteration(peerId: PeerId) {
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
        val cipher = Keyring.encryptorForPeer(db, peerId, validator)

        Log.i(TAG, "Trying ${manifest.feeds.count()} feeds")
        manifest.feeds.forEach { feedCid ->
            val feed = IpfsDeserializer.fromCid(cipher, ipfs, feedCid, IpfsFeed::class.java)
                ?: return@forEach

            var identity = db.identityDao().findByCid(feed.identity)
            if(identity == null) {
                // Download the identity if we haven't already
                val idCipher = Keyring.encryptorForPeer(db, peerId, ValidatorFactory.jsonValidator(IpfsIdentity::class.java))
                val ipfsIdentity = IpfsDeserializer.fromCid(idCipher, ipfs, feed.identity, IpfsIdentity::class.java)!!
                identity = Identity(feed.identity, peerId, ipfsIdentity.name, ipfsIdentity.profilePicCid)
                identity.id = db.identityDao().insert(identity)
            }

            Log.i(TAG, "Trying ${feed.posts.count()} posts")
            for(postCid in feed.posts) {
                var post = db.postDao().findByCid(postCid)
                if(post == null) {
                    val ipfsPost = IpfsDeserializer.fromCid(cipher, ipfs, postCid, IpfsPost::class.java)
                            ?: continue

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
                    if(!f.exists()) {
                        // We need to download this file attachment
                        val data = ipfs.getData(postFile.fileCid, 600) // TODO: InputStream to support larger files.
                        Log.i(TAG, "Downloaded post file! Saving to bwcache")
                        BufferedOutputStream(FileOutputStream(f)).use {
                            it.write(data)
                        }
                    }
                }
            }
        }
    }
}