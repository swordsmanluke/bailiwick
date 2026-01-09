package com.perfectlunacy.bailiwick.workers

import com.perfectlunacy.bailiwick.models.iroh.IrohFeed
import com.perfectlunacy.bailiwick.models.iroh.IrohFileDef
import com.perfectlunacy.bailiwick.models.iroh.IrohPost
import com.perfectlunacy.bailiwick.storage.iroh.IrohDocKeys
import com.perfectlunacy.bailiwick.storage.iroh.InMemoryIrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Integration tests for feed sync functionality.
 * Tests the key exchange → feed download pipeline logic.
 *
 * Note: Actual encryption tests require Android crypto providers and are in androidTest.
 */
class FeedSyncTest {

    private lateinit var aliceIroh: InMemoryIrohNode
    private lateinit var bobIroh: InMemoryIrohNode
    private lateinit var aliceNodeId: String
    private lateinit var bobNodeId: String

    @Before
    fun setUp() = runBlocking {
        aliceIroh = InMemoryIrohNode()
        bobIroh = InMemoryIrohNode()
        aliceNodeId = aliceIroh.nodeId()
        bobNodeId = bobIroh.nodeId()
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

        // This is the validator pattern used in syncPeer
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

    // ===== Feed Storage and Retrieval Tests =====

    @Test
    fun `feed hash can be stored and retrieved from doc`() = runBlocking {
        val feedHash = "feed-blob-hash-123"

        aliceIroh.getMyDoc().set(IrohDocKeys.KEY_FEED_LATEST, feedHash.toByteArray())

        val retrieved = aliceIroh.getMyDoc().get(IrohDocKeys.KEY_FEED_LATEST)
        assertNotNull(retrieved)
        assertEquals(feedHash, String(retrieved!!))
    }

    @Test
    fun `feed blob can be stored and retrieved`() = runBlocking {
        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = listOf("post1", "post2", "post3")
        )

        // Store feed as blob (in real use, this would be encrypted first)
        val json = GsonProvider.gson.toJson(feed)
        val feedHash = aliceIroh.storeBlob(json.toByteArray())

        // Store hash in doc
        aliceIroh.getMyDoc().set(IrohDocKeys.KEY_FEED_LATEST, feedHash.toByteArray())

        // Retrieve
        val storedHashBytes = aliceIroh.getMyDoc().get(IrohDocKeys.KEY_FEED_LATEST)
        val storedHash = String(storedHashBytes!!)
        assertEquals(feedHash, storedHash)

        val feedBlob = aliceIroh.getBlob(storedHash)
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

    // ===== Simulated End-to-End Feed Sync =====

    @Test
    fun `simulated feed sync flow without encryption`() = runBlocking {
        // Step 1: Alice creates a feed
        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = listOf("post1hash", "post2hash", "post3hash")
        )

        // Step 2: Alice stores feed as blob (in real use, encrypted)
        val feedJson = GsonProvider.gson.toJson(feed)
        val feedHash = aliceIroh.storeBlob(feedJson.toByteArray())

        // Step 3: Alice stores feed hash in her doc
        aliceIroh.getMyDoc().set(IrohDocKeys.KEY_FEED_LATEST, feedHash.toByteArray())

        // Step 4: Bob opens Alice's doc (via join)
        val aliceDocTicket = aliceIroh.myDocTicket()
        val aliceDocFromBob = bobIroh.joinDoc(aliceDocTicket)
        assertNotNull(aliceDocFromBob)

        // Step 5: Bob reads feed hash from Alice's doc
        // (In real sync, the doc would be synced first)
        val feedHashBytes = aliceIroh.getMyDoc().get(IrohDocKeys.KEY_FEED_LATEST)
        assertNotNull(feedHashBytes)
        val downloadedHash = String(feedHashBytes!!)
        assertEquals(feedHash, downloadedHash)

        // Step 6: Bob downloads feed blob
        val feedBlob = aliceIroh.getBlob(downloadedHash)
        assertNotNull(feedBlob)

        // Step 7: Bob parses feed
        val parsedFeed = GsonProvider.gson.fromJson(String(feedBlob!!), IrohFeed::class.java)
        assertEquals(3, parsedFeed.posts.size)
        assertTrue(parsedFeed.posts.contains("post1hash"))
    }

    // ===== Circle-based Feed Tests =====

    @Test
    fun `circle key is stored at correct path`() = runBlocking {
        val circleId = 42L
        val circleHash = "circle-42-hash"

        val key = IrohDocKeys.circleKey(circleId)
        aliceIroh.getMyDoc().set(key, circleHash.toByteArray())

        assertEquals("circles/42", key)

        val retrieved = aliceIroh.getMyDoc().get(key)
        assertEquals(circleHash, String(retrieved!!))
    }

    @Test
    fun `multiple circles can be stored independently`() = runBlocking {
        val circles = mapOf(
            1L to "circle1hash",
            2L to "circle2hash",
            3L to "circle3hash"
        )

        // Store all circles
        circles.forEach { (id, hash) ->
            val key = IrohDocKeys.circleKey(id)
            aliceIroh.getMyDoc().set(key, hash.toByteArray())
        }

        // Verify each can be retrieved
        circles.forEach { (id, expectedHash) ->
            val key = IrohDocKeys.circleKey(id)
            val retrieved = aliceIroh.getMyDoc().get(key)
            assertEquals(expectedHash, String(retrieved!!))
        }
    }

    // ===== Doc Key Discovery Tests =====

    @Test
    fun `feed and circle keys can be discovered`() = runBlocking {
        // Set up doc with feed and circles
        aliceIroh.getMyDoc().set(IrohDocKeys.KEY_FEED_LATEST, "feedhash".toByteArray())
        aliceIroh.getMyDoc().set(IrohDocKeys.KEY_IDENTITY, "identityhash".toByteArray())
        aliceIroh.getMyDoc().set(IrohDocKeys.circleKey(1), "circle1hash".toByteArray())
        aliceIroh.getMyDoc().set(IrohDocKeys.circleKey(2), "circle2hash".toByteArray())

        val allKeys = aliceIroh.getMyDoc().keys()

        assertTrue(allKeys.contains(IrohDocKeys.KEY_FEED_LATEST))
        assertTrue(allKeys.contains(IrohDocKeys.KEY_IDENTITY))
        assertTrue(allKeys.any { it.startsWith(IrohDocKeys.KEY_CIRCLE_PREFIX) })

        val circleKeys = allKeys.filter { it.startsWith(IrohDocKeys.KEY_CIRCLE_PREFIX) }
        assertEquals(2, circleKeys.size)
    }
}
