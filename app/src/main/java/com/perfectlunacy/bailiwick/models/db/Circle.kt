package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash

@Entity
data class Circle(
    var name: String,
    val identityId: Long,
    var blobHash: BlobHash?        // Iroh blob hash (was: cid)
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface CircleDao {
    @Query("SELECT * FROM circle")
    fun all(): List<Circle>

    @Query("SELECT * FROM circle WHERE id = :id LIMIT 1")
    fun find(id: Long): Circle

    @Query("SELECT * FROM circle WHERE identityId = :identityId")
    fun circlesFor(identityId: Long): List<Circle>

    @Insert
    fun insert(circle: Circle): Long

    @Query("UPDATE circle SET blobHash = NULL WHERE id = :id")
    fun clearHash(id: Long)

    @Query("UPDATE circle SET blobHash = :hash WHERE id = :id")
    fun storeHash(id: Long, hash: BlobHash)

    @Update
    fun update(circle: Circle)
}
