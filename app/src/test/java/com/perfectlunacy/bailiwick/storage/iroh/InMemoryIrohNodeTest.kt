package com.perfectlunacy.bailiwick.storage.iroh

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InMemoryIrohNodeTest {

    private lateinit var iroh: InMemoryIrohNode

    @Before
    fun setUp() {
        iroh = InMemoryIrohNode()
    }

    // ===== Blob Tests =====

    @Test
    fun `storeBlob returns consistent hash for same data`() {
        val data = "Hello, World!".toByteArray()
        val hash1 = iroh.storeBlob(data)
        val hash2 = iroh.storeBlob(data)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `storeBlob returns different hash for different data`() {
        val hash1 = iroh.storeBlob("Hello".toByteArray())
        val hash2 = iroh.storeBlob("World".toByteArray())

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `getBlob retrieves stored data`() {
        val data = "Test data".toByteArray()
        val hash = iroh.storeBlob(data)

        val retrieved = iroh.getBlob(hash)

        assertArrayEquals(data, retrieved)
    }

    @Test
    fun `getBlob returns null for unknown hash`() {
        val result = iroh.getBlob("nonexistent-hash")

        assertNull(result)
    }

    @Test
    fun `hasBlob returns true for stored blob`() {
        val hash = iroh.storeBlob("data".toByteArray())

        assertTrue(iroh.hasBlob(hash))
    }

    @Test
    fun `hasBlob returns false for unknown hash`() {
        assertFalse(iroh.hasBlob("unknown-hash"))
    }

    @Test
    fun `stored blob is independent of original array`() {
        val data = "mutable".toByteArray()
        val hash = iroh.storeBlob(data)

        // Mutate original
        data[0] = 'X'.code.toByte()

        // Retrieved data should be unchanged
        val retrieved = iroh.getBlob(hash)
        assertEquals("mutable", String(retrieved!!))
    }

    // ===== Collection Tests =====

    @Test
    fun `createCollection stores and retrieves entries`() {
        val blob1 = iroh.storeBlob("file1".toByteArray())
        val blob2 = iroh.storeBlob("file2".toByteArray())

        val entries = mapOf("a.txt" to blob1, "b.txt" to blob2)
        val collectionHash = iroh.createCollection(entries)

        val retrieved = iroh.getCollection(collectionHash)

        assertEquals(entries, retrieved)
    }

    @Test
    fun `getCollection returns null for unknown hash`() {
        assertNull(iroh.getCollection("unknown"))
    }

    @Test
    fun `createCollection returns consistent hash for same entries`() {
        val blob = iroh.storeBlob("data".toByteArray())
        val entries = mapOf("file.txt" to blob)

        val hash1 = iroh.createCollection(entries)
        val hash2 = iroh.createCollection(entries)

        assertEquals(hash1, hash2)
    }

    // ===== Doc Tests =====

    @Test
    fun `getMyDoc returns the primary document`() {
        val doc = iroh.getMyDoc()

        assertEquals(iroh.myDocNamespaceId, doc.namespaceId)
    }

    @Test
    fun `createDoc creates a new document`() {
        val docId = iroh.createDoc()

        assertNotNull(docId)
        assertNotEquals(iroh.myDocNamespaceId, docId)
    }

    @Test
    fun `openDoc retrieves created document`() {
        val docId = iroh.createDoc()
        val doc = iroh.openDoc(docId)

        assertNotNull(doc)
        assertEquals(docId, doc!!.namespaceId)
    }

    @Test
    fun `openDoc returns null for unknown namespace`() {
        assertNull(iroh.openDoc("unknown-namespace"))
    }

    @Test
    fun `document set and get work correctly`() {
        val doc = iroh.getMyDoc()
        val value = "test value".toByteArray()

        doc.set("key1", value)

        assertArrayEquals(value, doc.get("key1"))
    }

    @Test
    fun `document get returns null for unknown key`() {
        val doc = iroh.getMyDoc()

        assertNull(doc.get("unknown-key"))
    }

    @Test
    fun `document delete removes key`() {
        val doc = iroh.getMyDoc()
        doc.set("key", "value".toByteArray())

        doc.delete("key")

        assertNull(doc.get("key"))
    }

    @Test
    fun `document keys returns all keys`() {
        val doc = iroh.getMyDoc()
        doc.set("a", "1".toByteArray())
        doc.set("b", "2".toByteArray())
        doc.set("c", "3".toByteArray())

        val keys = doc.keys()

        assertEquals(3, keys.size)
        assertTrue(keys.containsAll(listOf("a", "b", "c")))
    }

    @Test
    fun `document subscribe notifies on updates`() {
        val doc = iroh.getMyDoc()
        var notifiedKey: String? = null
        var notifiedValue: ByteArray? = null

        doc.subscribe { key, value ->
            notifiedKey = key
            notifiedValue = value
        }

        doc.set("mykey", "myvalue".toByteArray())

        assertEquals("mykey", notifiedKey)
        assertArrayEquals("myvalue".toByteArray(), notifiedValue)
    }

    // ===== Utility Tests =====

    @Test
    fun `clear resets all data`() {
        iroh.storeBlob("data".toByteArray())
        iroh.getMyDoc().set("key", "value".toByteArray())

        iroh.clear()

        assertEquals(0, iroh.blobCount())
        assertEquals(0, (iroh.getMyDoc() as InMemoryIrohDoc).entryCount())
    }

    @Test
    fun `isConnected returns true`() {
        assertTrue(iroh.isConnected())
    }

    @Test
    fun `nodeId is 64 characters`() {
        assertEquals(64, iroh.nodeId.length)
    }
}
