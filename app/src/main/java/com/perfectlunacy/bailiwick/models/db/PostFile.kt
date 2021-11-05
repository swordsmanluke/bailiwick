package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId

@Entity(indices = [Index(value = ["postId", "fileCid"], unique = true)])
data class PostFile(val postId: Long, val fileCid: ContentId) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface PostFileDao {
    @Query("SELECT * FROM postfile WHERE postId = :postId")
    fun filesFor(postId: Long): List<PostFile>

    @Insert
    fun insert(postFile: PostFile): Long
}