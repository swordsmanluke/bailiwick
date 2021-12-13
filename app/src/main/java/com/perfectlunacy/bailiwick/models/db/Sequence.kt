package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

@Entity
data class Sequence(@PrimaryKey val peerId: PeerId,
                    var sequence: Long,
                    @ColumnInfo(defaultValue = "1")
                    var upToDate: Boolean )

@Dao
interface SequenceDao{
    @Query("SELECT * FROM sequence")
    fun all(): List<Sequence>

    @Query("SELECT * FROM sequence WHERE peerId = :peerId LIMIT 1")
    fun find(peerId: PeerId): Sequence?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(sequence: Sequence): Long

    @Query("UPDATE sequence SET sequence = :sequence WHERE peerId = :peerId")
    fun updateSequence(peerId: PeerId, sequence: Long)

    @Query("UPDATE sequence SET upToDate = 1 WHERE peerId = :peerId AND sequence = :sequence")
    fun setUpToDate(peerId: PeerId, sequence: Long)

    @Query("UPDATE sequence SET upToDate = 0 WHERE peerId = :peerId AND sequence = :sequence")
    fun clearUpToDate(peerId: PeerId, sequence: Long)

    @Query("SELECT MAX(sequence) FROM sequence WHERE peerId = :peerId AND upToDate = 1")
    fun upToDateSequence(peerId: PeerId): Long?
}