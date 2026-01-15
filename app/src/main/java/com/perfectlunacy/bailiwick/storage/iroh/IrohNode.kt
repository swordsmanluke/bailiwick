package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import computer.iroh.Gossip

/**
 * Interface for Iroh operations.
 *
 * All methods are suspend functions since the underlying Iroh FFI is async.
 *
 * ## Error Handling Contract
 *
 * Methods follow consistent error handling strategies:
 *
 * - **Write operations** ([storeBlob], [createCollection]): Throw exceptions on failure.
 *   These operations must succeed for the app to function correctly.
 *
 * - **Read operations** ([getBlob], [getCollection]):
 *   Return `null` or empty on failure. Missing data is expected (e.g., content not yet synced).
 *
 * - **Query operations** ([hasBlob], [isConnected]): Return `false` on error.
 *   These are best-effort queries where failure is equivalent to "not found/connected".
 *
 * - **Background operations** ([shutdown]):
 *   Suppress errors. These run in background contexts where failures shouldn't crash the caller.
 *
 * Note: Iroh Docs have been removed in favor of Gossip-based manifest synchronization.
 * See designs/proposals/007-gossip-manifest-sync.md for details.
 */
interface IrohNode {
    /**
     * This node's unique identifier (Ed25519 public key).
     * Value is cached at construction time.
     */
    suspend fun nodeId(): NodeId

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

    // ===== Network =====

    /**
     * Check if we have network connectivity.
     * Returns false if not connected or on error.
     */
    suspend fun isConnected(): Boolean

    /**
     * Get this node's known addresses for sharing with peers.
     * Includes relay addresses and any discovered direct addresses.
     * Returns empty list if no addresses available.
     */
    suspend fun getNodeAddresses(): List<String>

    /**
     * Shutdown the node gracefully.
     * Errors are logged but not thrown.
     */
    suspend fun shutdown()

    // ===== Gossip =====

    /**
     * Get the Gossip interface for pub/sub messaging.
     * Used by GossipService for real-time sync.
     */
    fun getGossip(): Gossip
}
