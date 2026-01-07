package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
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
        private const val KEY_IDENTITY = "identity"
        private const val KEY_FEED_LATEST = "feed/latest"
    }

    private val gson = Gson()

    /**
     * Sync with all subscribed peers.
     */
    fun syncAll() {
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
    fun syncPeer(peer: PeerDoc) {
        Log.i(TAG, "Syncing with peer ${peer.nodeId}")

        val doc = iroh.openDoc(peer.docNamespaceId) ?: run {
            Log.w(TAG, "Could not open doc for ${peer.nodeId}")
            return
        }

        // Trigger sync
        doc.syncWith(peer.nodeId)

        // Download identity (not encrypted)
        doc.get(KEY_IDENTITY)?.let { identityHashBytes ->
            val hash = String(identityHashBytes)
            downloadIdentity(hash, peer.nodeId)
        }

        // Download latest feed (need cipher for decryption)
        // Note: In a real implementation, we'd get the cipher from the circle membership
        // For now, we just log that we found a feed
        doc.get(KEY_FEED_LATEST)?.let { feedHashBytes ->
            val hash = String(feedHashBytes)
            Log.i(TAG, "Found feed at $hash for peer ${peer.nodeId}")
            // downloadFeed(hash, peer.nodeId, cipher)
        }
    }

    /**
     * Download and save an identity.
     */
    private fun downloadIdentity(hash: BlobHash, nodeId: NodeId) {
        // Check if we already have it
        val existing = db.identityDao().findByHash(hash)
        if (existing != null) {
            Log.d(TAG, "Already have identity $hash")
            return
        }

        val data = iroh.getBlob(hash) ?: run {
            Log.w(TAG, "Could not download identity blob $hash")
            return
        }

        val irohIdentity = try {
            gson.fromJson(String(data), IrohIdentity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse identity $hash: ${e.message}")
            return
        }

        // Create or update identity in DB
        val existingByOwner = db.identityDao().identitiesFor(nodeId).firstOrNull()
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
            downloadBlob(picHash)
        }
    }

    /**
     * Download a feed and its posts.
     */
    fun downloadFeed(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        val encrypted = iroh.getBlob(hash) ?: run {
            Log.w(TAG, "Could not download feed blob $hash")
            return
        }

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt feed $hash: ${e.message}")
            return
        }

        val feed = try {
            gson.fromJson(String(data), IrohFeed::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse feed $hash: ${e.message}")
            return
        }

        Log.i(TAG, "Downloaded feed with ${feed.posts.size} posts")

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
    fun downloadPost(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        // Skip if already have it
        if (db.postDao().findByHash(hash) != null) {
            Log.d(TAG, "Already have post $hash")
            return
        }

        val encrypted = iroh.getBlob(hash) ?: run {
            Log.w(TAG, "Could not download post blob $hash")
            return
        }

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt post $hash: ${e.message}")
            return
        }

        val irohPost = try {
            gson.fromJson(String(data), IrohPost::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse post $hash: ${e.message}")
            return
        }

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
                downloadFile(file.blobHash, postId, file.mimeType, cipher)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download file ${file.blobHash}: ${e.message}")
            }
        }

        Log.i(TAG, "Downloaded post $hash with ${irohPost.files.size} files")
    }

    /**
     * Download a file and save to cache.
     */
    private fun downloadFile(hash: BlobHash, postId: Long, mimeType: String, cipher: Encryptor) {
        val encrypted = iroh.getBlob(hash) ?: run {
            Log.w(TAG, "Could not download file blob $hash")
            return
        }

        val data = try {
            cipher.decrypt(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt file $hash: ${e.message}")
            return
        }

        // Store in local file cache
        val cacheFile = File(cacheDir, hash)
        cacheFile.writeBytes(data)

        // Record in DB
        db.postFileDao().insert(PostFile(postId, hash, mimeType))

        Log.d(TAG, "Downloaded file $hash (${data.size} bytes)")
    }

    /**
     * Download a blob to cache without decryption.
     * Used for public content like profile pictures.
     */
    private fun downloadBlob(hash: BlobHash) {
        if (File(cacheDir, hash).exists()) {
            Log.d(TAG, "Already have blob $hash in cache")
            return
        }

        val data = iroh.getBlob(hash) ?: run {
            Log.w(TAG, "Could not download blob $hash")
            return
        }

        val cacheFile = File(cacheDir, hash)
        cacheFile.writeBytes(data)

        Log.d(TAG, "Downloaded blob $hash (${data.size} bytes)")
    }

    /**
     * Find or create an identity for a node ID.
     */
    private fun findOrCreateIdentity(nodeId: NodeId): Identity {
        val existing = db.identityDao().identitiesFor(nodeId).firstOrNull()
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
}
