package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.db.ActionType as DbActionType
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.BlobCache
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
import com.perfectlunacy.bailiwick.crypto.KeyStorage
import com.perfectlunacy.bailiwick.util.GsonProvider
import java.io.File

/**
 * Downloads content from Iroh blobs.
 *
 * This class handles downloading, decrypting, and storing content.
 * It provides utility methods for downloading various content types.
 * Sync coordination is handled separately by GossipService.
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
     * Download and save an identity.
     */
    suspend fun downloadIdentity(hash: BlobHash, nodeId: NodeId) {
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
    suspend fun downloadFile(hash: BlobHash, nodeId: NodeId, postId: Long, mimeType: String, cipher: Encryptor): Boolean {
        Log.d(TAG, "Attempting to download file $hash from $nodeId")
        
        val encrypted = downloadBlobOrLog(hash, nodeId, "file")
        if (encrypted == null) {
            Log.w(TAG, "FILE DOWNLOAD FAILED: Could not get blob $hash from peer $nodeId")
            return false
        }

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "FILE DECRYPT FAILED: $hash - ${e.message}")
            return false
        }

        // Safeguard: reject empty decryption results
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
    suspend fun downloadPublicBlob(hash: BlobHash, nodeId: NodeId) {
        if (blobCache.exists(hash)) {
            Log.d(TAG, "Already have blob $hash in cache")
            return
        }

        val data = downloadBlobOrLog(hash, nodeId, "blob") ?: return
        blobCache.store(hash, data)
    }

    /**
     * Download a reaction and store it.
     */
    suspend fun downloadReaction(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        // Skip if already have it
        if (db.reactionDao().reactionExists(hash)) {
            Log.d(TAG, "Already have reaction $hash")
            return
        }

        val irohReaction = downloadAndDecrypt<IrohReaction>(hash, nodeId, cipher, "reaction") ?: return

        if (irohReaction.isRemoval) {
            // Handle reaction removal
            db.reactionDao().deleteReaction(
                irohReaction.postHash,
                irohReaction.authorNodeId,
                irohReaction.emoji
            )
            Log.i(TAG, "Removed reaction ${irohReaction.emoji} on ${irohReaction.postHash}")
        } else {
            // Store the reaction
            val reaction = Reaction(
                postHash = irohReaction.postHash,
                authorNodeId = irohReaction.authorNodeId,
                emoji = irohReaction.emoji,
                timestamp = irohReaction.timestamp,
                signature = irohReaction.signature,
                blobHash = hash
            )
            db.reactionDao().insert(reaction)
            Log.i(TAG, "Downloaded reaction ${irohReaction.emoji} on ${irohReaction.postHash}")
        }
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
     * Download an action blob.
     * @return The parsed action, or null if download/parse failed.
     */
    suspend fun downloadAction(hash: BlobHash, nodeId: NodeId): NetworkAction? {
        val data = downloadBlobOrLog(hash, nodeId, "action") ?: return null
        return try {
            gson.fromJson(String(data), NetworkAction::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse action $hash: ${e.message}")
            null
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
                    DbActionType.Delete -> processDeleteAction(action)
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
     * Process a Delete action.
     * Removes the post with the specified blob hash from the local database.
     * Also removes associated files from the cache.
     */
    private fun processDeleteAction(action: Action) {
        val postBlobHash = action.data
        if (postBlobHash.isBlank()) {
            Log.w(TAG, "Delete action has empty post hash")
            return
        }

        val post = db.postDao().findByHash(postBlobHash)
        if (post == null) {
            Log.d(TAG, "Post with hash $postBlobHash not found locally, nothing to delete")
            return
        }

        // Delete associated files from cache
        val files = db.postFileDao().filesForPost(post.id)
        for (file in files) {
            try {
                blobCache.delete(file.blobHash)
                Log.d(TAG, "Deleted cached file: ${file.blobHash}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete cached file ${file.blobHash}: ${e.message}")
            }
        }

        // Delete post files from database
        db.postFileDao().deleteForPost(post.id)

        // Delete the post itself
        db.postDao().delete(post.id)

        Log.i(TAG, "Deleted post ${post.id} (hash: $postBlobHash) from peer ${action.fromPeerId}")
    }

    /**
     * Retry downloading missing files for recent posts.
     * Only retries posts from the last 30 days.
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
    data class NetworkAction(
        val type: String,
        val data: String,
        val timestamp: Long
    )
}
