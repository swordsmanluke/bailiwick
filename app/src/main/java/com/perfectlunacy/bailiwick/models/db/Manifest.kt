package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId

@Entity(indices = [Index(value = ["sequence"], unique = true)])
data class Manifest(val cid: ContentId, val sequence: Long) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface ManifestDao {
    @Query("SELECT * FROM manifest")
    fun all(): List<Manifest>

    @Query("SELECT MAX(sequence) FROM manifest")
    fun currentSequence(): Long?

    @Query("SELECT * FROM manifest ORDER BY sequence DESC LIMIT 1")
    fun current(): Manifest?

    @Query("SELECT * FROM manifest WHERE sequence = :sequence LIMIT 1")
    fun find(sequence: Long): Manifest?

    @Insert
    fun insert(manifest: Manifest)
}