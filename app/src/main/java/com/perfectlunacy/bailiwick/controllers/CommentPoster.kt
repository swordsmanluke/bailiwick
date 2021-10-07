package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.models.Interaction
import com.perfectlunacy.bailiwick.models.PostFile
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import threads.lite.cid.Cid
import java.util.*

class CommentPoster(private val dht: BailiwickNetwork) {
    fun postComment(postId: String, parentId: String?, comment: String): Cid {
        val file = publishContents(comment)
        val interaction = Interaction("comment", "0.1", UUID.randomUUID().toString(), System.currentTimeMillis(), postId, parentId, listOf(file))
        val cid = dht.store(interaction.toJson())
        return cid
    }

    private fun publishContents(comment: String): PostFile {
        val cid = dht.store(comment)
        val signature = comment.hashCode().toString() // TODO: Replace this with actual signing
        return PostFile("plain/text", cid.toString(), signature)
    }
}