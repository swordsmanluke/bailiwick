package com.perfectlunacy.bailiwick.workers.runners.downloaders

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostDao
import com.perfectlunacy.bailiwick.models.db.PostFile
import com.perfectlunacy.bailiwick.models.db.PostFileDao
import com.perfectlunacy.bailiwick.models.ipfs.IpfsPost
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

class FileDownloader(private val filePath: Path, private val postFileDao: PostFileDao, private val ipfs: IPFS) {
    companion object {
        const val TIMEOUT = 300L
        const val TAG = "FileDownloader"
    }

    fun download(cid: ContentId, cipher: Encryptor, postId: Long, mimeType: String) {
        if(fileIsDownloaded(cid)) {
            Log.i(TAG, "Already downloaded PostFile $cid")
            return
        }

        // Capture the file details first so we can re-download if/when required
        postFileDao.insert(PostFile(postId, cid, mimeType))

        // Now download and cache the file
        val f = Path(filePath.pathString, "bwcache", cid).toFile()
        f.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(f)).use {
            it.write(cipher.decrypt(ipfs.getData(cid, TIMEOUT)))
        }

        Log.i(TAG, "Downloaded file $cid")
    }

    private fun fileIsDownloaded(cid: ContentId):Boolean {
        val f = Path(filePath.pathString, "bwcache", cid).toFile()
        return f.exists()
    }
}