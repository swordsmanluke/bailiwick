package com.perfectlunacy.bailiwick.models.db

import androidx.room.*

@Entity
data class Post (
    @PrimaryKey val id: Int,
    @ColumnInfo val user_id: Int,
    @ColumnInfo val timestamp: Long,
    @ColumnInfo val text: String,
    @ColumnInfo var signature: String?=null
)

@Entity(foreignKeys = arrayOf(
    ForeignKey(entity = Post::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("post_id"))))
data class PostFile(
    @PrimaryKey val id: Int,
    @ColumnInfo val post_id: Int,
    @ColumnInfo val mimeType: String,
    @ColumnInfo val cid: String,
    @ColumnInfo val signature: String
)

@Dao
interface PostDao {
    @Insert
    fun insert(post: Post)

    @Update
    fun update(post: Post)

    @Delete
    fun delete(post: Post)

    @Query("SELECT * FROM post")
    fun all(): List<Post>

    @Query("SELECT * FROM post WHERE user_id=:user_id")
    fun postsForUser(user_id: Int): List<Post>
}

@Dao
interface PostFileDao {
    @Insert
    fun insert(post: PostFile)

    @Update
    fun update(post: PostFile)

    @Delete
    fun delete(post: PostFile)

    @Query("SELECT * FROM postfile")
    fun all(): List<PostFile>

    @Query("SELECT * FROM postfile WHERE post_id=:post_id")
    fun filesForPost(post_id: Int)
}