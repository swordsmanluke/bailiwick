package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.InMemoryStore
import com.perfectlunacy.bailiwick.models.Interaction
import org.junit.Assert
import org.junit.Test

class PublishCommentTest {

    @Test
    fun publishingASimpleTextCommentShouldWork() {
        val myPeerId = "peerId"
        val dht = InMemoryStore(myPeerId)
        val commenter = CommentPoster(dht)

        val cid = commenter.postComment("post_id", "parent_id", "comment text")

        Assert.assertNotEquals(dht.retrieve(cid), "") // Should have put something in
    }

    @Test
    fun retrievingAPlainTextCommentShouldWork() {
        val myPeerId = "peerId"
        val dht = InMemoryStore(myPeerId)
        val commenter = CommentPoster(dht)

        val cid = commenter.postComment("post_id", "parent_id", "comment text")

        val comment = Interaction.fromJson(dht.retrieve(cid))
        val contents = comment.files.map{ f -> dht.retrieve(f.path) }

        Assert.assertEquals(1, contents.size)
        Assert.assertEquals("comment text", contents.first())
    }

}