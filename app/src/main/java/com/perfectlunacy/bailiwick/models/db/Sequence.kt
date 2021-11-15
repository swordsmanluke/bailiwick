package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

@Entity
data class Sequence(@PrimaryKey val peerId: PeerId,
                  var sequence: Long)

@Dao
interface SequenceDao{
    @Query("SELECT * FROM sequence")
    fun all(): List<Sequence>

    @Query("SELECT * FROM sequence WHERE peerId = :peerId LIMIT 1")
    fun find(peerId: PeerId): Sequence?

    @Insert
    fun insert(sequence: Sequence): Long

    @Query("UPDATE sequence SET sequence = :sequence WHERE peerId = :peerId")
    fun updateSequence(peerId: PeerId, sequence: Long)
}