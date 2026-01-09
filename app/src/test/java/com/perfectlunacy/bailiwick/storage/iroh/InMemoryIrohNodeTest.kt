package com.perfectlunacy.bailiwick.storage.iroh

import kotlinx.coroutines.runBlocking
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
    fun `storeBlob returns consistent hash for same data`() = runBlocking {
        val data = "Hello, World!".toByteArray()
        val hash1 = iroh.storeBlob(data)
        val hash2 = iroh.storeBlob(data)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `storeBlob returns different hash for different data`() = runBlocking {
        val hash1 = iroh.storeBlob("Hello".toByteArray())
        val hash2 = iroh.storeBlob("World".toByteArray())

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `getBlob retrieves stored data`() = runBlocking {
        val data = "Test data".toByteArray()
        val hash = iroh.storeBlob(data)

        val retrieved = iroh.getBlob(hash)

        assertArrayEquals(data, retrieved)
    }

    @Test
    fun `getBlob returns null for unknown hash`() = runBlocking {
        val result = iroh.getBlob("nonexistent-hash")

        assertNull(result)
    }

    @Test
    fun `hasBlob returns true for stored blob`() = runBlocking {
        val hash = iroh.storeBlob("data".toByteArray())

        assertTrue(iroh.hasBlob(hash))
    }

    @Test
    fun `hasBlob returns false for unknown hash`() = runBlocking {
        assertFalse(iroh.hasBlob("unknown-hash"))
    }

    @Test
    fun `stored blob is independent of original array`() = runBlocking {
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
    fun `createCollection stores and retrieves entries`() = runBlocking {
        val blob1 = iroh.storeBlob("file1".toByteArray())
        val blob2 = iroh.storeBlob("file2".toByteArray())

        val entries = mapOf("a.txt" to blob1, "b.txt" to blob2)
        val collectionHash = iroh.createCollection(entries)

        val retrieved = iroh.getCollection(collectionHash)

        assertEquals(entries, retrieved)
    }

    @Test
    fun `getCollection returns null for unknown hash`() = runBlocking {
        assertNull(iroh.getCollection("unknown"))
    }

    @Test
    fun `createCollection returns consistent hash for same entries`() = runBlocking {
        val blob = iroh.storeBlob("data".toByteArray())
        val entries = mapOf("file.txt" to blob)

        val hash1 = iroh.createCollection(entries)
        val hash2 = iroh.createCollection(entries)

        assertEquals(hash1, hash2)
    }

    // ===== Doc Tests =====

    @Test
    fun `getMyDoc returns the primary document`() = runBlocking {
        val doc = iroh.getMyDoc()

        assertEquals(iroh.myDocNamespaceId(), doc.namespaceId())
    }

    @Test
    fun `createDoc creates a new document`() = runBlocking {
        val docId = iroh.createDoc()

        assertNotNull(docId)
        assertNotEquals(iroh.myDocNamespaceId(), docId)
    }

    @Test
    fun `openDoc retrieves created document`() = runBlocking {
        val docId = iroh.createDoc()
        val doc = iroh.openDoc(docId)

        assertNotNull(doc)
        assertEquals(docId, doc!!.namespaceId())
    }

    @Test
    fun `openDoc returns null for unknown namespace`() = runBlocking {
        assertNull(iroh.openDoc("unknown-namespace"))
    }

    @Test
    fun `document set and get work correctly`() = runBlocking {
        val doc = iroh.getMyDoc()
        val value = "test value".toByteArray()

        doc.set("key1", value)

        assertArrayEquals(value, doc.get("key1"))
    }

    @Test
    fun `document get returns null for unknown key`() = runBlocking {
        val doc = iroh.getMyDoc()

        assertNull(doc.get("unknown-key"))
    }

    @Test
    fun `document delete removes key`() = runBlocking {
        val doc = iroh.getMyDoc()
        doc.set("key", "value".toByteArray())

        doc.delete("key")

        assertNull(doc.get("key"))
    }

    @Test
    fun `document keys returns all keys`() = runBlocking {
        val doc = iroh.getMyDoc()
        doc.set("a", "1".toByteArray())
        doc.set("b", "2".toByteArray())
        doc.set("c", "3".toByteArray())

        val keys = doc.keys()

        assertEquals(3, keys.size)
        assertTrue(keys.containsAll(listOf("a", "b", "c")))
    }

    @Test
    fun `document subscribe notifies on updates`() = runBlocking {
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
    fun `clear resets all data`() = runBlocking {
        iroh.storeBlob("data".toByteArray())
        iroh.getMyDoc().set("key", "value".toByteArray())

        iroh.clear()

        assertEquals(0, iroh.blobCount())
        assertEquals(0, (iroh.getMyDoc() as InMemoryIrohDoc).entryCount())
    }

    @Test
    fun `isConnected returns true`() = runBlocking {
        assertTrue(iroh.isConnected())
    }

    @Test
    fun `nodeId is 64 characters`() = runBlocking {
        assertEquals(64, iroh.nodeId().length)
    }

    // ===== Edge Case Tests =====

    @Test
    fun `storeBlob handles empty byte array`() = runBlocking {
        val hash = iroh.storeBlob(ByteArray(0))

        assertNotNull(hash)
        val retrieved = iroh.getBlob(hash)
        assertNotNull(retrieved)
        assertEquals(0, retrieved!!.size)
    }

    @Test
    fun `createCollection handles empty map`() = runBlocking {
        val hash = iroh.createCollection(emptyMap())

        assertNotNull(hash)
        val retrieved = iroh.getCollection(hash)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isEmpty())
    }

    @Test
    fun `document delete on non-existent key does not throw`() = runBlocking {
        val doc = iroh.getMyDoc()

        // Should not throw
        doc.delete("non-existent-key")

        // Key still doesn't exist
        assertNull(doc.get("non-existent-key"))
    }

    @Test
    fun `document keys returns empty list on empty document`() = runBlocking {
        val docId = iroh.createDoc()
        val doc = iroh.openDoc(docId)!!

        val keys = doc.keys()

        assertTrue(keys.isEmpty())
    }

    @Test
    fun `document subscribe with multiple subscribers notifies all`() = runBlocking {
        val doc = iroh.getMyDoc()
        val notifications1 = mutableListOf<String>()
        val notifications2 = mutableListOf<String>()

        doc.subscribe { key, _ -> notifications1.add(key) }
        doc.subscribe { key, _ -> notifications2.add(key) }

        doc.set("key1", "value1".toByteArray())
        doc.set("key2", "value2".toByteArray())

        assertEquals(listOf("key1", "key2"), notifications1)
        assertEquals(listOf("key1", "key2"), notifications2)
    }

    @Test
    fun `shutdown does not throw`() = runBlocking {
        // Store some data first
        iroh.storeBlob("data".toByteArray())
        iroh.getMyDoc().set("key", "value".toByteArray())

        // Should not throw
        iroh.shutdown()

        // Node should still be usable (in-memory impl doesn't actually shut down)
        assertTrue(iroh.isConnected())
    }

    @Test
    fun `syncWith does not throw for valid nodeId`() = runBlocking {
        val doc = iroh.getMyDoc()

        // Should not throw - syncWith is a no-op in InMemoryIrohNode
        doc.syncWith("some-node-id")
    }

    @Test
    fun `document set value is independent of original array`() = runBlocking {
        val doc = iroh.getMyDoc()
        val value = "original".toByteArray()

        doc.set("key", value)

        // Mutate original
        value[0] = 'X'.code.toByte()

        // Retrieved value should be unchanged
        assertEquals("original", String(doc.get("key")!!))
    }

    @Test
    fun `subscription unsubscribe stops notifications`() = runBlocking {
        val doc = iroh.getMyDoc()
        val notifications = mutableListOf<String>()

        val subscription = doc.subscribe { key, _ -> notifications.add(key) }

        doc.set("key1", "value1".toByteArray())
        assertEquals(1, notifications.size)

        // Unsubscribe
        subscription.unsubscribe()

        // Should not receive this notification
        doc.set("key2", "value2".toByteArray())
        assertEquals(1, notifications.size) // Still 1, not 2

        assertEquals(listOf("key1"), notifications)
    }
}
