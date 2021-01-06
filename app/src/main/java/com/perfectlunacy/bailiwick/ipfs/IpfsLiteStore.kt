package com.perfectlunacy.bailiwick.ipfs

import com.perfectlunacy.bailiwick.DistHashStore
import com.perfectlunacy.bailiwick.ipfs.lite.CID
import com.perfectlunacy.bailiwick.ipfs.lite.Closeable
import com.perfectlunacy.bailiwick.ipfs.lite.IPFS

class IpfsLiteStore(val ipfs: IPFS): DistHashStore, Closeable {


    override fun store(data: String): String {
        return ipfs.storeData(data.toByteArray())!!.cid
    }

    override fun publish_posts(data: String): String {
        TODO("Not yet implemented")
    }

    override fun retrieve(key: String): String {
        return ipfs.getText(CID(key)) ?: ""
    }

    override fun retrieve_posts(key: String): String {
        TODO("Not yet implemented")
    }

    override val isClosed: Boolean
        get() = false // Let's see what this does
}