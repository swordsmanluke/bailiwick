package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.InMemoryStore
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.PostFile
import com.perfectlunacy.bailiwick.models.db.User
import org.junit.Assert
import org.junit.Test

class PublishPostTest {

    @Test
    fun publishingASimpleTextPostShouldWork() {
        val myPeerId = "peerId"
        val dht = InMemoryStore(myPeerId)
        val publisher = PostPublisher(dht, myPeerId)
        val author = User("1234", "author", "nopic")
        val post = Post("0.1", System.currentTimeMillis(), author,"This is a post!", emptyList())

        publisher.publish(post)

        Assert.assertNotEquals(dht.retrieve(myPeerId), "") // Should have put something in
    }

    @Test
    fun publishingATextPostWithAFileShouldWork() {
        val myPeerId = "peerId"
        val dht = InMemoryStore(myPeerId)
        val publisher = PostPublisher(dht, myPeerId)
        val file = PostFile("application/text", "fakeCid", "fakeSig")
        val author = User("1234", "author", "nopic")
        val post = Post("0.1", System.currentTimeMillis(), author, "This is a post!", listOf(file))

        publisher.publish(post)

        Assert.assertNotEquals(dht.retrieve(myPeerId), "") // Should have put something in
    }

    @Test
    fun retrievingAPlainTextPostShouldWork() {
        val myPeerId = "peerId"
        val dht = InMemoryStore(myPeerId)
        val publisher = PostPublisher(dht, myPeerId)
        val author = User("1234", "author", "nopic")
        val post = Post("0.1", System.currentTimeMillis(), author,"This is a post!", emptyList())
        publisher.publish(post)

        val posts = dht.retrieve_posts(myPeerId).
                        split("\n").
                        map { cid -> dht.retrieve_posts(cid) }.
                        filterNot { s -> s.isBlank() }. // Get rid of any posts we weren't able to retrieve
                        map { pJson -> Post.fromJson(pJson) }

        Assert.assertEquals(posts.size, 1)
        Assert.assertEquals(posts[0], post)
    }

}