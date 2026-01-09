package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.db.ActionType as DbActionType
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohDocKeys
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider

/**
 * Publishes local content to Iroh.
 *
 * Content structure in my Doc:
 * - "identity" → blob hash of IrohIdentity JSON
 * - "feed/latest" → blob hash of latest IrohFeed
 * - "circles/{circleId}" → blob hash of IrohCircle (encrypted)
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
     * Publish identity to my doc.
     * Identity is public (not encrypted).
     */
    suspend fun publishIdentity(identity: Identity): BlobHash {
        val irohIdentity = IrohIdentity(
            name = identity.name,
            profilePicHash = identity.profilePicHash
        )

        val json = gson.toJson(irohIdentity)
        val hash = iroh.storeBlob(json.toByteArray())

        // Update my doc to point to the new identity
        iroh.getMyDoc().set(IrohDocKeys.KEY_IDENTITY, hash.toByteArray())

        // Update identity with its blob hash
        identity.blobHash = hash
        db.identityDao().updateHash(identity.id, hash)

        Log.i(TAG, "Published identity: $hash")
        return hash
    }

    /**
     * Publish a post (encrypted).
     * Returns the blob hash of the encrypted post.
     */
    suspend fun publishPost(post: Post, cipher: Encryptor): BlobHash {
        // Get files for this post
        val files = db.postFileDao().filesForPost(post.id)

        val irohPost = IrohPost(
            timestamp = post.timestamp,
            parentHash = post.parentHash,
            text = post.text,
            files = files.map { IrohFileDef(it.mimeType, it.blobHash) },
            signature = post.signature
        )

        val hash = storeEncrypted(irohPost, cipher)

        // Update post with its hash
        db.postDao().updateHash(post.id, hash)

        Log.i(TAG, "Published post: $hash")
        return hash
    }

    /**
     * Publish a file blob (encrypted).
     * Returns the blob hash of the encrypted file.
     */
    suspend fun publishFile(data: ByteArray, cipher: Encryptor): BlobHash {
        val encrypted = cipher.encrypt(data)
        val hash = iroh.storeBlob(encrypted)
        Log.d(TAG, "Published file: $hash (${data.size} bytes)")
        return hash
    }

    /**
     * Publish a feed snapshot for a circle.
     * This is the list of all post hashes in the circle.
     */
    suspend fun publishFeed(circle: Circle, cipher: Encryptor): BlobHash {
        // Get all posts in this circle
        val postIds = db.circlePostDao().postsIn(circle.id)
        val posts = postIds.mapNotNull { db.postDao().find(it).blobHash }

        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = posts
        )

        val hash = storeEncrypted(feed, cipher)

        // Store in my doc under circle path
        iroh.getMyDoc().set(IrohDocKeys.circleKey(circle.id), hash.toByteArray())

        // Also update latest feed pointer
        iroh.getMyDoc().set(IrohDocKeys.KEY_FEED_LATEST, hash.toByteArray())

        // Update circle with its hash
        db.circleDao().storeHash(circle.id, hash)

        Log.i(TAG, "Published feed for circle ${circle.id}: $hash with ${posts.size} posts")
        return hash
    }

    /**
     * Publish all unpublished content.
     * Call this during sync to push local changes.
     */
    suspend fun publishPending(cipher: Encryptor) {
        // Publish unpublished posts
        val pendingPosts = db.postDao().inNeedOfSync()
        for (post in pendingPosts) {
            try {
                publishPost(post, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish post ${post.id}: ${e.message}")
            }
        }

        // Update feeds for circles with new posts
        val circles = db.circleDao().all()
        for (circle in circles) {
            try {
                publishFeed(circle, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish feed for circle ${circle.id}: ${e.message}")
            }
        }

        // Publish unpublished actions
        publishActions()

        Log.i(TAG, "Published ${pendingPosts.size} pending posts")
    }

    /**
     * Publish all unpublished actions to Iroh doc.
     * Actions are stored at: actions/{targetNodeId}/{timestamp}
     * so recipients can find all actions meant for them.
     */
    suspend fun publishActions() {
        val pendingActions = db.actionDao().inNeedOfSync()
        if (pendingActions.isEmpty()) {
            return
        }

        val myDoc = iroh.getMyDoc()

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

                // Store in doc at actions/{targetNodeId}/{timestamp}
                val key = IrohDocKeys.actionKey(action.toPeerId, action.timestamp)
                myDoc.set(key, hash.toByteArray())

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
