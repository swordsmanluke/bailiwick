package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider

/**
 * Stores local content as Iroh blobs.
 *
 * This class handles serialization and encryption of content for storage.
 * Content is stored as blobs and tracked in the local database.
 * Publishing to the network (via Gossip) is handled separately by GossipService.
 */
class ContentPublisher(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase
) {
    companion object {
        private const val TAG = "ContentPublisher"
    }

    private val gson = GsonProvider.gson

    /**
     * Serializes an object to JSON, encrypts it, and stores as a blob.
     * @return The blob hash of the encrypted content.
     */
    private suspend fun <T> storeEncrypted(obj: T, cipher: Encryptor): BlobHash {
        val json = gson.toJson(obj)
        val encrypted = cipher.encrypt(json.toByteArray())
        return iroh.storeBlob(encrypted)
    }

    /**
     * Store identity as a blob and update database.
     * Identity is public (not encrypted).
     * @return The blob hash of the identity.
     */
    suspend fun publishIdentity(identity: Identity): BlobHash {
        val irohIdentity = IrohIdentity(
            name = identity.name,
            profilePicHash = identity.profilePicHash
        )

        val json = gson.toJson(irohIdentity)
        val hash = iroh.storeBlob(json.toByteArray())

        // Update identity with its blob hash
        identity.blobHash = hash
        db.identityDao().updateHash(identity.id, hash)

        Log.i(TAG, "Published identity: $hash")
        return hash
    }

    /**
     * Store a post as an encrypted blob.
     * @return The blob hash of the encrypted post.
     */
    suspend fun publishPost(post: Post, circleId: Long, cipher: Encryptor): BlobHash {
        // Log cipher type for debugging key issues
        Log.i(TAG, "publishPost: using cipher ${cipher.javaClass.simpleName} for circle $circleId")

        // Get files for this post
        val files = db.postFileDao().filesForPost(post.id)

        val timestamp = post.timestamp ?: System.currentTimeMillis()

        // Encrypt and publish each file, mapping original hash to encrypted hash
        val encryptedFiles = mutableListOf<IrohFileDef>()
        for (file in files) {
            // Get the unencrypted file content
            val fileData = iroh.getBlob(file.blobHash)
            if (fileData == null) {
                Log.w(TAG, "Could not find file blob ${file.blobHash} for post ${post.id}")
                continue
            }

            // Encrypt and store with SAME cipher as post
            Log.d(TAG, "Encrypting file ${file.blobHash} (${fileData.size} bytes) with same cipher as post")
            val encryptedHash = publishFile(fileData, cipher)
            encryptedFiles.add(IrohFileDef(file.mimeType, encryptedHash))
            Log.d(TAG, "Encrypted file ${file.blobHash} -> $encryptedHash")
        }
        
        val irohPost = IrohPost(
            timestamp = timestamp,
            parentHash = post.parentHash,
            text = post.text,
            files = encryptedFiles,
            signature = post.signature
        )

        val hash = storeEncrypted(irohPost, cipher)
        
        // Update post with its hash
        db.postDao().updateHash(post.id, hash)

        Log.i(TAG, "Published post: $hash with ${encryptedFiles.size} files")
        return hash
    }

    /**
     * Store a file blob (encrypted).
     * @return The blob hash of the encrypted file.
     */
    suspend fun publishFile(data: ByteArray, cipher: Encryptor): BlobHash {
        val encrypted = cipher.encrypt(data)
        val hash = iroh.storeBlob(encrypted)
        Log.d(TAG, "Published file: $hash (${data.size} bytes)")
        return hash
    }

    /**
     * Store a reaction as an encrypted blob.
     * @param isRemoval If true, this publishes a reaction removal notification.
     * @return The blob hash of the encrypted reaction.
     */
    suspend fun publishReaction(reaction: Reaction, cipher: Encryptor, isRemoval: Boolean = false): BlobHash {
        val irohReaction = IrohReaction(
            postHash = reaction.postHash,
            authorNodeId = reaction.authorNodeId,
            emoji = reaction.emoji,
            timestamp = reaction.timestamp,
            signature = reaction.signature,
            isRemoval = isRemoval
        )

        val hash = storeEncrypted(irohReaction, cipher)

        // Update reaction with its hash (only for non-removals)
        if (!isRemoval) {
            db.reactionDao().updateHash(reaction.id, hash)
        }

        Log.i(TAG, "Published reaction: $hash (${reaction.emoji} on ${reaction.postHash}, removal=$isRemoval)")
        return hash
    }

    /**
     * Store all unpublished content as blobs.
     * Call this to prepare local changes for sync.
     */
    suspend fun publishPending(cipher: Encryptor) {
        // Publish unpublished posts
        val pendingPosts = db.postDao().inNeedOfSync()
        for (post in pendingPosts) {
            try {
                // Get all circles this post belongs to
                val circleIds = db.circlePostDao().circlesForPost(post.id)
                for (circleId in circleIds) {
                    publishPost(post, circleId, cipher)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish post ${post.id}: ${e.message}")
            }
        }

        // Publish unpublished reactions
        val pendingReactions = db.reactionDao().inNeedOfSync()
        for (reaction in pendingReactions) {
            try {
                publishReaction(reaction, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish reaction ${reaction.id}: ${e.message}")
            }
        }

        // Publish unpublished actions
        publishActions()

        Log.i(TAG, "Published ${pendingPosts.size} pending posts, ${pendingReactions.size} pending reactions")
    }

    /**
     * Store unpublished actions as blobs.
     * Actions are included in the manifest for recipients to discover.
     */
    suspend fun publishActions() {
        val pendingActions = db.actionDao().inNeedOfSync()
        if (pendingActions.isEmpty()) {
            return
        }

        for (action in pendingActions) {
            try {
                // Create serializable action for network
                val networkAction = NetworkAction(
                    type = action.actionType.name,
                    data = action.data,
                    timestamp = action.timestamp
                )

                // Store as blob
                val json = gson.toJson(networkAction)
                val hash = iroh.storeBlob(json.toByteArray())

                // Update local record with blob hash
                db.actionDao().updateHash(action.id, hash)

                Log.d(TAG, "Published action ${action.id} to ${action.toPeerId}: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish action ${action.id}: ${e.message}")
            }
        }

        Log.i(TAG, "Published ${pendingActions.size} pending actions")
    }

    /**
     * Data class for serializing actions to Iroh.
     * Simple structure that can be deserialized by recipient.
     */
    private data class NetworkAction(
        val type: String,
        val data: String,
        val timestamp: Long
    )
}
