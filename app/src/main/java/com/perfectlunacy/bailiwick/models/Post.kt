package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.models.ipfs.FileDef
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.models.ipfs.Post as IpfsPost

class Post(val author: UserIdentity, val timestamp: Long, val parent: ContentId?, val cid: ContentId, val text: String, val files: List<FileDef>, val signature: String, val responses: MutableList<Post>) {

    companion object {
        @JvmStatic
        fun fromIPFS(bw: Bailiwick, author: UserIdentity, cid: ContentId, post: IpfsPost): Post {
            post.files.map {
                bw.download(it.cid) // Ensure it's in the local cache
            }
            return Post(author, post.timestamp, post.parentCid, cid, post.text, post.files, post.signature, mutableListOf())
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other as Post? == null) {
            return false
        }

        return (other as Post).signature == signature
    }

    override fun hashCode(): Int {
        return this.signature.hashCode()
    }
}