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
        
        // After syncing with all peers, retry downloading any missing files
        // for recent posts (< 30 days old)
        try {
            retryMissingFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retry missing files: ${e.message}")
        }
    }

    /**
     * Sync with a specific peer.
     */
    suspend fun syncPeer(peer: PeerDoc) {
        Log.d(TAG, "Syncing with peer ${peer.nodeId}")

        // Try to open existing doc, or join if we don't have it
        val doc = iroh.openDoc(peer.docNamespaceId) ?: if (peer.docTicket != null) {
            Log.d(TAG, "Joining doc using ticket for ${peer.nodeId}")
            iroh.joinDoc(peer.docTicket)
        } else {
            null
        }

        if (doc != null) {
            // Sync with the peer to get latest updates
            Log.d(TAG, "Syncing doc with peer ${peer.nodeId}")
            val syncSuccess = doc.syncWithAndWait(peer.nodeId, timeoutMs = 30000)
            Log.d(TAG, "Sync complete for ${peer.nodeId} (success=$syncSuccess)")
        }

        if (doc == null) {
            Log.w(TAG, "Could not open doc for ${peer.nodeId}")
            return
        }

        // Download identity (not encrypted)
        doc.get(IrohDocKeys.KEY_IDENTITY)?.let { identityHashBytes ->
            val hash = String(identityHashBytes)
            downloadIdentity(hash, peer.nodeId)
        }

        // Download actions meant for us
        downloadActions(doc, peer.nodeId)

        // Process downloaded actions immediately - this extracts encryption keys
        // from UpdateKey actions so we can decrypt content below
        processActions()

        // Log all keys in the doc to understand what's available
        val docNamespace = doc.namespaceId()
        val allKeys = doc.keys()
        Log.d(TAG, "Peer doc $docNamespace has ${allKeys.size} keys: ${allKeys.take(10)}")

        // NEW APPROACH: Download posts directly from posts/* entries
        // This is the reliable sync method for Iroh Docs
        downloadPostsFromKeys(doc, peer.nodeId)

        // Debug: Check all entries for feed/latest to see if there are multiple authors
        val allFeedEntries = doc.getAllEntriesForKey(IrohDocKeys.KEY_FEED_LATEST)
        Log.d(TAG, "All entries for feed/latest (${allFeedEntries.size}): ${allFeedEntries.map { "${it.first.take(8)}...=${it.second.take(16)}..." }}")

        // DEPRECATED: Also try old feed/latest approach for backward compatibility
        // Remove this block after all devices are updated
        val feedHashBytes = doc.get(IrohDocKeys.KEY_FEED_LATEST)
        feedHashBytes?.let {
            val hash = String(feedHashBytes)
            Log.d(TAG, "Found feed at $hash for peer ${peer.nodeId} (legacy)")

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
     * Download posts directly from doc entries.
     * This is the new approach that works reliably with Iroh Docs.
     * Posts are stored at posts/{circleId}/{timestamp} entries.
     */
    suspend fun downloadPostsFromKeys(doc: IrohDoc, nodeId: NodeId) {
        val postKeys = doc.keysWithPrefix(IrohDocKeys.KEY_POSTS_PREFIX)
        Log.d(TAG, "Found ${postKeys.size} post keys in peer doc")

        for (key in postKeys) {
            try {
                // Parse key: "posts/{circleId}/{timestamp}"
                val parts = key.split("/")
                if (parts.size != 3) {
                    Log.w(TAG, "Invalid post key format: $key")
                    continue
                }

                val circleId = parts[1].toLongOrNull()
                if (circleId == null) {
                    Log.w(TAG, "Could not parse circle ID from key: $key")
                    continue
                }

                // Get hash from doc
                val hashBytes = doc.get(key) ?: continue
                val hash = String(hashBytes)

                // Skip if already have it
                if (db.postDao().findByHash(hash) != null) {
                    Log.d(TAG, "Already have post $hash")
                    continue
                }

                // Get cipher for this peer (tries all available keys)
                val cipher = EncryptorFactory.forPeer(db.keyDao(), nodeId) { data ->
                    try {
                        gson.fromJson(String(data), IrohPost::class.java)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                // Download and process
                downloadPost(hash, nodeId, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download post from key $key: ${e.message}")
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
        val fileCount = irohPost.files.size
        if (fileCount > 0) {
            Log.i(TAG, "POST $hash has $fileCount files to download")
            
            // Create a file-specific cipher with binary validator.
            // The post cipher uses a JSON validator which fails for binary file content.
            // Files are binary data, so we just validate that decryption produces non-empty output.
            val fileCipher = EncryptorFactory.forPeer(db.keyDao(), nodeId) { it.isNotEmpty() }
            
            var successCount = 0
            var failCount = 0
            for ((index, file) in irohPost.files.withIndex()) {
                try {
                    Log.d(TAG, "Downloading file ${index + 1}/$fileCount: ${file.blobHash}")
                    val success = downloadFile(file.blobHash, nodeId, postId, file.mimeType, fileCipher)
                    if (success) successCount++ else failCount++
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "Exception downloading file ${file.blobHash}: ${e.message}")
                }
            }
            
            Log.i(TAG, "POST $hash files: $successCount success, $failCount failed out of $fileCount total")
        }
        Log.d(TAG, "Downloaded post $hash with ${irohPost.files.size} files")
    }

    /**
     * Download a file and save to cache.
     * @return true if file was downloaded successfully, false otherwise
     */
    private suspend fun downloadFile(hash: BlobHash, nodeId: NodeId, postId: Long, mimeType: String, cipher: Encryptor): Boolean {
        Log.d(TAG, "Attempting to download file $hash from $nodeId")
        
        val encrypted = downloadBlobOrLog(hash, nodeId, "file")
        if (encrypted == null) {
            Log.w(TAG, "FILE DOWNLOAD FAILED: Could not get blob $hash from peer $nodeId - blob may have been garbage collected")
            return false
        }

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "FILE DECRYPT FAILED: $hash - ${e.message}")
            return false
        }

        // Safeguard: reject empty decryption results (indicates cipher validation failed)
        if (data.isEmpty()) {
            Log.e(TAG, "FILE DECRYPT EMPTY: $hash - decryption produced empty result, likely wrong key")
            return false
        }

        // Store in local file cache
        if (!blobCache.store(hash, data)) {
            Log.e(TAG, "FILE CACHE FAILED: Could not store $hash in local cache")
            return false
        }

        // Record in DB
        db.postFileDao().insert(PostFile(postId, hash, mimeType))

        Log.i(TAG, "FILE DOWNLOAD SUCCESS: $hash (${data.size} bytes, $mimeType)")
        return true
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
     * Retry downloading missing files for recent posts.
     * Only retries posts from the last 30 days to avoid wasting bandwidth on old content
     * that may no longer be available.
     */
    suspend fun retryMissingFiles() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val postsWithFiles = db.postDao().postsWithFilesSince(thirtyDaysAgo)
        
        if (postsWithFiles.isEmpty()) {
            return
        }
        
        Log.d(TAG, "Checking ${postsWithFiles.size} recent posts for missing files")
        
        var totalMissing = 0
        var totalRetried = 0
        var totalSuccess = 0
        
        for (post in postsWithFiles) {
            val files = db.postFileDao().filesForPost(post.id)
            val missingFiles = files.filter { !blobCache.exists(it.blobHash) }
            
            if (missingFiles.isEmpty()) {
                continue
            }
            
            totalMissing += missingFiles.size
            
            // Get the author's identity to find their nodeId
            val author = try {
                db.identityDao().find(post.authorId)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot retry files for post ${post.id}: author ${post.authorId} not found")
                continue
            }
            
            // Create cipher for decryption
            val cipher = EncryptorFactory.forPeer(db.keyDao(), author.owner) { true }
            
            Log.i(TAG, "RETRY: Post ${post.id} has ${missingFiles.size} missing files, attempting download from ${author.owner}")
            
            for (file in missingFiles) {
                totalRetried++
                try {
                    val success = downloadFile(file.blobHash, author.owner, post.id, file.mimeType, cipher)
                    if (success) {
                        totalSuccess++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "RETRY FAILED: ${file.blobHash} - ${e.message}")
                }
            }
        }
        
        if (totalMissing > 0) {
            Log.i(TAG, "RETRY SUMMARY: $totalSuccess/$totalRetried files recovered ($totalMissing total missing)")
        }
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
