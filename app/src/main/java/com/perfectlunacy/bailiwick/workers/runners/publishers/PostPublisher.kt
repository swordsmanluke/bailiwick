package com.perfectlunacy.bailiwick.workers.runners.publishers

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostDao
import com.perfectlunacy.bailiwick.models.db.PostFileDao
import com.perfectlunacy.bailiwick.models.ipfs.IpfsFileDef
import com.perfectlunacy.bailiwick.models.ipfs.IpfsPost
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS

class PostPublisher(private val postDao: PostDao, private val postFileDao: PostFileDao, private val ipfs: IPFS) {
    fun publish(post: Post, cipher: Encryptor): ContentId {
        val fileDefs = postFileDao.filesFor(post.id).map { pf ->
            IpfsFileDef(pf.mimeType, pf.fileCid)
        }

        val ipfsPost = IpfsPost(post.timestamp, post.parent, post.text, fileDefs, post.signature)
        val cid = ipfsPost.toIpfs(cipher, ipfs)
        postDao.updateCid(post.id, cid)
        return cid
    }
}