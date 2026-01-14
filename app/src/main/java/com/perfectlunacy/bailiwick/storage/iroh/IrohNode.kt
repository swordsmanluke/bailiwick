package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Interface for Iroh operations.
 * This replaces the old IPFS interface.
 *
 * All methods are suspend functions since the underlying Iroh FFI is async.
 *
 * ## Error Handling Contract
 *
 * Methods follow consistent error handling strategies:
 *
 * - **Write operations** ([storeBlob], [createCollection], [createDoc]): Throw exceptions on failure.
 *   These operations must succeed for the app to function correctly.
 *
 * - **Read operations** ([getBlob], [getCollection], [openDoc], [IrohDoc.get], [IrohDoc.keys]):
 *   Return `null` or empty on failure. Missing data is expected (e.g., content not yet synced).
 *
 * - **Query operations** ([hasBlob], [isConnected]): Return `false` on error.
 *   These are best-effort queries where failure is equivalent to "not found/connected".
 *
 * - **Background operations** ([shutdown], [IrohDoc.syncWith], [IrohDoc.subscribe]):
 *   Suppress errors. These run in background contexts where failures shouldn't crash the caller.
 */
interface IrohNode {
    /**
     * This node's unique identifier (Ed25519 public key).
     * Value is cached at construction time.
     */
    suspend fun nodeId(): NodeId

    /**
     * The namespace ID of this node's primary document.
     * Other peers subscribe to this to receive our updates.
     * Value is cached at construction time.
     */
    suspend fun myDocNamespaceId(): DocNamespaceId

    /**
     * Get a shareable ticket for this node's primary document.
     * This ticket can be shared with other peers to allow them to join and sync.
     * @throws IrohException on failure to generate ticket
     */
    suspend fun myDocTicket(): String

    // ===== Blob Operations (content-addressed storage) =====

    /**
     * Store data and return its BLAKE3 hash.
     * @throws IrohException on storage failure
     */
    suspend fun storeBlob(data: ByteArray): BlobHash

    /**
     * Retrieve data by hash from local storage, or null if not found or on error.
     * Does NOT download from network - use [downloadBlob] for that.
     */
    suspend fun getBlob(hash: BlobHash): ByteArray?

    /**
     * Download a blob from a specific peer if not already available locally.
     * First checks local storage, and if not found, downloads from the peer.
     * @param hash The blob hash to download
     * @param nodeId The peer to download from
     * @return The blob data, or null if download failed
     */
    suspend fun downloadBlob(hash: BlobHash, nodeId: NodeId): ByteArray?

    /**
     * Check if we have the blob locally.
     * Returns false if not found or on error.
     */
    suspend fun hasBlob(hash: BlobHash): Boolean

    // ===== Collection Operations (like IPFS directories) =====

    /**
     * Create a collection (ordered list of named blobs) and return its hash.
     * @throws IrohException on creation failure
     */
    suspend fun createCollection(entries: Map<String, BlobHash>): BlobHash

    /**
     * Get a collection's entries, or null if not found or not a valid collection.
     */
    suspend fun getCollection(hash: BlobHash): Map<String, BlobHash>?

    // ===== Doc Operations (replaces IPNS) =====

    /**
     * Create a new document and return its namespace ID.
     * @throws IrohException on creation failure
     */
    suspend fun createDoc(): DocNamespaceId

    /**
     * Open an existing document by namespace ID.
     * Returns null if document not found or on error.
     * Note: This only works for documents you own or have already joined.
     */
    suspend fun openDoc(namespaceId: DocNamespaceId): IrohDoc?

    /**
     * Join a remote document using a ticket.
     * The ticket is obtained from the document owner via [myDocTicket].
     * Returns the joined document, or null on error.
     */
    suspend fun joinDoc(ticket: String): IrohDoc?

    /**
     * Delete a document from local storage.
     * This permanently removes the document's secret key and all entries.
     * Use this to force a completely fresh sync when rejoining.
     * Errors are logged but not thrown (safe to call on non-existent docs).
     */
    suspend fun dropDoc(namespaceId: DocNamespaceId)

    /**
     * Get this node's primary document for publishing content.
     * This document is created during initialization and always exists.
     */
    suspend fun getMyDoc(): IrohDoc

    // ===== Network =====

    /**
     * Check if we have network connectivity.
     * Returns false if not connected or on error.
     */
    suspend fun isConnected(): Boolean

    /**
     * Shutdown the node gracefully.
     * Errors are logged but not thrown.
     */
    suspend fun shutdown()
}

/**
 * Handle for an active subscription that allows cleanup.
 * Call [unsubscribe] when the subscription is no longer needed.
 */
interface Subscription {
    /**
     * Cancel this subscription. No more updates will be delivered.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    fun unsubscribe()
}

/**
 * Interface for operations on an Iroh Document.
 * Documents are mutable key-value stores that sync between peers.
 *
 * All methods are suspend functions since the underlying Iroh FFI is async.
 *
 * See [IrohNode] for error handling contract documentation.
 */
interface IrohDoc {
    /**
     * The document's namespace ID.
     * Value is cached at construction time.
     */
    suspend fun namespaceId(): DocNamespaceId

    /**
     * Set a key-value pair in the document.
     * @throws IrohException on write failure
     */
    suspend fun set(key: String, value: ByteArray)

    /**
     * Get a value by key, or null if not found or on error.
     */
    suspend fun get(key: String): ByteArray?

    /**
     * Delete a key from the document.
     * @throws IrohException on delete failure
     */
    suspend fun delete(key: String)

    /**
     * List all keys in the document.
     * Returns empty list if document is empty or on error.
     */
    suspend fun keys(): List<String>

    /**
     * List all keys matching a prefix.
     * Returns empty list if no matches or on error.
     * @param prefix The prefix to match against keys
     */
    suspend fun keysWithPrefix(prefix: String): List<String>

    /**
     * Subscribe to updates from other peers.
     * The callback will be invoked when new data arrives.
     * Errors in subscription setup or callback are logged but not thrown.
     *
     * @return A [Subscription] handle that can be used to cancel the subscription.
     */
    suspend fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit): Subscription

    /**
     * Start syncing this document with a specific peer.
     * Errors are logged but not thrown.
     */
    suspend fun syncWith(nodeId: NodeId)

    /**
     * Sync with a peer and wait for completion.
     * Waits for SYNC_FINISHED event with a timeout.
     * @param nodeId The peer to sync with
     * @param timeoutMs Maximum time to wait for sync completion
     * @return true if sync completed successfully, false on timeout or error
     */
    suspend fun syncWithAndWait(nodeId: NodeId, timeoutMs: Long = 30000): Boolean

    /**
     * Debug method: Get all entries for a key from all authors.
     * Returns a list of (authorId, hash) pairs.
     */
    suspend fun getAllEntriesForKey(key: String): List<Pair<String, String>>

    /**
     * Leave (stop syncing) this document.
     * This clears the local replica, forcing a fresh sync on next join.
     */
    suspend fun leave()
}
