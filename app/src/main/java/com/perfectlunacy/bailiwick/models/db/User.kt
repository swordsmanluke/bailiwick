package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.PeerId

@Entity(indices = [Index(value = ["peerId"], unique = true)])
data class User(val peerId: PeerId, val publicKey: String) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun all(): List<User>

    @Query("SELECT publicKey FROM user WHERE peerId = :peerId LIMIT 1")
    fun publicKeyFor(peerId: PeerId): String?

    @Insert
    fun insert(user: User)
}