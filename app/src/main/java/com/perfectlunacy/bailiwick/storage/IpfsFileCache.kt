package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.util.Log
import java.io.File

interface IpfsCache {
    fun get(cid: ContentId): ByteArray?
    fun cache(cid: ContentId, data: ByteArray)
}

class IpfsFileCache(val filesDir: String) : IpfsCache {
    companion object {
        const val TAG = "IpfsFileCache"
    }

    override fun get(cid: ContentId): ByteArray? {
        val f = File("$filesDir/bwcache/$cid")
        if(!f.exists()) { Log.i(TAG, "Cache miss for @$cid"); return null }
        Log.i(TAG, "Cache hit for @$cid")

        return f.readBytes()
    }

    override fun cache(cid: ContentId, data: ByteArray) {
        File("$filesDir/bwcache").mkdirs()
        val f = File("$filesDir/bwcache/$cid")
        if(f.exists()) { return }

        Log.i(TAG, "Caching @$cid ${data.size} bytes")
        f.writeBytes(data)
    }
}

/***
 * For use with MockIPFS so we don't double store everything.
 */
class MockFileCache(): IpfsCache {
    override fun get(cid: ContentId): ByteArray? {
        return null
    }

    override fun cache(cid: ContentId, data: ByteArray) {
        return
    }

}