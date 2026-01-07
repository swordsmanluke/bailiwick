package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of IrohNode for unit testing.
 * Uses HashMaps to simulate blob storage and documents.
 */
class InMemoryIrohNode(
    override val nodeId: NodeId = generateNodeId(),
    override val myDocNamespaceId: DocNamespaceId = generateDocId()
) : IrohNode {

    companion object {
        private fun generateNodeId(): NodeId = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")

        private fun generateDocId(): DocNamespaceId = UUID.randomUUID().toString().replace("-", "")

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

    // Document storage: namespaceId -> InMemoryIrohDoc
    private val docs = ConcurrentHashMap<DocNamespaceId, InMemoryIrohDoc>()

    // My primary document
    private val myDoc = InMemoryIrohDoc(myDocNamespaceId)

    init {
        docs[myDocNamespaceId] = myDoc
    }

    // ===== Blob Operations =====

    override fun storeBlob(data: ByteArray): BlobHash {
        val hash = computeHash(data)
        blobs[hash] = data.copyOf()
        return hash
    }

    override fun getBlob(hash: BlobHash): ByteArray? {
        return blobs[hash]?.copyOf()
    }

    override fun hasBlob(hash: BlobHash): Boolean {
        return blobs.containsKey(hash)
    }

    // ===== Collection Operations =====

    override fun createCollection(entries: Map<String, BlobHash>): BlobHash {
        // Serialize collection to create a deterministic hash
        val serialized = entries.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}:${it.value}" }
            .toByteArray()
        val hash = computeHash(serialized)
        collections[hash] = entries.toMap()
        return hash
    }

    override fun getCollection(hash: BlobHash): Map<String, BlobHash>? {
        return collections[hash]?.toMap()
    }

    // ===== Doc Operations =====

    override fun createDoc(): DocNamespaceId {
        val id = generateDocId()
        docs[id] = InMemoryIrohDoc(id)
        return id
    }

    override fun openDoc(namespaceId: DocNamespaceId): IrohDoc? {
        return docs[namespaceId]
    }

    override fun getMyDoc(): IrohDoc {
        return myDoc
    }

    // ===== Network =====

    override fun isConnected(): Boolean = true

    override fun shutdown() {
        // No-op for in-memory implementation
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
        docs.clear()
        docs[myDocNamespaceId] = myDoc
        myDoc.clear()
    }
}

/**
 * In-memory implementation of IrohDoc for unit testing.
 */
class InMemoryIrohDoc(
    override val namespaceId: DocNamespaceId
) : IrohDoc {

    private val data = ConcurrentHashMap<String, ByteArray>()
    private val subscribers = mutableListOf<(String, ByteArray) -> Unit>()

    override fun set(key: String, value: ByteArray) {
        data[key] = value.copyOf()
        // Notify subscribers
        subscribers.forEach { it(key, value) }
    }

    override fun get(key: String): ByteArray? {
        return data[key]?.copyOf()
    }

    override fun delete(key: String) {
        data.remove(key)
    }

    override fun keys(): List<String> {
        return data.keys.toList()
    }

    override fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit) {
        subscribers.add(onUpdate)
    }

    override fun syncWith(nodeId: NodeId) {
        // No-op for in-memory implementation - can't sync without network
    }

    // ===== Test Utilities =====

    /**
     * Get the number of entries (for test assertions).
     */
    fun entryCount(): Int = data.size

    /**
     * Clear all data (for test isolation).
     */
    fun clear() {
        data.clear()
    }
}
