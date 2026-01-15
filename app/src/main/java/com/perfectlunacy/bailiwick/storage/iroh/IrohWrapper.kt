package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import android.util.Base64
import android.util.Log
import com.perfectlunacy.bailiwick.storage.BlobHash
import java.security.SecureRandom
import com.perfectlunacy.bailiwick.storage.NodeId
import computer.iroh.Blobs
import computer.iroh.Collection
import computer.iroh.Gossip
import computer.iroh.Hash
import computer.iroh.Iroh
import computer.iroh.IrohException
import computer.iroh.NodeOptions
import computer.iroh.NodeAddr
import computer.iroh.PublicKey
import computer.iroh.SetTagOption
import computer.iroh.BlobDownloadOptions
import computer.iroh.BlobFormat
import computer.iroh.DownloadCallback
import computer.iroh.DownloadProgress
import computer.iroh.DownloadProgressType

/**
 * Implementation of IrohNode using the iroh-ffi bindings.
 *
 * ## Error Handling Policy
 *
 * This class uses consistent error handling strategies:
 *
 * - **Write operations** (storeBlob, createCollection): Log error, rethrow exception.
 *   These operations must succeed for the app to function correctly.
 *
 * - **Read operations** (getBlob, getCollection): Log warning, return null/empty.
 *   Missing data is expected (e.g., content not yet synced), so failures are not exceptional.
 *
 * - **Query operations** (hasBlob, isConnected): Return false on error.
 *   These are best-effort queries where failure is equivalent to "not found/connected".
 *
 * - **Background operations** (shutdown): Log and suppress errors.
 *   These run in background contexts where failures shouldn't crash the caller.
 *
 * Note: Iroh Docs have been removed in favor of Gossip-based manifest synchronization.
 * See designs/proposals/007-gossip-manifest-sync.md for details.
 */
class IrohWrapper private constructor(
    private val iroh: Iroh,
    private val cachedNodeId: NodeId
) : IrohNode {

    companion object {
        private const val TAG = "IrohWrapper"
        private const val SECRET_KEY = "iroh_secret_key"

        /**
         * Initialize Iroh with persistent storage.
         */
        suspend fun create(context: Context): IrohWrapper {
            val dataDir = context.filesDir.resolve("iroh").absolutePath

            Log.i(TAG, "Initializing Iroh at $dataDir")

            // Get or create persistent secret key to maintain stable node identity
            val secretKey = getOrCreateSecretKey(context)

            // Create persistent Iroh node with our secret key
            // Note: enableDocs is false since we use Gossip for sync
            val options = NodeOptions(enableDocs = false, secretKey = secretKey)
            val iroh = Iroh.persistentWithOptions(dataDir, options)

            // Cache values that don't change
            val nodeIdStr = iroh.net().nodeId().toString()
            Log.i(TAG, "Iroh initialized. NodeId: $nodeIdStr")

            return IrohWrapper(iroh, nodeIdStr)
        }

        /**
         * Get or create a persistent secret key for the Iroh node.
         * This ensures the NodeId remains stable across app restarts.
         */
        private fun getOrCreateSecretKey(context: Context): ByteArray {
            val prefs = context.getSharedPreferences("iroh_config", Context.MODE_PRIVATE)
            val savedKey = prefs.getString(SECRET_KEY, null)

            if (savedKey != null) {
                Log.d(TAG, "Using existing secret key")
                return Base64.decode(savedKey, Base64.NO_WRAP)
            }

            // Generate a new 32-byte secret key
            val newKey = ByteArray(32)
            SecureRandom().nextBytes(newKey)

            // Save it for future use
            val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)
            prefs.edit().putString(SECRET_KEY, encoded).apply()
            Log.i(TAG, "Generated new secret key")

            return newKey
        }
    }

    private val blobs: Blobs = iroh.blobs()

    override suspend fun nodeId(): NodeId = cachedNodeId

    // ===== Blob Operations =====

    override suspend fun storeBlob(data: ByteArray): BlobHash {
        return try {
            // Use addBytesNamed to create a persistent named tag
            // This prevents garbage collection and allows sharing with peers
            val name = "blob-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
            val outcome = blobs.addBytesNamed(data, name)
            val hash = outcome.hash.toHex()
            Log.d(TAG, "Stored blob: $hash (${data.size} bytes)")
            hash
        } catch (e: IrohException) {
            Log.e(TAG, "Failed to store blob: ${e.message}")
            throw e
        }
    }

    override suspend fun getBlob(hash: BlobHash): ByteArray? {
        return try {
            val irohHash = Hash.fromString(hash)
            blobs.readToBytes(irohHash)
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to get blob $hash: ${e.message}")
            null
        }
    }

    override suspend fun downloadBlob(hash: BlobHash, nodeId: NodeId): ByteArray? {
        // First check if we already have it locally
        val local = getBlob(hash)
        if (local != null) {
            Log.d(TAG, "Blob $hash already available locally")
            return local
        }

        Log.d(TAG, "Downloading blob $hash from peer $nodeId")

        return try {
            val irohHash = Hash.fromString(hash)
            val publicKey = PublicKey.fromString(nodeId)
            val nodeAddr = NodeAddr(publicKey, null, listOf())

            // Use a named tag based on the hash to ensure persistence
            // Named tags prevent garbage collection and allow re-sharing
            val tag = SetTagOption.named("blob-$hash".toByteArray())
            val options = BlobDownloadOptions(
                BlobFormat.RAW,
                listOf(nodeAddr),
                tag
            )

            // Track download result
            var downloadFailed = false
            var failureReason: String? = null

            val callback = object : DownloadCallback {
                override suspend fun progress(progress: DownloadProgress) {
                    when (progress.type()) {
                        DownloadProgressType.ALL_DONE -> {
                            Log.d(TAG, "Blob $hash download complete")
                        }
                        DownloadProgressType.ABORT -> {
                            downloadFailed = true
                            failureReason = progress.asAbort().toString()
                            Log.w(TAG, "Blob $hash download aborted: $failureReason")
                        }
                        DownloadProgressType.PROGRESS -> {
                            // Just log progress
                            Log.d(TAG, "Blob $hash download in progress")
                        }
                        DownloadProgressType.FOUND -> {
                            Log.d(TAG, "Blob $hash found on peer")
                        }
                        else -> {
                            // Ignore other progress types
                        }
                    }
                }
            }

            // Download is a suspend function that completes when download finishes
            blobs.download(irohHash, options, callback)

            if (downloadFailed) {
                Log.w(TAG, "Blob $hash download failed: $failureReason")
                return null
            }

            // Now read from local store
            blobs.readToBytes(irohHash)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download blob $hash from $nodeId: ${e.message}")
            null
        }
    }

    override suspend fun hasBlob(hash: BlobHash): Boolean {
        return try {
            // In v0.28.1, there's no `has` method - use `size` instead
            // If size succeeds, the blob exists
            blobs.size(Hash.fromString(hash))
            true
        } catch (e: IrohException) {
            Log.d(TAG, "hasBlob($hash) failed: ${e.message}")
            false
        }
    }

    // ===== Collection Operations =====

    override suspend fun createCollection(entries: Map<String, BlobHash>): BlobHash {
        return try {
            val collection = Collection()
            entries.forEach { (name, hash) ->
                collection.push(name, Hash.fromString(hash))
            }
            val result = blobs.createCollection(collection, SetTagOption.auto(), listOf())
            val hash = result.hash.toHex()
            Log.d(TAG, "Created collection: $hash with ${entries.size} entries")
            hash
        } catch (e: IrohException) {
            Log.e(TAG, "Failed to create collection: ${e.message}")
            throw e
        }
    }

    override suspend fun getCollection(hash: BlobHash): Map<String, BlobHash>? {
        return try {
            val irohHash = Hash.fromString(hash)
            val collection = blobs.getCollection(irohHash)
            val result = mutableMapOf<String, BlobHash>()
            for (blob in collection.blobs()) {
                result[blob.name] = blob.link.toHex()
            }
            result
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to get collection $hash: ${e.message}")
            null
        }
    }

    // ===== Network =====

    override suspend fun isConnected(): Boolean {
        return try {
            // Check if we have a home relay connection
            val netInfo = iroh.net().homeRelay()
            netInfo != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getNodeAddresses(): List<String> {
        return try {
            val addresses = mutableListOf<String>()

            // Add the home relay URL if available
            iroh.net().homeRelay()?.let { relay ->
                addresses.add(relay.toString())
            }

            // Note: Direct addresses can be obtained from iroh.net().localAddresses()
            // but these may not be reachable from outside the local network.
            // The relay is typically the most reliable way to reach a mobile peer.

            addresses
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get node addresses: ${e.message}")
            emptyList()
        }
    }

    override suspend fun shutdown() {
        try {
            iroh.node().shutdown(false)  // graceful shutdown
            Log.i(TAG, "Iroh shutdown")
        } catch (e: Exception) {
            Log.w(TAG, "Error during shutdown: ${e.message}")
        }
    }

    // ===== Gossip =====

    override fun getGossip(): Gossip = iroh.gossip()
}
