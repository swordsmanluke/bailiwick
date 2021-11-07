package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.IpnsCache
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.ipfs.Feed
import com.perfectlunacy.bailiwick.models.ipfs.IpfsPost
import com.perfectlunacy.bailiwick.models.ipfs.Link
import com.perfectlunacy.bailiwick.models.ipfs.Manifest
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.storage.ipfs.IpnsImpl
import com.perfectlunacy.bailiwick.workers.IpfsDownloadWorker

class RefreshRunner(val context: Context, val peers: List<PeerId>, val db: BailiwickDatabase, val ipfs: IPFS, val ipns: IpnsCacheDao) {
    companion object {
        const val TAG = "RefreshRunner"
    }

    fun run() {
        peers.forEach { peerId ->
            iteration(peerId)
            while(downloadedFiles) {
                iteration(peerId)
            }
        }
    }

    private val filesToDownload: MutableList<ContentId> = mutableListOf()
    private var downloadedFiles = false

    private fun iteration(peerId: PeerId) {
        refreshIPNSCache(peerId)
        checkCache(peerId)
        downloadedFiles = false
        createIpfsJobs(peerId)
    }

    private fun createIpfsJobs(peerId: PeerId) {
        val runners = mutableListOf<IpfsDownloadRunner>()
        while(filesToDownload.isNotEmpty()) {
            downloadedFiles = true
            val cid = filesToDownload.removeAt(0)
            IpfsDownloadWorker.enqueue(context, cid)
        }

        runners.forEach { it.run() }
    }

    private fun checkCache(peerId: PeerId) {
        val root = ipns.getPath(peerId, "")
        if(root == null) {
            Log.d(TAG, "Could not find root for ")
            return
        }

        val cipher = Keyring.encryptorForPeer(db, peerId) { Gson().fromJson(String(it), Manifest::class.java) != null }
        val manifest = IpfsDeserializer.fromBailiwickFile(cipher, ipfs, peerId, "manifest.json", Manifest::class.java)!!
        manifest.feeds.forEach { feedCid ->
            IpfsDeserializer.fromCid(cipher, ipfs, feedCid, Feed::class.java)?.let { feed ->
                val author = db.identityDao().findByCid(feed.identity)

                feed.posts.forEach { postCid ->
                    if (!db.postDao().postExists(postCid)) {
                        downloadPost(cipher, postCid, author)
                    }
                }
            }
        }
    }

    private fun downloadPost(cipher: Encryptor, postCid: ContentId, author: Identity) {
        val ipfsPost = IpfsDeserializer.fromCid(cipher, ipfs, postCid, IpfsPost::class.java)!!
        db.postDao().insert(
            Post(
                author.id,
                postCid,
                ipfsPost.timestamp,
                ipfsPost.parent_cid,
                ipfsPost.text,
                ipfsPost.signature
            )
        )
    }

    private fun refreshIPNSCache(peerId: PeerId) {
        val sequence = ipns.getPath(peerId, "")?.sequence ?: 0
        var newRecord = ipfs.resolveName(peerId, sequence, 30)
        var tries = 10

        while ((newRecord == null || newRecord.sequence < sequence) && tries > 0) {
            Log.d(TAG, "Found expired IPNS record ($newRecord.sequence < $sequence) for $peerId. Trying again ($tries remaining)")
            newRecord = ipfs.resolveName(peerId, sequence, 30) ?: newRecord
            tries -= 1
        }

        if (newRecord != null) {
            if (newRecord.sequence == sequence) {
                Log.i(TAG, "No update found for $peerId. Skipping.")
                return
            }

            ipns.insert(IpnsCache(peerId, "", newRecord.hash, newRecord.sequence))
            val links = ipfs.getLinks(peerId, true, IpnsImpl.TIMEOUT_LONG) ?: emptyList()
            cacheLinks(peerId, links, sequence, "")
        }
    }

    private fun cacheLinks(peerId: PeerId, links: List<Link>, sequence: Long, path: String) {
        links.forEach { link ->
            val pathSoFar = "$path/${link.name}"
            val key = "/$peerId$pathSoFar"
            Log.i(TAG, "Caching $key = ${link.cid}")

            ipns.insert(IpnsCache(peerId, pathSoFar, link.cid, sequence))

            val nextLinks = ipfs.getLinks(link.cid, true, IpnsImpl.TIMEOUT_LONG) ?: emptyList()
            cacheLinks(peerId, nextLinks, sequence, pathSoFar)
        }
    }

}