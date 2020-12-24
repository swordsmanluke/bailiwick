package com.perfectlunacy.bailiwick.ipfs

import com.perfectlunacy.bailiwick.IpfsStore
import io.ipfs.kotlin.IPFS

class IpfsWrapper(val ipfs: IPFS): IpfsStore {
    override fun store(data: String): String {
        val result = ipfs.add.string(data)
        return result.Hash
    }

    override fun retrieve(uri: String): String {
        TODO("Not yet implemented")
    }
}