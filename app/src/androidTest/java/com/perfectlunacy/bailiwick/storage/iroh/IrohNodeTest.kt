package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for IrohNode implementation.
 * Replaces the old IPFS tests with Iroh equivalents.
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
    fun nodeIdIs64Characters() {
        // Iroh node IDs are 64 hex characters (32 bytes Ed25519 public key)
        assertEquals(64, iroh.nodeId.length)
    }

    @Test
    fun storeAndRetrieveBlob() {
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
    fun documentStructureForBailiwick() {
        // Simulate the Bailiwick directory structure using Iroh docs

        // Store identity content
        val identityJson = """{"name": "TestUser", "profilePicHash": null}"""
        val identityHash = iroh.storeBlob(identityJson.toByteArray())

        // Store post content
        val postJson = """{"timestamp": 12345, "text": "Hello World", "files": [], "signature": "abc"}"""
        val postHash = iroh.storeBlob(postJson.toByteArray())

        // Store feed content
        val feedJson = """{"updatedAt": 12345, "posts": ["$postHash"]}"""
        val feedHash = iroh.storeBlob(feedJson.toByteArray())

        // Set up document structure (like IPNS publish)
        val myDoc = iroh.getMyDoc()
        myDoc.set("identity", identityHash.toByteArray())
        myDoc.set("feed/latest", feedHash.toByteArray())
        myDoc.set("circles/1", feedHash.toByteArray())

        // Verify we can read back the structure
        assertEquals(identityHash, String(myDoc.get("identity")!!))
        assertEquals(feedHash, String(myDoc.get("feed/latest")!!))
        assertEquals(feedHash, String(myDoc.get("circles/1")!!))

        // Verify we can traverse back to content
        val retrievedFeedHash = String(myDoc.get("feed/latest")!!)
        val feedData = iroh.getBlob(retrievedFeedHash)
        assertNotNull(feedData)
        assertTrue(String(feedData!!).contains(postHash))
    }

    @Test
    fun collectionsMaintainStructure() {
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
    fun hasBlobWorksCorrectly() {
        val hash = iroh.storeBlob("test data".toByteArray())

        assertTrue(iroh.hasBlob(hash))
        assertFalse(iroh.hasBlob("nonexistent-hash"))
    }

    @Test
    fun multipleDocsAreIndependent() {
        val doc1Id = iroh.createDoc()
        val doc2Id = iroh.createDoc()

        val doc1 = iroh.openDoc(doc1Id)!!
        val doc2 = iroh.openDoc(doc2Id)!!

        // Set different values in each doc
        doc1.set("key", "value1".toByteArray())
        doc2.set("key", "value2".toByteArray())

        // Verify they are independent
        assertEquals("value1", String(doc1.get("key")!!))
        assertEquals("value2", String(doc2.get("key")!!))
    }

    @Test
    fun docKeysListsAllKeys() {
        val doc = iroh.getMyDoc()

        doc.set("alpha", "1".toByteArray())
        doc.set("beta", "2".toByteArray())
        doc.set("gamma", "3".toByteArray())

        val keys = doc.keys()
        assertEquals(3, keys.size)
        assertTrue(keys.contains("alpha"))
        assertTrue(keys.contains("beta"))
        assertTrue(keys.contains("gamma"))
    }

    @Test
    fun docDeleteRemovesKey() {
        val doc = iroh.getMyDoc()
        doc.set("temporary", "data".toByteArray())

        assertNotNull(doc.get("temporary"))

        doc.delete("temporary")

        assertNull(doc.get("temporary"))
    }

    @Test
    fun isConnectedReturnsTrue() {
        // InMemoryIrohNode is always "connected"
        assertTrue(iroh.isConnected())
    }
}
