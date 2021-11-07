package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId

@Entity
data class Post(
    val authorId: Long,
    val cid: ContentId?,
    val timestamp: Long,
    val parent: ContentId?,
    val text: String,
    var signature: String
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface PostDao {
    @Query("SELECT * FROM post")
    fun all(): List<Post>

    @Query("SELECT * FROM post WHERE cid IS NULL")
    fun inNeedOfSync(): List<Post>

    @Query("SELECT * FROM post WHERE id = :id LIMIT 1")
    fun find(id: Long): Post

    @Query("SELECT * FROM post WHERE cid = :cid")
    fun findByCid(cid: ContentId): Post?

    @Query("SELECT * FROM post WHERE authorId = :authorId")
    fun postsFor(authorId: Long): List<Post>

    @Query("SELECT * FROM post WHERE authorId IN (:authorIds)")
    fun postsFor(authorIds: List<Long>): List<Post>

    @Query("SELECT EXISTS( SELECT 1 FROM post WHERE cid = :cid)")
    fun postExists(cid: ContentId): Boolean

    @Query("UPDATE post SET cid = :cid WHERE id = :id")
    fun updateCid(id: Long, cid: ContentId)

    @Insert
    fun insert(post: Post): Long

}