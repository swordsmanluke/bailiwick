package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.NodeId

@Entity(indices = [Index(value = ["nodeId"], unique = true)])
data class User(
    val nodeId: NodeId,
    val publicKey: String,
    var isMuted: Boolean = false
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun all(): List<User>

    @Query("SELECT * FROM user WHERE id = :id LIMIT 1")
    fun find(id: Long): User?

    @Query("SELECT * FROM user WHERE nodeId = :nodeId LIMIT 1")
    fun findByNodeId(nodeId: NodeId): User?

    @Query("SELECT publicKey FROM user WHERE nodeId = :nodeId LIMIT 1")
    fun publicKeyFor(nodeId: NodeId): String?

    @Query("UPDATE user SET isMuted = :muted WHERE id = :id")
    fun setMuted(id: Long, muted: Boolean)

    @Query("SELECT isMuted FROM user WHERE id = :id")
    fun isMuted(id: Long): Boolean

    @Insert
    fun insert(user: User)

    @Query("DELETE FROM user WHERE id = :id")
    fun delete(id: Long)
}