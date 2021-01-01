package com.perfectlunacy.bailiwick.ipfs

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.perfectlunacy.bailiwick.DistHashStore
import io.ipfs.kotlin.IPFS
import java.io.File

/*****
 * Distributed Hash Store backed by IPFS (https://ipfs.io).
 * Wraps the go-ipfs client (https://github.com/ipfs/go-ipfs)
 * via ipfs-kotlin-android lib (https://github.com/komputing/ipfs-api-kotlin)
 */
class IpfsWrapper(private val ipfs: IPFS): DistHashStore {
    override fun store(data: String): String {
        val result = ipfs.add.string(data)
        return result.Hash
    }

    override fun retrieve(uri: String): String {
        return ipfs.get.cat(uri)
    }
}