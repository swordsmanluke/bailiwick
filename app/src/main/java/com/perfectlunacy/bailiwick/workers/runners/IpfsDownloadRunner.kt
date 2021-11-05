package com.perfectlunacy.bailiwick.workers.runners

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheWriter

class IpfsDownloadRunner(val cid: ContentId, val ipfs: IPFS, val cache: IPFSCacheWriter) {
    fun run() {
        val data = ipfs.getData(cid, 600)
        cache.putInCache(cid, data)
    }
}