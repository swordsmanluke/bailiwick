package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.db.ActionType as DbActionType
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.BlobCache
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.crypto.KeyStorage
import com.perfectlunacy.bailiwick.storage.iroh.IrohDoc
import com.perfectlunacy.bailiwick.storage.iroh.IrohDocKeys
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider
import java.io.File

/**
 * Downloads content from peer Docs.
 *
 * Reads from peer's doc structure:
 * - "identity" → blob hash of IrohIdentity JSON
 * - "feed/latest" → blob hash of latest IrohFeed
 * - "circles/{circleId}" → blob hash of IrohCircle (encrypted)
 */
class ContentDownloader(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase,
    private val cacheDir: File
) {
    companion object {
        private const val TAG = "ContentDownloader"
    }

    private val gson = GsonProvider.gson
    private val blobCache = BlobCache(cacheDir)

    /**
     * Downloads a blob from a peer and logs a warning if not found.
     * @param hash The blob hash to download
     * @param nodeId The peer to download from
     * @param contentType Description for logging
     * @return The blob data, or null if not found.
     */
    private suspend fun downloadBlobOrLog(hash: BlobHash, nodeId: NodeId, contentType: String): ByteArray? {
        return iroh.downloadBlob(hash, nodeId) ?: run {
            Log.w(TAG, "Could not download $contentType blob $hash from $nodeId")
            null
        }
    }

    /**
     * Downloads an encrypted blob from a peer, decrypts it, and deserializes to the specified type.
     * @return The deserialized object, or null if download/decrypt/parse failed.
     */
    private suspend inline fun <reified T> downloadAndDecrypt(hash: BlobHash, nodeId: NodeId, cipher: Encryptor, contentType: String): T? {
        val encrypted = downloadBlobOrLog(hash, nodeId, contentType) ?: return null

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt $contentType $hash: ${e.message}")
            return null
        }

        return try {
            gson.fromJson(String(data), T::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $contentType $hash: ${e.message}")
            null
        }
    }

    /**
     * Sync with all subscribed peers.
     */
    suspend fun syncAll() {
        val peers = db.peerDocDao().subscribedPeers()
        Log.i(TAG, "Syncing with ${peers.size} peers")

        for (peer in peers) {
            try {
                syncPeer(peer)
                db.peerDocDao().updateLastSynced(peer.nodeId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync with ${peer.nodeId}: ${e.message}")
            }
        }
    }

    /**
     * Sync with a specific peer.
     */
    suspend fun syncPeer(peer: PeerDoc) {
        Log.d(TAG, "Syncing with peer ${peer.nodeId}")

        // Use the stored ticket to re-join the doc if available
        // This ensures we have the peer's relay addresses for syncing
        val doc = if (peer.docTicket != null) {
            Log.d(TAG, "Re-joining doc using stored ticket for ${peer.nodeId}")
            iroh.joinDoc(peer.docTicket) ?: run {
                Log.w(TAG, "Could not join doc for ${peer.nodeId}, trying openDoc")
                iroh.openDoc(peer.docNamespaceId)
            }
        } else {
            Log.d(TAG, "No ticket stored, opening doc by namespace for ${peer.nodeId}")
            iroh.openDoc(peer.docNamespaceId)
        }

        if (doc == null) {
            Log.w(TAG, "Could not open doc for ${peer.nodeId}")
            return
        }

        // Trigger sync with peer
        try {
            doc.syncWith(peer.nodeId)
        } catch (e: Exception) {
            Log.w(TAG, "syncWith failed for ${peer.nodeId}: ${e.message}")
        }

        // Wait a moment for sync to propagate
        // Note: This is a workaround until we implement proper sync completion detection
        kotlinx.coroutines.delay(3000)
        Log.d(TAG, "Sync wait complete for peer ${peer.nodeId}")

        // Download identity (not encrypted)
        doc.get(IrohDocKeys.KEY_IDENTITY)?.let { identityHashBytes ->
            val hash = String(identityHashBytes)
            downloadIdentity(hash, peer.nodeId)
        }

        // Download actions meant for us
        downloadActions(doc, peer.nodeId)

        // Log all keys in the doc to understand what's available
        val allKeys = doc.keys()
        Log.d(TAG, "Peer doc has ${allKeys.size} keys: ${allKeys.take(10)}")

        // Download latest feed using cipher from stored keys
        val feedHashBytes = doc.get(IrohDocKeys.KEY_FEED_LATEST)
        Log.d(TAG, "Feed hash bytes: ${feedHashBytes?.let { String(it) } ?: "null"}")

        feedHashBytes?.let {
            val hash = String(feedHashBytes)
            Log.d(TAG, "Found feed at $hash for peer ${peer.nodeId}")

            // Create cipher using any keys we have for this peer
            val cipher = EncryptorFactory.forPeer(db.keyDao(), peer.nodeId) { data ->
                // Validator: check if decrypted data is valid JSON
                try {
                    gson.fromJson(String(data), IrohFeed::class.java)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            downloadFeed(hash, peer.nodeId, cipher)
        }
    }

    /**
     * Download and save an identity.
     */
    private suspend fun downloadIdentity(hash: BlobHash, nodeId: NodeId) {
        // Check if we already have it
        val existing = db.identityDao().findByHash(hash)
        if (existing != null) {
            Log.d(TAG, "Already have identity $hash")
            return
        }

        val data = downloadBlobOrLog(hash, nodeId, "identity") ?: return

        val irohIdentity = try {
            gson.fromJson(String(data), IrohIdentity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse identity $hash: ${e.message}")
            return
        }

        // Create or update identity in DB
        val existingByOwner = db.identityDao().findByOwner(nodeId)
        if (existingByOwner != null) {
            existingByOwner.name = irohIdentity.name
            existingByOwner.profilePicHash = irohIdentity.profilePicHash
            existingByOwner.blobHash = hash
            db.identityDao().update(existingByOwner)
            Log.i(TAG, "Updated identity for $nodeId: ${irohIdentity.name}")
        } else {
            val identity = Identity(
                blobHash = hash,
                owner = nodeId,
                name = irohIdentity.name,
                profilePicHash = irohIdentity.profilePicHash
            )
            db.identityDao().insert(identity)
            Log.i(TAG, "Created new identity for $nodeId: ${irohIdentity.name}")
        }

        // Download profile picture if present
        irohIdentity.profilePicHash?.let { picHash ->
            downloadPublicBlob(picHash, nodeId)
        }
    }

    /**
     * Download a feed and its posts.
     */
    suspend fun downloadFeed(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        val feed = downloadAndDecrypt<IrohFeed>(hash, nodeId, cipher, "feed") ?: return

        Log.d(TAG, "Downloaded feed with ${feed.posts.size} posts")

        // Download each post
        for (postHash in feed.posts) {
            try {
                downloadPost(postHash, nodeId, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download post $postHash: ${e.message}")
            }
        }
    }

    /**
     * Download a post and its files.
     */
    suspend fun downloadPost(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        // Skip if already have it
        if (db.postDao().findByHash(hash) != null) {
            Log.d(TAG, "Already have post $hash")
            return
        }

        val irohPost = downloadAndDecrypt<IrohPost>(hash, nodeId, cipher, "post") ?: return

        // Find or create identity for this author
        val identity = findOrCreateIdentity(nodeId)

        val post = Post(
            authorId = identity.id,
            blobHash = hash,
            timestamp = irohPost.timestamp,
            parentHash = irohPost.parentHash,
            text = irohPost.text,
            signature = irohPost.signature
        )

        val postId = db.postDao().insert(post)

        // Download files
        for (file in irohPost.files) {
            try {
                downloadFile(file.blobHash, nodeId, postId, file.mimeType, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download file ${file.blobHash}: ${e.message}")
            }
        }

        Log.d(TAG, "Downloaded post $hash with ${irohPost.files.size} files")
    }

    /**
     * Download a file and save to cache.
     */
    private suspend fun downloadFile(hash: BlobHash, nodeId: NodeId, postId: Long, mimeType: String, cipher: Encryptor) {
        val encrypted = downloadBlobOrLog(hash, nodeId, "file") ?: return

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt file $hash: ${e.message}")
            return
        }

        // Store in local file cache
        if (!blobCache.store(hash, data)) {
            return
        }

        // Record in DB
        db.postFileDao().insert(PostFile(postId, hash, mimeType))

        Log.d(TAG, "Downloaded file $hash (${data.size} bytes)")
    }

    /**
     * Download a public blob to cache without decryption.
     * Used for public content like profile pictures.
     */
    private suspend fun downloadPublicBlob(hash: BlobHash, nodeId: NodeId) {
        if (blobCache.exists(hash)) {
            Log.d(TAG, "Already have blob $hash in cache")
            return
        }

        val data = downloadBlobOrLog(hash, nodeId, "blob") ?: return
        blobCache.store(hash, data)
    }

    /**
     * Find or create an identity for a node ID.
     */
    private fun findOrCreateIdentity(nodeId: NodeId): Identity {
        val existing = db.identityDao().findByOwner(nodeId)
        if (existing != null) {
            return existing
        }

        // Create a placeholder identity
        val identity = Identity(
            blobHash = null,
            owner = nodeId,
            name = "Unknown",
            profilePicHash = null
        )
        val id = db.identityDao().insert(identity)
        return db.identityDao().find(id)
    }

    /**
     * Download actions meant for us from a peer's doc.
     * Actions are stored at: actions/{ourNodeId}/{timestamp}
     */
    suspend fun downloadActions(doc: IrohDoc, peerNodeId: NodeId) {
        val myNodeId = iroh.nodeId()
        val prefix = "${IrohDocKeys.KEY_ACTIONS_PREFIX}$myNodeId/"

        // Get all keys and filter for actions meant for us
        val allKeys = doc.keys()
        val actionKeys = allKeys.filter { it.startsWith(prefix) }

        if (actionKeys.isEmpty()) {
            return
        }

        Log.d(TAG, "Found ${actionKeys.size} action keys for us from $peerNodeId")

        for (key in actionKeys) {
            try {
                // Get the blob hash stored at this key
                val hashBytes = doc.get(key) ?: continue
                val hash = String(hashBytes)

                // Check if we already have this action
                if (db.actionDao().actionExists(hash)) {
                    Log.d(TAG, "Already have action $hash")
                    continue
                }

                // Download the action blob
                val data = downloadBlobOrLog(hash, peerNodeId, "action") ?: continue

                // Parse the network action
                val networkAction = try {
                    gson.fromJson(String(data), NetworkAction::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse action $hash: ${e.message}")
                    continue
                }

                // Convert to DB action
                val actionType = try {
                    DbActionType.valueOf(networkAction.type)
                } catch (e: Exception) {
                    Log.w(TAG, "Unknown action type: ${networkAction.type}")
                    continue
                }

                val action = Action(
                    timestamp = networkAction.timestamp,
                    blobHash = hash,
                    fromPeerId = peerNodeId,  // The peer who sent us this action
                    toPeerId = myNodeId,       // It's meant for us
                    actionType = actionType,
                    data = networkAction.data,
                    processed = false  // We need to process this
                )

                db.actionDao().insert(action)
                Log.d(TAG, "Downloaded action $hash from $peerNodeId: ${action.actionType}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download action from key $key: ${e.message}")
            }
        }
    }

    /**
     * Process all unprocessed actions.
     * For UpdateKey actions, stores the received key for future use.
     */
    fun processActions() {
        val pending = db.actionDao().inNeedOfProcessing()
        if (pending.isEmpty()) {
            return
        }

        Log.i(TAG, "Processing ${pending.size} pending actions")

        for (action in pending) {
            try {
                when (action.actionType) {
                    DbActionType.UpdateKey -> processUpdateKeyAction(action)
                    DbActionType.Delete -> Log.d(TAG, "Delete action not yet implemented")
                    DbActionType.Introduce -> Log.d(TAG, "Introduce action not yet implemented")
                }
                db.actionDao().markProcessed(action.id)
                Log.d(TAG, "Processed action ${action.id}: ${action.actionType}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process action ${action.id}: ${e.message}")
            }
        }
    }

    /**
     * Process an UpdateKey action.
     * Stores the received AES key for the sender, allowing us to decrypt their content.
     */
    private fun processUpdateKeyAction(action: Action) {
        val fromPeerId = action.fromPeerId
        if (fromPeerId == null) {
            Log.w(TAG, "UpdateKey action has no fromPeerId, cannot store key")
            return
        }

        // The data is a Base64-encoded AES key
        KeyStorage.storeAesKey(db.keyDao(), fromPeerId, action.data)
        Log.i(TAG, "Stored key from $fromPeerId")
    }

    /**
     * Data class for deserializing actions from Iroh.
     * Matches the format used by ContentPublisher.
     */
    private data class NetworkAction(
        val type: String,
        val data: String,
        val timestamp: Long
    )
}
