package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory implementation of IrohNode for unit testing.
 * Uses HashMaps to simulate blob storage and documents.
 */
class InMemoryIrohNode(
    private val cachedNodeId: NodeId = generateNodeId(),
    private val cachedMyDocNamespaceId: DocNamespaceId = generateDocId()
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
    private val myDoc = InMemoryIrohDoc(cachedMyDocNamespaceId)

    init {
        docs[cachedMyDocNamespaceId] = myDoc
    }

    override suspend fun nodeId(): NodeId = cachedNodeId

    override suspend fun myDocNamespaceId(): DocNamespaceId = cachedMyDocNamespaceId

    override suspend fun myDocTicket(): String {
        // In-memory mock: return a fake ticket string based on namespace
        return "ticket:$cachedMyDocNamespaceId"
    }

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

    // ===== Doc Operations =====

    override suspend fun createDoc(): DocNamespaceId {
        val id = generateDocId()
        docs[id] = InMemoryIrohDoc(id)
        return id
    }

    override suspend fun openDoc(namespaceId: DocNamespaceId): IrohDoc? {
        return docs[namespaceId]
    }

    override suspend fun joinDoc(ticket: String): IrohDoc? {
        // In-memory mock: parse ticket and create/return doc
        val namespaceId = ticket.removePrefix("ticket:")
        return docs.getOrPut(namespaceId) { InMemoryIrohDoc(namespaceId) }
    }

    override suspend fun getMyDoc(): IrohDoc {
        return myDoc
    }

    // ===== Network =====

    override suspend fun isConnected(): Boolean = true

    override suspend fun shutdown() {
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
        docs[cachedMyDocNamespaceId] = myDoc
        myDoc.clear()
    }
}

/**
 * In-memory implementation of IrohDoc for unit testing.
 */
class InMemoryIrohDoc(
    private val cachedNamespaceId: DocNamespaceId
) : IrohDoc {

    private val data = ConcurrentHashMap<String, ByteArray>()
    private val subscribers = CopyOnWriteArrayList<(String, ByteArray) -> Unit>()

    override suspend fun namespaceId(): DocNamespaceId = cachedNamespaceId

    override suspend fun set(key: String, value: ByteArray) {
        data[key] = value.copyOf()
        // Notify subscribers - wrap in try-catch to prevent subscriber exceptions from breaking set()
        subscribers.forEach { subscriber ->
            try {
                subscriber(key, value)
            } catch (e: Exception) {
                // Log but don't propagate subscriber exceptions
            }
        }
    }

    override suspend fun get(key: String): ByteArray? {
        return data[key]?.copyOf()
    }

    override suspend fun delete(key: String) {
        data.remove(key)
    }

    override suspend fun keys(): List<String> {
        return data.keys.toList()
    }

    override suspend fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit): Subscription {
        subscribers.add(onUpdate)
        return object : Subscription {
            override fun unsubscribe() {
                subscribers.remove(onUpdate)
            }
        }
    }

    override suspend fun syncWith(nodeId: NodeId) {
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
