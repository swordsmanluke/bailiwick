package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.models.Keyring
import com.perfectlunacy.bailiwick.models.Link
import com.perfectlunacy.bailiwick.models.Manifest
import com.perfectlunacy.bailiwick.models.db.IpnsCache
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import com.perfectlunacy.bailiwick.storage.ipfs.IPNS
import com.perfectlunacy.bailiwick.workers.IpfsDownloadWorker

class RefreshRunner(val context: Context, val peers: List<PeerId>, val keys: Keyring, val cache: IPFSCache, val ipns: IpnsCacheDao, val ipfs: IPFS) {
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
            IpfsDownloadWorker.enqueue(context, cid, cache, ipfs)
        }

        runners.forEach { it.run() }
    }

    private fun checkCache(peerId: PeerId) {
        val root = ipns.getPath(peerId, "")
        if(root == null) {
            Log.d(TAG, "Could not find root for ")
            return
        }

        val manifestCid = ipns.getPath(peerId, "bw/${BailiwickImpl.VERSION}/manifest.json")!!.cid
        cacheIfMissing(manifestCid)

        val cipher = keys.encryptorForPeer(peerId)
        val manifest = Manifest(cipher, cache, manifestCid)
        manifest.feedCids.forEach {
            cacheIfMissing(it)
        }

        manifest.feeds.forEach { feed ->
            feed.postCids.forEach {
                cacheIfMissing(it)
            }
            feed.actionCids.forEach {
                cacheIfMissing(it)
            }
            // TODO: Interactions
        }
    }

    private fun cacheIfMissing(cid: String): Boolean {
        if (!cache.contains(cid)) {
            filesToDownload.add(cid)
            return true
        }
        return false
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

            ipns.insert(IpnsCache(0, peerId, "", newRecord.hash, newRecord.sequence))
            val links = ipfs.getLinks(peerId, true, IPNS.TIMEOUT_LONG) ?: emptyList()
            cacheLinks(peerId, links, sequence, "")
        }
    }

    private fun cacheLinks(peerId: PeerId, links: List<Link>, sequence: Long, path: String) {
        links.forEach { link ->
            val pathSoFar = "$path/${link.name}"
            val key = "/$peerId$pathSoFar"
            Log.i(IPNS.TAG, "Caching $key = ${link.cid}")

            ipns.insert(IpnsCache(0, peerId, pathSoFar, link.cid, sequence))

            val nextLinks = ipfs.getLinks(link.cid, true, IPNS.TIMEOUT_LONG) ?: emptyList()
            cacheLinks(peerId, nextLinks, sequence, pathSoFar)
        }
    }

}