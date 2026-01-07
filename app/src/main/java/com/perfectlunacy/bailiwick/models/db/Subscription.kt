package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.NodeId

@Entity(indices = [Index(value = ["nodeId"], unique = true)])
data class Subscription(val nodeId: NodeId, val version: Long) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface SubscriptionDao{
    @Query("SELECT * FROM subscription")
    fun all(): List<Subscription>

    @Query("UPDATE subscription SET version = :version WHERE nodeId = :nodeId")
    fun setLatestVersion(nodeId: NodeId, version: Long)

    @Insert
    fun insert(sub: Subscription)
}