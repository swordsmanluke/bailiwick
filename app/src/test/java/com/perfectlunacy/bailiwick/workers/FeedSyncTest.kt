package com.perfectlunacy.bailiwick.workers

import com.perfectlunacy.bailiwick.models.iroh.IrohFeed
import com.perfectlunacy.bailiwick.models.iroh.IrohFileDef
import com.perfectlunacy.bailiwick.models.iroh.IrohPost
import com.perfectlunacy.bailiwick.storage.iroh.InMemoryIrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Integration tests for feed sync functionality.
 * Tests serialization and blob storage for feeds.
 *
 * Note: Actual encryption tests require Android crypto providers and are in androidTest.
 */
class FeedSyncTest {

    private lateinit var aliceIroh: InMemoryIrohNode
    private lateinit var aliceNodeId: String

    @Before
    fun setUp() = runBlocking {
        aliceIroh = InMemoryIrohNode()
        aliceNodeId = aliceIroh.nodeId()
    }

    // ===== IrohFeed Serialization Tests =====

    @Test
    fun `IrohFeed serializes correctly`() {
        val feed = IrohFeed(
            updatedAt = 1704067200000L,
            posts = listOf("hash1", "hash2", "hash3")
        )

        val json = GsonProvider.gson.toJson(feed)
        val parsed = GsonProvider.gson.fromJson(json, IrohFeed::class.java)

        assertEquals(feed.updatedAt, parsed.updatedAt)
        assertEquals(feed.posts, parsed.posts)
    }

    @Test
    fun `empty IrohFeed serializes correctly`() {
        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = emptyList()
        )

        val json = GsonProvider.gson.toJson(feed)
        val parsed = GsonProvider.gson.fromJson(json, IrohFeed::class.java)

        assertTrue(parsed.posts.isEmpty())
    }

    @Test
    fun `IrohFeed can be used as cipher validator`() {
        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = listOf("hash1", "hash2")
        )

        val json = GsonProvider.gson.toJson(feed)

        // This is the validator pattern used in content sync
        val isValidFeed = try {
            GsonProvider.gson.fromJson(json, IrohFeed::class.java)
            true
        } catch (e: Exception) {
            false
        }

        assertTrue(isValidFeed)
    }

    @Test
    fun `invalid JSON fails feed validation`() {
        val invalidJson = "not valid json"

        val isValidFeed = try {
            GsonProvider.gson.fromJson(invalidJson, IrohFeed::class.java)
            true
        } catch (e: Exception) {
            false
        }

        assertFalse(isValidFeed)
    }

    @Test
    fun `malformed feed fails validation`() {
        val malformedJson = "{\"wrong\":\"fields\"}"

        val isValidFeed = try {
            val parsed = GsonProvider.gson.fromJson(malformedJson, IrohFeed::class.java)
            // Check if posts is not null (Gson may still parse with null fields)
            parsed.posts != null
        } catch (e: Exception) {
            false
        }

        assertFalse(isValidFeed)
    }

    // ===== IrohPost Serialization Tests =====

    @Test
    fun `IrohPost serializes correctly`() {
        val post = IrohPost(
            timestamp = 1704067200000L,
            parentHash = "parent123",
            text = "Hello, World!",
            files = listOf(
                IrohFileDef("image/png", "imagehash123"),
                IrohFileDef("image/jpeg", "imagehash456")
            ),
            signature = "sig123"
        )

        val json = GsonProvider.gson.toJson(post)
        val parsed = GsonProvider.gson.fromJson(json, IrohPost::class.java)

        assertEquals(post.timestamp, parsed.timestamp)
        assertEquals(post.parentHash, parsed.parentHash)
        assertEquals(post.text, parsed.text)
        assertEquals(post.signature, parsed.signature)
        assertEquals(2, parsed.files.size)
        assertEquals("image/png", parsed.files[0].mimeType)
        assertEquals("imagehash123", parsed.files[0].blobHash)
    }

    @Test
    fun `IrohPost with null parent serializes correctly`() {
        val post = IrohPost(
            timestamp = System.currentTimeMillis(),
            parentHash = null,
            text = "Root post",
            files = emptyList(),
            signature = "sig"
        )

        val json = GsonProvider.gson.toJson(post)
        val parsed = GsonProvider.gson.fromJson(json, IrohPost::class.java)

        assertNull(parsed.parentHash)
        assertEquals("Root post", parsed.text)
    }

    // ===== Blob Storage Tests =====

    @Test
    fun `feed blob can be stored and retrieved`() = runBlocking {
        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = listOf("post1", "post2", "post3")
        )

        // Store feed as blob (in real use, this would be encrypted first)
        val json = GsonProvider.gson.toJson(feed)
        val feedHash = aliceIroh.storeBlob(json.toByteArray())

        // Retrieve
        val feedBlob = aliceIroh.getBlob(feedHash)
        assertNotNull(feedBlob)
        val parsedFeed = GsonProvider.gson.fromJson(String(feedBlob!!), IrohFeed::class.java)

        assertEquals(feed.posts, parsedFeed.posts)
    }

    // ===== Key Exchange Format Tests =====

    @Test
    fun `AES key can be encoded and decoded as Base64`() {
        // Simulate 256-bit AES key
        val keyBytes = ByteArray(32) { it.toByte() }

        val base64Key = Base64.getEncoder().encodeToString(keyBytes)
        val decoded = Base64.getDecoder().decode(base64Key)

        assertArrayEquals(keyBytes, decoded)
        assertEquals(32, decoded.size)
    }

    @Test
    fun `key for nodeId can be looked up with prefix pattern`() {
        // This simulates how EncryptorFactory.forPeer works
        val nodeId = "abc123"
        val keyIdentifier = "key:$nodeId:1"

        assertTrue(keyIdentifier.contains(nodeId))
        assertTrue(keyIdentifier.startsWith("key:"))
    }
}
