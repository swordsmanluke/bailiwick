package com.perfectlunacy.bailiwick.models

import org.junit.Assert
import org.junit.Test
import java.lang.Exception
import java.lang.RuntimeException

class InteractionTest {
    val file = PostFile("plain/text", "fake_cid", "fake_sig")
    val comment = Interaction("comment", "0.1", "uuid", 1610156501960, "post_id", "parent_id", listOf(file))
    val commentJson = "{\"type\":\"comment\",\"version\":\"0.1\",\"id\":\"uuid\",\"timestampMillis\":1610156501960,\"post\":\"post_id\",\"parent\":\"parent_id\",\"files\":[{\"mimeType\":\"plain/text\",\"cid\":\"fake_cid\",\"signature\":\"fake_sig\"}],\"signature\":\"-648179585\"}"

    @Test
    fun interactionSerializesToJson() {
        val pJson = comment.toJson()

        Assert.assertEquals(commentJson, pJson)
    }

    @Test
    fun interactionDeserializesFromJson() {
        val interaction = Interaction.fromJson(commentJson)

        Assert.assertNotNull(interaction)
    }

    @Test
    fun interactionDeserializationFailsForBadSignature() {
        val jsonForm = "{\"version\":\"0.1\",\"timestamp\":1610156501960,\"text\":\"a post\",\"files\":[],\"signature\":\"badSig\"}"
        try {
            Interaction.fromJson(jsonForm)
            throw RuntimeException("Parse didn't raise")
        } catch(e: Exception) { // TODO: Assert the proper exception type - when there is one
            Assert.assertNotEquals("Parse didn't raise", e.message)
        }
    }
}