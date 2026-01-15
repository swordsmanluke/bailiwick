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

    // ===== Utility Tests =====

    @Test
    fun `clear resets all data`() = runBlocking {
        iroh.storeBlob("data".toByteArray())

        iroh.clear()

        assertEquals(0, iroh.blobCount())
    }

    @Test
    fun `isConnected returns true`() = runBlocking {
        assertTrue(iroh.isConnected())
    }

    @Test
    fun `nodeId is 64 characters`() = runBlocking {
        assertEquals(64, iroh.nodeId().length)
    }

    @Test
    fun `getNodeAddresses returns mock addresses`() = runBlocking {
        val addresses = iroh.getNodeAddresses()
        assertTrue(addresses.isNotEmpty())
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
    fun `shutdown does not throw`() = runBlocking {
        // Store some data first
        iroh.storeBlob("data".toByteArray())

        // Should not throw
        iroh.shutdown()

        // Node should still be usable (in-memory impl doesn't actually shut down)
        assertTrue(iroh.isConnected())
    }

    @Test
    fun `downloadBlob returns local blob`() = runBlocking {
        val data = "test data".toByteArray()
        val hash = iroh.storeBlob(data)

        val downloaded = iroh.downloadBlob(hash, "some-node-id")
        assertNotNull(downloaded)
        assertArrayEquals(data, downloaded)
    }

    @Test
    fun `downloadBlob returns null for unknown hash`() = runBlocking {
        val downloaded = iroh.downloadBlob("unknown-hash", "some-node-id")
        assertNull(downloaded)
    }
}
