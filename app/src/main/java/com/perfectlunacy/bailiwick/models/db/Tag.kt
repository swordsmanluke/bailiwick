package com.perfectlunacy.bailiwick.models.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash

/**
 * A tag/topic associated with a post.
 *
 * Tags are created by the post author to categorize content.
 * Users can filter their feed by tags.
 */
@Entity(
    indices = [
        Index(value = ["postHash"]),
        Index(value = ["name"]),
        Index(value = ["postHash", "name"], unique = true)
    ]
)
data class Tag(
    val postHash: BlobHash,           // Hash of the tagged post
    val name: String,                 // Tag name (lowercase, no spaces, e.g., "photography")
    val authorNodeId: String          // Who created this tag (post author)
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    companion object {
        /**
         * Normalize a tag name: lowercase, trim, replace spaces with hyphens.
         */
        fun normalize(tagName: String): String {
            return tagName.trim()
                .lowercase()
                .replace(Regex("\\s+"), "-")
                .replace(Regex("[^a-z0-9-]"), "")
                .take(50) // Max tag length
        }
    }
}

/**
 * Tag usage statistics for autocomplete and discovery.
 */
data class TagStats(
    val name: String,
    val postCount: Int
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tag WHERE postHash = :postHash ORDER BY name ASC")
    fun tagsForPost(postHash: BlobHash): List<Tag>

    @Query("SELECT * FROM tag WHERE postHash = :postHash ORDER BY name ASC")
    fun tagsForPostLive(postHash: BlobHash): LiveData<List<Tag>>

    @Query("SELECT DISTINCT postHash FROM tag WHERE name = :tagName")
    fun postHashesForTag(tagName: String): List<BlobHash>

    @Query("SELECT name, COUNT(DISTINCT postHash) as postCount FROM tag GROUP BY name ORDER BY postCount DESC")
    fun tagStats(): List<TagStats>

    @Query("SELECT name, COUNT(DISTINCT postHash) as postCount FROM tag GROUP BY name ORDER BY postCount DESC LIMIT :limit")
    fun topTags(limit: Int): List<TagStats>

    @Query("SELECT DISTINCT name FROM tag WHERE name LIKE :prefix || '%' ORDER BY name ASC LIMIT :limit")
    fun suggestTags(prefix: String, limit: Int = 10): List<String>

    @Query("SELECT * FROM tag WHERE name = :name")
    fun findByName(name: String): List<Tag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(tags: List<Tag>)

    @Delete
    fun delete(tag: Tag)

    @Query("DELETE FROM tag WHERE postHash = :postHash AND name = :name")
    fun deleteTag(postHash: BlobHash, name: String)

    @Query("DELETE FROM tag WHERE postHash = :postHash")
    fun deleteAllForPost(postHash: BlobHash)
}
