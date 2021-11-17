package com.perfectlunacy.bailiwick.workers.runners.downloaders

import android.util.Log
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostDao
import com.perfectlunacy.bailiwick.models.ipfs.IpfsPost
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import java.lang.Exception

class PostDownloader(private val postDao: PostDao, private val ipfs: IPFS, private val fileDownloader: FileDownloader) {
    fun download(cid: ContentId, identityId: Long, cipher: Encryptor) {
        if (postAlreadyDownloaded(cid)) {
            Log.i(DownloadRunner.TAG, "Already downloaded post $cid!")
        } else {
            val ipfsPostPair = try {
                IpfsDeserializer.fromCid(cipher, ipfs, cid, IpfsPost::class.java)
            } catch (e: Exception) {
                Log.e(DownloadRunner.TAG, "Failed to download Post $cid", e)
                return
            }

            if (ipfsPostPair == null) {
                Log.e(DownloadRunner.TAG, "Failed to parse Post $cid")
                return
            }

            val ipfsPost = ipfsPostPair.first

            Log.i(DownloadRunner.TAG, "Downloaded post!")

            val post = Post(
                identityId,
                cid,
                ipfsPost.timestamp,
                ipfsPost.parent_cid,
                ipfsPost.text,
                ipfsPost.signature
            )

            post.id = postDao.insert(post)

            ipfsPost.files.forEach {
                val fileCipher = MultiCipher(listOf(cipher), ValidatorFactory.mimeTypeValidator(it.mimeType))
                fileDownloader.download(it.cid, fileCipher, post.id, it.mimeType)
            }
        }
    }

    private fun postAlreadyDownloaded(cid: ContentId) =
        postDao.findByCid(cid) != null
}