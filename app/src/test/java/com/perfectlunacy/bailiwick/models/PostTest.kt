package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.models.db.User
import org.junit.Assert
import org.junit.Test
import java.lang.Exception
import java.lang.RuntimeException

class PostTest {
    val author = User("1234", "author", "nopic")
    val textPost = Post("0.1", 1610156501960, author, "a post", emptyList())

    val file = PostFile("plain/text", "fake_cid", "fake_sig")
    val filePost = Post("0.1", 1610156501960, author,"a post", listOf(file))

    val postJson = "{\"version\":\"0.1\",\"timestamp\":1610156501960,\"author\":{\"id\":0,\"uid\":\"1234\",\"name\":\"author\",\"profilePicCid\":\"nopic\"},\"text\":\"a post\",\"files\":[],\"signature\":\"1819436663\"}"
    val filePostJson = "{\"version\":\"0.1\",\"timestamp\":1610156501960,\"author\":{\"id\":0,\"uid\":\"1234\",\"name\":\"author\",\"profilePicCid\":\"nopic\"},\"text\":\"a post\",\"files\":[{\"mimeType\":\"plain/text\",\"cid\":\"fake_cid\",\"signature\":\"fake_sig\"}],\"signature\":\"1598744516\"}"

    @Test
    fun postSerializesToJson() {
        val pJson = textPost.toJson()

        Assert.assertEquals(postJson, pJson)
    }

    @Test
    fun postWithFileSerializesToJson() {
        val pJson = filePost.toJson()

        Assert.assertEquals(filePostJson, pJson)
    }

    @Test
    fun postDeserializesFromJson() {
        val post = Post.fromJson(postJson)

        Assert.assertNotNull(post)
    }

    @Test
    fun postWithFileDeserializesFromJson() {
        val post = Post.fromJson(filePostJson)

        Assert.assertNotNull(post)
        Assert.assertEquals(post.files.size, 1)
    }

    @Test
    fun postDeserializationFailsForBadSignature() {
        val jsonForm = "{\"version\":\"0.1\",\"timestamp\":1610156501960,\"text\":\"a post\",\"files\":[],\"signature\":\"badSig\"}"
        try {
            Post.fromJson(jsonForm)
            throw RuntimeException("Parse didn't raise")
        } catch(e: Exception) { // TODO: Assert the proper exception type - when there is one
            Assert.assertNotEquals("Parse didn't raise", e.message)
        }
    }
}