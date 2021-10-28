package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.ContentId
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

interface IPFSCacheWriter {
    fun contains(cid: ContentId): Boolean
    fun store(cid: ContentId, content: ByteArray)
}

interface IPFSCacheReader {
    fun <T>retrieve(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T?
    fun raw(cid: ContentId): ByteArray?
}

class IPFSCache(private val filesDir: String): IPFSCacheWriter, IPFSCacheReader {
    companion object {
        const val TAG = "IPFSCache"
    }
    override fun contains(cid: ContentId): Boolean {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid
        val f = File("$filesDir/bwcache/$cid")
        return f.exists()
    }

    override fun store(cid: ContentId, content: ByteArray) {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid

        val f = File("$filesDir/bwcache/$cid")
        if(f.exists()) { return } // we already have this file - nothing to do!

        // Just in case
        File("$filesDir/bwcache").mkdirs()

        val fout = FileOutputStream(f)
        fout.write(content)
        fout.close()
    }

    override fun <T>retrieve(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T? {
        val cid = cid.split("/").last() // if we're using filenames, split them off and just use the cid
        val f = File("$filesDir/bwcache/$cid")
        if(!f.exists()) { return null } // No file here

        // TODO: This will fail on files larger than 2Gb
        //       Probably safe enough for now, but large
        //       files will need different handling.
        val raw = cipher.decrypt(raw(cid)!!)
        return Gson().fromJson(String(raw), clazz)
    }

    override fun raw(cid: ContentId): ByteArray? {
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