package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.perfectlunacy.bailiwick.models.Link
import com.perfectlunacy.bailiwick.models.db.AccountDao
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class IPNS(val ipfs: IPFS, val accounts: AccountDao) {
    companion object {
        const val TAG = "IPNS"
        const val TIMEOUT_LONG = 600L
    }

    init {
        GlobalScope.launch {
            accounts.activeAccount()?.let { acct ->
                ipfs.publishName(acct.rootCid, acct.sequence, TIMEOUT_LONG)
            }
            rootPublishLoop()
        }
    }

    private suspend fun rootPublishLoop() {
        while (true) {
            if (newRoots.isEmpty()) {
                delay(10000)
            } else {
                var newRoot = newRoots.remove()
                while (!newRoots.isEmpty()) {
                    newRoot = newRoots.remove()
                }
                Log.i(TAG, "Publishing new IPFS Root: $newRoot")
                val account = accounts.activeAccount()
                val oldRoot = account?.rootCid
                val seq = (account?.sequence ?: 0) + 1
                ipfs.publishName(newRoot, seq, TIMEOUT_LONG)
                account?.let {
                    it.rootCid = newRoot
                    it.sequence = seq
                    accounts.update(it)
                }

                // Clear outdated cache keys
                if (oldRoot != null) {
                    cachedPaths.keys.filter { it.contains(oldRoot) }.forEach {
                        cachedPaths.remove(it)
                    }
                }

                // Cache current links
                val links = ipfs.getLinks(newRoot, true, TIMEOUT_LONG) ?: emptyList()
                Log.i(TAG, "Updating cache for new root...")
                cacheLinks(ipfs.peerID, links, seq, "")
                Log.i(TAG, "New root $newRoot published!")
            }
        }
    }

    private fun cacheLinks(peerId: PeerId, links: List<Link>, version: Int, path: String) {
        links.forEach { link ->
            val now = Calendar.getInstance().timeInMillis
            val pathSoFar = "$path/${link.name}"
            val key = "/$peerId$pathSoFar"
            Log.i(TAG, "Caching $key = ${link.cid}")

            cachedPaths[key] = CidRecord(now, version, link.cid)

            val nextLinks = ipfs.getLinks(link.cid, true, TIMEOUT_LONG) ?: emptyList()
            cacheLinks(peerId, nextLinks, version, pathSoFar)
        }
    }

    data class CidRecord(val retrievedAt: Long, val version: Int, val cid: ContentId)
    private val cachedPaths: MutableMap<String, CidRecord> = mutableMapOf()

    private var newRoots: Queue<ContentId> = LinkedList()

    fun publishRoot(root: ContentId) {
        GlobalScope.launch {
            newRoots.add(root)
        }
    }

    fun cidForPath(peerId: PeerId, path: String, minSequence: Int): ContentId? {
        Log.i(TAG, "Resolving $peerId/$path:${minSequence}")
        val pathElements = listOf(peerId) + path.split("/").filter { it.isNotBlank() }
        var curPath = ""
        // Iteratively build the set of paths in this request
        // e.g. peer/, peer/bw, peer/bw/VERSION, peer/bw/VERSION/filename
        val paths = pathElements.map { name ->
            curPath += "/$name"
            curPath
        }.reversed()

        // if the cached file path is present, return it. Otherwise, lookup and cache the values
        val cachedFile = cachedPaths.get(paths.first())
        if (cachedFile != null && minSequence <= cachedFile.version && notExpired(cachedFile.retrievedAt)) {
            Log.i(TAG, "Found $path:$minSequence in cache")
            return cachedFile.cid
        }

        var maxRoot = ipfs.resolveName(peerId, minSequence.toLong(), TIMEOUT_LONG)
        var tries = 10
        while(maxRoot?.sequence ?: 0 < minSequence && tries > 0) {
            tries -= 1
            val newRoot = ipfs.resolveName(peerId, 0, TIMEOUT_LONG)
            maxRoot = if((newRoot?.sequence ?: 0)  > (maxRoot?.sequence ?: 0)) {
                            newRoot
                        } else {
                            maxRoot
                        }
            Log.i(TAG, "Retrieved root for peer $peerId...again. Version: ${maxRoot?.sequence ?: 0} (Desired $minSequence)")
        }

        val root = maxRoot?.hash

        // Let's start caching values
        if(root != null) {
            val now = Calendar.getInstance().timeInMillis
            cachedPaths.put("/$peerId", CidRecord(now, maxRoot!!.sequence.toInt(), root))
            val links = ipfs.getLinks(root, true, TIMEOUT_LONG) ?: emptyList()
            Log.i(TAG, "Updating cache for discovered root")
            cacheLinks(peerId, links, maxRoot!!.sequence.toInt(),"")
        } else {
            Log.w(TAG, "Failed to resolve root for $peerId")
        }

        // And try one more time
        val cid = cachedPaths.get(paths.first())?.cid
        return cid
    }

    private fun notExpired(time: Long): Boolean {
        val elapsed = Calendar.getInstance().timeInMillis - time
        return elapsed > 10 * 60 // Ten minutes
    }
}