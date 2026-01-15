package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for IrohNode implementation.
 * Tests blob storage and collection operations.
 */
@RunWith(AndroidJUnit4::class)
class IrohNodeTest {

    private lateinit var context: Context
    private lateinit var iroh: InMemoryIrohNode

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        iroh = InMemoryIrohNode()
    }

    @Test
    fun nodeIdIs64Characters() = runBlocking {
        // Iroh node IDs are 64 hex characters (32 bytes Ed25519 public key)
        assertEquals(64, iroh.nodeId().length)
    }

    @Test
    fun storeAndRetrieveBlob() = runBlocking {
        val content = "Some test content"
        val data = content.toByteArray()

        // Store the blob
        val hash = iroh.storeBlob(data)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())

        // Retrieve and verify
        val retrieved = iroh.getBlob(hash)
        assertNotNull(retrieved)
        assertEquals(content, String(retrieved!!))
    }

    @Test
    fun blobStorageForBailiwickContent() = runBlocking {
        // Simulate storing Bailiwick content as blobs

        // Store identity content
        val identityJson = """{"name": "TestUser", "profilePicHash": null}"""
        val identityHash = iroh.storeBlob(identityJson.toByteArray())

        // Store post content
        val postJson = """{"timestamp": 12345, "text": "Hello World", "files": [], "signature": "abc"}"""
        val postHash = iroh.storeBlob(postJson.toByteArray())

        // Store feed content
        val feedJson = """{"updatedAt": 12345, "posts": ["$postHash"]}"""
        val feedHash = iroh.storeBlob(feedJson.toByteArray())

        // Verify we can read back the content
        assertNotNull(iroh.getBlob(identityHash))
        assertNotNull(iroh.getBlob(feedHash))
        
        // Verify we can traverse back to content
        val feedData = iroh.getBlob(feedHash)
        assertNotNull(feedData)
        assertTrue(String(feedData!!).contains(postHash))
    }

    @Test
    fun collectionsMaintainStructure() = runBlocking {
        // Store some blobs
        val file1Hash = iroh.storeBlob("File 1 content".toByteArray())
        val file2Hash = iroh.storeBlob("File 2 content".toByteArray())
        val file3Hash = iroh.storeBlob("File 3 content".toByteArray())

        // Create a collection (like an IPFS directory)
        val entries = mapOf(
            "readme.txt" to file1Hash,
            "data.json" to file2Hash,
            "image.png" to file3Hash
        )
        val collectionHash = iroh.createCollection(entries)

        // Retrieve and verify the collection
        val retrieved = iroh.getCollection(collectionHash)
        assertNotNull(retrieved)
        assertEquals(3, retrieved!!.size)
        assertEquals(file1Hash, retrieved["readme.txt"])
        assertEquals(file2Hash, retrieved["data.json"])
        assertEquals(file3Hash, retrieved["image.png"])

        // Verify we can access individual files from collection
        val file1Content = iroh.getBlob(retrieved["readme.txt"]!!)
        assertEquals("File 1 content", String(file1Content!!))
    }

    @Test
    fun hasBlobWorksCorrectly() = runBlocking {
        val hash = iroh.storeBlob("test data".toByteArray())

        assertTrue(iroh.hasBlob(hash))
        assertFalse(iroh.hasBlob("nonexistent-hash"))
    }

    @Test
    fun isConnectedReturnsTrue() = runBlocking {
        // InMemoryIrohNode is always "connected"
        assertTrue(iroh.isConnected())
    }

    @Test
    fun getNodeAddressesReturnsAddresses() = runBlocking {
        val addresses = iroh.getNodeAddresses()
        assertTrue(addresses.isNotEmpty())
    }

    @Test
    fun downloadBlobReturnsLocalBlob() = runBlocking {
        val data = "test data".toByteArray()
        val hash = iroh.storeBlob(data)

        // In memory mock, downloadBlob returns local blob
        val downloaded = iroh.downloadBlob(hash, "some-node-id")
        assertNotNull(downloaded)
        assertArrayEquals(data, downloaded)
    }

    @Test
    fun downloadBlobReturnsNullForUnknownHash() = runBlocking {
        val downloaded = iroh.downloadBlob("unknown-hash", "some-node-id")
        assertNull(downloaded)
    }
}
