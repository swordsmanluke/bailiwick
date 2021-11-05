package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.PeerId

@Entity(indices = [Index(value = ["peerId"], unique = true)])
data class Subscription(val peerId: PeerId, val version: Long) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface SubscriptionDao{
    @Query("SELECT * FROM subscription")
    fun all(): List<Subscription>

    @Query("UPDATE subscription SET version = :version WHERE peerId = :peerId")
    fun setLatestVersion(peerId: PeerId, version: Long)

    @Insert
    fun insert(sub: Subscription)
}