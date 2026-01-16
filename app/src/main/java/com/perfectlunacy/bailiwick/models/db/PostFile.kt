package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash

@Entity(indices = [Index(value = ["postId", "blobHash"], unique = true)])
data class PostFile(
    val postId: Long,
    val blobHash: BlobHash,       // Was: fileCid
    val mimeType: String
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface PostFileDao {
    @Query("SELECT * FROM postfile")
    fun all(): List<PostFile>

    @Query("SELECT * FROM postfile WHERE postId = :postId")
    fun filesForPost(postId: Long): List<PostFile>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(postFile: PostFile): Long

    @Query("DELETE FROM postfile WHERE postId = :postId")
    fun deleteForPost(postId: Long)
}
