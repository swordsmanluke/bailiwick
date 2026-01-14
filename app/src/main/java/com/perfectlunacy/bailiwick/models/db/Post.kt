package com.perfectlunacy.bailiwick.models.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.perfectlunacy.bailiwick.signatures.Signer
import com.perfectlunacy.bailiwick.storage.BlobHash
import java.util.*

/**
 * A social post entity.
 *
 * **Equality Note:** Posts are compared by [signature] only, not by all fields.
 * This is intentional because the signature cryptographically identifies the post content.
 * Two posts with the same signature represent the same content, regardless of database ID.
 *
 * **Important:** Posts must be signed via [sign] before being used in Sets or as Map keys,
 * otherwise all unsigned posts (with empty signature) would be considered equal.
 */
@Entity
data class Post(
    val authorId: Long,
    val blobHash: BlobHash?,       // Iroh blob hash (was: cid)
    val timestamp: Long,
    val parentHash: BlobHash?,     // Parent post hash for threading (was: parent)
    val text: String,
    var signature: String
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    /**
     * Sign this post using the provided signer.
     * The signature covers: timestamp, parentHash, text, and file hashes.
     * Must be called before using this post in collections (Set, Map keys).
     */
    fun sign(signer: Signer, files: List<PostFile>) {
        signature = Base64.getEncoder().encodeToString(signer.sign(signingBytes(files)))
    }

    private fun signingBytes(files: List<PostFile>): ByteArray {
        val filenames = files.map { it.blobHash }.sorted().joinToString()
        return "$timestamp:$parentHash:$text:$filenames".toByteArray()
    }

    /**
     * Hash code based on signature only.
     * See class documentation for rationale.
     */
    override fun hashCode(): Int {
        return signature.hashCode()
    }

    /**
     * Equality based on signature only.
     * See class documentation for rationale.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Post

        if (signature != other.signature) return false

        return true
    }
}

@Dao
interface PostDao {
    @Query("SELECT * FROM post ORDER BY timestamp DESC")
    fun all(): List<Post>

    @Query("SELECT * FROM post ORDER BY timestamp DESC")
    fun allLive(): LiveData<List<Post>>

    @Query("SELECT * FROM post WHERE blobHash IS NULL")
    fun inNeedOfSync(): List<Post>

    @Query("SELECT * FROM post WHERE id = :id LIMIT 1")
    fun find(id: Long): Post

    @Query("SELECT * FROM post WHERE blobHash = :hash")
    fun findByHash(hash: BlobHash): Post?

    @Query("SELECT * FROM post WHERE authorId = :authorId")
    fun postsFor(authorId: Long): List<Post>

    @Query("SELECT * FROM post WHERE authorId IN (:authorIds)")
    fun postsFor(authorIds: List<Long>): List<Post>

    @Query("SELECT * FROM post WHERE id IN (:ids)")
    fun findAll(ids: List<Long>): List<Post>

    @Query("SELECT EXISTS( SELECT 1 FROM post WHERE blobHash = :hash)")
    fun postExists(hash: BlobHash): Boolean

    @Query("UPDATE post SET blobHash = :hash WHERE id = :id")
    fun updateHash(id: Long, hash: BlobHash)

    @Query("SELECT * FROM post WHERE parentHash = :parentHash ORDER BY timestamp ASC")
    fun replies(parentHash: BlobHash): List<Post>

    @Query("SELECT COUNT(*) FROM post WHERE parentHash = :parentHash")
    fun replyCount(parentHash: BlobHash): Int


    /**
     * Get posts from the last N days that have associated files.
     * Used for retrying failed file downloads.
     */
    @Query("""
        SELECT DISTINCT p.* FROM post p 
        INNER JOIN postfile pf ON p.id = pf.postId 
        WHERE p.timestamp > :minTimestamp 
        ORDER BY p.timestamp DESC
    """)
    fun postsWithFilesSince(minTimestamp: Long): List<Post>

    @Insert
    fun insert(post: Post): Long

    @Update
    fun update(post: Post)
}
