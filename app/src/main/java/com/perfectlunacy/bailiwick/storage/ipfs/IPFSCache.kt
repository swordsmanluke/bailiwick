package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.ContentId
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

interface IPFSCacheWriter {
    fun cacheContains(cid: ContentId): Boolean
    fun putInCache(cid: ContentId, content: ByteArray)
}

interface IPFSCacheReader {
    fun <T>retrieveFromCache(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T?
    fun downloadFromCache(cid: ContentId): ByteArray?
}

class IPFSCache(private val filesDir: String): IPFSCacheWriter, IPFSCacheReader {
    companion object {
        const val TAG = "IPFSCache"
    }
    override fun cacheContains(cid: ContentId): Boolean {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid
        val f = File("$filesDir/$cid")
        return f.exists()
    }

    override fun putInCache(cid: ContentId, content: ByteArray) {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid

        val f = File("$filesDir/$cid")
        if(f.exists()) { return } // we already have this file - nothing to do!

        // Just in case
        File(filesDir).mkdirs()

        val fout = FileOutputStream(f)
        fout.write(content)
        fout.close()
    }

    override fun <T>retrieveFromCache(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T? {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid
        val f = File("$filesDir/$cid")
        if(!f.exists()) { return null } // No file here

        // TODO: This will fail on files larger than 2Gb
        //       Probably safe enough for now, but large
        //       files will need different handling.
        try{
            val raw = cipher.decrypt(downloadFromCache(cid)!!)
            return Gson().fromJson(String(raw), clazz)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve cid $cid: ${e.message}\n${e.stackTraceToString()} ")
            return null
        }
    }

    override fun downloadFromCache(cid: ContentId): ByteArray? {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid
        val f = File("$filesDir/$cid")
        if(!f.exists()) { return null } // No file here

        try {
            return f.readBytes()
        } catch(e: Exception) {
            Log.e(TAG, "Could not read cache for cid $cid")
            return null
        }
    }
}