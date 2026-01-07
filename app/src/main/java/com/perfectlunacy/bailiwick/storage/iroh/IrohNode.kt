package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Interface for Iroh operations.
 * This replaces the old IPFS interface.
 */
interface IrohNode {
    /**
     * This node's unique identifier (Ed25519 public key).
     */
    val nodeId: NodeId

    /**
     * The namespace ID of this node's primary document.
     * Other peers subscribe to this to receive our updates.
     */
    val myDocNamespaceId: DocNamespaceId

    // ===== Blob Operations (content-addressed storage) =====

    /**
     * Store data and return its BLAKE3 hash.
     */
    fun storeBlob(data: ByteArray): BlobHash

    /**
     * Retrieve data by hash, or null if not found.
     */
    fun getBlob(hash: BlobHash): ByteArray?

    /**
     * Check if we have the blob locally.
     */
    fun hasBlob(hash: BlobHash): Boolean

    // ===== Collection Operations (like IPFS directories) =====

    /**
     * Create a collection (ordered list of named blobs) and return its hash.
     */
    fun createCollection(entries: Map<String, BlobHash>): BlobHash

    /**
     * Get a collection's entries, or null if not a valid collection.
     */
    fun getCollection(hash: BlobHash): Map<String, BlobHash>?

    // ===== Doc Operations (replaces IPNS) =====

    /**
     * Create a new document and return its namespace ID.
     */
    fun createDoc(): DocNamespaceId

    /**
     * Open an existing document by namespace ID.
     */
    fun openDoc(namespaceId: DocNamespaceId): IrohDoc?

    /**
     * Get this node's primary document for publishing content.
     */
    fun getMyDoc(): IrohDoc

    // ===== Network =====

    /**
     * Check if we have network connectivity.
     */
    fun isConnected(): Boolean

    /**
     * Shutdown the node gracefully.
     */
    fun shutdown()
}

/**
 * Interface for operations on an Iroh Document.
 * Documents are mutable key-value stores that sync between peers.
 */
interface IrohDoc {
    /**
     * The document's namespace ID.
     */
    val namespaceId: DocNamespaceId

    /**
     * Set a key-value pair in the document.
     */
    fun set(key: String, value: ByteArray)

    /**
     * Get a value by key, or null if not found.
     */
    fun get(key: String): ByteArray?

    /**
     * Delete a key from the document.
     */
    fun delete(key: String)

    /**
     * List all keys in the document.
     */
    fun keys(): List<String>

    /**
     * Subscribe to updates from other peers.
     * The callback will be invoked when new data arrives.
     */
    fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit)

    /**
     * Start syncing this document with a specific peer.
     */
    fun syncWith(nodeId: NodeId)
}
