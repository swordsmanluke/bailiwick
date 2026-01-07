package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.NodeId

@Entity(indices = [Index(value = ["nodeId"], unique = true)])
data class User(val nodeId: NodeId, val publicKey: String) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun all(): List<User>

    @Query("SELECT publicKey FROM user WHERE nodeId = :nodeId LIMIT 1")
    fun publicKeyFor(nodeId: NodeId): String?

    @Insert
    fun insert(user: User)
}