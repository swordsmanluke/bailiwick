package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import computer.iroh.Gossip
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of IrohNode for unit testing.
 * Uses HashMaps to simulate blob storage.
 */
class InMemoryIrohNode(
    private val cachedNodeId: NodeId = generateNodeId()
) : IrohNode {

    companion object {
        private fun generateNodeId(): NodeId = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")

        /**
         * Compute a BLAKE3-like hash (using SHA-256 as a stand-in for tests).
         */
        fun computeHash(data: ByteArray): BlobHash {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(data).joinToString("") { "%02x".format(it) }
        }
    }

    // Blob storage: hash -> data
    private val blobs = ConcurrentHashMap<BlobHash, ByteArray>()

    // Collection storage: hash -> (name -> blobHash)
    private val collections = ConcurrentHashMap<BlobHash, Map<String, BlobHash>>()

    override suspend fun nodeId(): NodeId = cachedNodeId

    // ===== Blob Operations =====

    override suspend fun storeBlob(data: ByteArray): BlobHash {
        val hash = computeHash(data)
        blobs[hash] = data.copyOf()
        return hash
    }

    override suspend fun getBlob(hash: BlobHash): ByteArray? {
        return blobs[hash]?.copyOf()
    }

    override suspend fun hasBlob(hash: BlobHash): Boolean {
        return blobs.containsKey(hash)
    }

    override suspend fun downloadBlob(hash: BlobHash, nodeId: NodeId): ByteArray? {
        // In-memory mock: just return local blob if available
        // In real implementation, this would download from the peer
        return blobs[hash]?.copyOf()
    }

    // ===== Collection Operations =====

    override suspend fun createCollection(entries: Map<String, BlobHash>): BlobHash {
        // Serialize collection to create a deterministic hash
        val serialized = entries.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}:${it.value}" }
            .toByteArray()
        val hash = computeHash(serialized)
        collections[hash] = entries.toMap()
        return hash
    }

    override suspend fun getCollection(hash: BlobHash): Map<String, BlobHash>? {
        return collections[hash]?.toMap()
    }

    // ===== Network =====

    override suspend fun isConnected(): Boolean = true

    override suspend fun getNodeAddresses(): List<String> {
        // Return mock addresses for testing
        return listOf("mock://localhost:1234")
    }

    override suspend fun addPeerAddresses(nodeId: NodeId, relayUrl: String?, directAddresses: List<String>) {
        // No-op for in-memory implementation
    }

    override suspend fun shutdown() {
        // No-op for in-memory implementation
    }

    // ===== Gossip =====

    override fun getGossip(): Gossip {
        throw UnsupportedOperationException("Gossip not available in test implementation")
    }

    // ===== Test Utilities =====

    /**
     * Get the number of stored blobs (for test assertions).
     */
    fun blobCount(): Int = blobs.size

    /**
     * Get the number of stored collections (for test assertions).
     */
    fun collectionCount(): Int = collections.size

    /**
     * Clear all data (for test isolation).
     */
    fun clear() {
        blobs.clear()
        collections.clear()
    }
}
