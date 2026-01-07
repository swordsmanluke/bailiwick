package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.signatures.Signer
import com.perfectlunacy.bailiwick.storage.BlobHash
import java.sql.Time
import java.text.DateFormat
import java.time.Instant
import java.util.*

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

    val timeStr: String
        get() {
            val time = Time.from(Instant.ofEpochMilli(timestamp))
            val formatter = DateFormat.getDateTimeInstance()

            return formatter.format(time)
        }

    fun sign(signer: Signer, files: List<PostFile>) {
        signature = Base64.getEncoder().encodeToString(signer.sign(signingBytes(files)))
    }

    private fun signingBytes(files: List<PostFile>): ByteArray {
        val filenames = files.map { it.blobHash }.sorted().joinToString()
        return "$timestamp:$parentHash:$text:$filenames".toByteArray()
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }

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

    @Query("SELECT EXISTS( SELECT 1 FROM post WHERE blobHash = :hash)")
    fun postExists(hash: BlobHash): Boolean

    @Query("UPDATE post SET blobHash = :hash WHERE id = :id")
    fun updateHash(id: Long, hash: BlobHash)

    @Query("SELECT * FROM post WHERE parentHash = :parentHash ORDER BY timestamp ASC")
    fun replies(parentHash: BlobHash): List<Post>

    @Query("SELECT COUNT(*) FROM post WHERE parentHash = :parentHash")
    fun replyCount(parentHash: BlobHash): Int

    @Insert
    fun insert(post: Post): Long

    @Update
    fun update(post: Post)
}
