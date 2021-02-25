package com.perfectlunacy.bailiwick.models.db

import androidx.room.*

@Entity
data class Post (
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo val user_id: Int,
    @ColumnInfo val timestamp: Long,
    @ColumnInfo val text: String,
    @ColumnInfo var signature: String?=null
) {
    // FIXME: Signature calculation doesn't match docs
    constructor(author: User, text: String): this(0, author.id, System.currentTimeMillis(), text, text.hashCode().toString())
}

@Entity(foreignKeys = arrayOf(
    ForeignKey(entity = Post::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("post_id"))))
data class PostFile(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo val post_id: Int,
    @ColumnInfo val mimeType: String,
    @ColumnInfo val cid: String,
    @ColumnInfo val signature: String
) {
    // FIXME: Signature calculation doesn't match docs
    constructor(post_id: Int,
                mimeType: String,
                cid: String) : this(0, post_id, mimeType, cid, cid.hashCode().toString())
}

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
    fun filesForPost(post_id: Int): List<PostFile>
}

// TODO: This is... less than ideal. Think of a better way to perform these conversions
class PostConverter(val users: UserDao, val files: PostFileDao) {

    fun toDbPost(post: com.perfectlunacy.bailiwick.models.Post): Post {
        return Post(
            0,
            post.author.id,
            post.timestamp,
            post.text,
            post.signature
        )
    }

    fun toPostModel(dbPost: Post): com.perfectlunacy.bailiwick.models.Post {
        val attachments = files.filesForPost(dbPost.id).map{ dbF -> toPostFileModel(dbF) }

        return com.perfectlunacy.bailiwick.models.Post(
            "0.1",
            dbPost.timestamp,
            users.find(dbPost.user_id),
            dbPost.text,
            attachments,
            dbPost.signature
        )
    }

    fun toDbFile(source: com.perfectlunacy.bailiwick.models.PostFile, post_id: Int): PostFile {
        return PostFile(0, post_id, source.mimeType, source.cid, source.signature)
    }

    fun toPostFileModel(source: PostFile): com.perfectlunacy.bailiwick.models.PostFile {
        return com.perfectlunacy.bailiwick.models.PostFile(source.mimeType, source.cid, source.signature)
    }
}