package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import java.util.*

class UploadRunner(val context: Context, val db: BailiwickDatabase, val ipfs: IPFS) {
    companion object {
        const val TAG = "UploadRunner"
    }

    private var dirty = false

    // TODO: LOTS of N+1 queries in here. Update our DAOs so we don't need so many.

    fun run() {
        val idsToSync = db.identityDao().all().filter { it.cid == null }
        val postsToSync = db.postDao().inNeedOfSync()

        db.circleDao().all().forEach { circle ->
            val cipher = Keyring.encryptorForCircle(db, circle.id)
            val postIds = db.circlePostDao().postsIn(circle.id)
            val syncPosts = postsToSync.filter { postIds.contains(it.id) }
            idsToSync.first{it.id == circle.identityId}.also { ident ->
                val cid = IpfsIdentity(ident.name, ident.profilePicCid ?: "").toIpfs(cipher, ipfs)
                db.identityDao().updateCid(ident.id, cid)
            }

            // Sync any required posts
            if(syncPosts.isNotEmpty()) {
                dirty = true
                db.circleDao().clearCid(circle.id)
                circle.cid = null
                uploadPostsToIpfs(syncPosts, cipher)
            }

            // Sync the Circle
            if(circle.cid == null) {
                dirty = true
                uploadCircleToIpfs(circle, cipher)
            }
        }

        // Now publish a new manifest if we changed anything
        if(dirty) { publishNewManifest() }
    }

    private fun publishNewManifest() {
        val manCid = Manifest(db.circleDao().all().mapNotNull { it.cid }, listOf()).toIpfs(
            NoopEncryptor(),
            ipfs
        )
        val seq = db.ipnsCacheDao().sequenceFor(ipfs.peerID) ?: 0

        var verCid = cidForDir("bw/${Bailiwick.VERSION}", seq)
        var bwCid = cidForDir("bw", seq)
        var rootCid = cidForDir("", seq)

        verCid = ipfs.addLinkToDir(verCid, "manifest.json", manCid)!!
        bwCid = ipfs.addLinkToDir(bwCid, Bailiwick.VERSION, verCid)!!
        rootCid = ipfs.addLinkToDir(rootCid, "bw", bwCid)!!

        val peerId = ipfs.peerID
        db.ipnsCacheDao().insert(IpnsCache(peerId, "", rootCid, seq + 1))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw", bwCid, seq + 1))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw/${Bailiwick.VERSION}", verCid, seq + 1))

        ipfs.publishName(rootCid, seq + 1, 10)
    }

    private fun cidForDir(path: String, seq: Long): ContentId {
        val paths = db.ipnsCacheDao().pathsForPeer(ipfs.peerID, seq)
        val cid = paths.filter { p -> p.path == path }.firstOrNull()?.cid ?: ipfs.createEmptyDir()
        return cid!!
    }

    private fun uploadCircleToIpfs(circle: Circle, cipher: Encryptor) {
        val postCids = db.circlePostDao().postsIn(circle.id).mapNotNull {
            db.postDao().find(it).cid
        }

        // Feeds are Circles... from the other side. I don't know who you're posting to
        // just that there's a list I can't read.
        val identityCid = db.identityDao().find(circle.identityId).cid!!
        val now = Calendar.getInstance().timeInMillis
        val feed = IpfsFeed(now, postCids, listOf(), listOf(), identityCid)
        val feedCid = feed.toIpfs(cipher, ipfs)
        db.circleDao().storeCid(circle.id, feedCid)
    }

    private fun uploadPostsToIpfs(syncPosts: List<Post>, cipher: Encryptor) {
        syncPosts.forEach { post ->
            val fileDefs = db.postFileDao().filesFor(post.id).map { pf ->
                IpfsFileDef(pf.mimeType, pf.fileCid)
            }

            val ipfsPost = IpfsPost(post.timestamp, post.parent, post.text, fileDefs, post.signature)
            db.postDao().updateCid(post.id, ipfsPost.toIpfs(cipher, ipfs))
        }
    }


}