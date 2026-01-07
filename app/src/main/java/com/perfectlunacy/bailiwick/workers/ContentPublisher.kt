package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

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
        private const val KEY_IDENTITY = "identity"
        private const val KEY_FEED_LATEST = "feed/latest"
        private const val KEY_CIRCLE_PREFIX = "circles/"
    }

    private val gson = Gson()

    /**
     * Publish identity to my doc.
     * Identity is public (not encrypted).
     */
    fun publishIdentity(identity: Identity): BlobHash {
        val irohIdentity = IrohIdentity(
            name = identity.name,
            profilePicHash = identity.profilePicHash
        )

        val json = gson.toJson(irohIdentity)
        val hash = iroh.storeBlob(json.toByteArray())

        // Update my doc to point to the new identity
        iroh.getMyDoc().set(KEY_IDENTITY, hash.toByteArray())

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
    fun publishPost(post: Post, cipher: Encryptor): BlobHash {
        // Get files for this post
        val files = db.postFileDao().filesForPost(post.id)

        val irohPost = IrohPost(
            timestamp = post.timestamp,
            parentHash = post.parentHash,
            text = post.text,
            files = files.map { IrohFileDef(it.mimeType, it.blobHash) },
            signature = post.signature
        )

        val json = gson.toJson(irohPost)
        val encrypted = cipher.encrypt(json.toByteArray())
        val hash = iroh.storeBlob(encrypted)

        // Update post with its hash
        db.postDao().updateHash(post.id, hash)

        Log.i(TAG, "Published post: $hash")
        return hash
    }

    /**
     * Publish a file blob (encrypted).
     * Returns the blob hash of the encrypted file.
     */
    fun publishFile(data: ByteArray, cipher: Encryptor): BlobHash {
        val encrypted = cipher.encrypt(data)
        val hash = iroh.storeBlob(encrypted)
        Log.d(TAG, "Published file: $hash (${data.size} bytes)")
        return hash
    }

    /**
     * Publish a feed snapshot for a circle.
     * This is the list of all post hashes in the circle.
     */
    fun publishFeed(circle: Circle, cipher: Encryptor): BlobHash {
        // Get all posts in this circle
        val postIds = db.circlePostDao().postsIn(circle.id)
        val posts = postIds.mapNotNull { db.postDao().find(it).blobHash }

        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = posts
        )

        val json = gson.toJson(feed)
        val encrypted = cipher.encrypt(json.toByteArray())
        val hash = iroh.storeBlob(encrypted)

        // Store in my doc under circle path
        iroh.getMyDoc().set("$KEY_CIRCLE_PREFIX${circle.id}", hash.toByteArray())

        // Also update latest feed pointer
        iroh.getMyDoc().set(KEY_FEED_LATEST, hash.toByteArray())

        // Update circle with its hash
        db.circleDao().storeHash(circle.id, hash)

        Log.i(TAG, "Published feed for circle ${circle.id}: $hash with ${posts.size} posts")
        return hash
    }

    /**
     * Publish all unpublished content.
     * Call this during sync to push local changes.
     */
    fun publishPending(cipher: Encryptor) {
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

        Log.i(TAG, "Published ${pendingPosts.size} pending posts")
    }
}
