package com.perfectlunacy.bailiwick.models.db

import androidx.room.*

@Entity
@TypeConverters(CommonConverters::class)
data class Account(
    @PrimaryKey val username: String,
    var passwordHash: String,
    val peerId: String,
    var rootCid: String,
    var sequence: Int,
    var loggedIn: Boolean,

    // Gossip-related fields (Phase 1)
    /** Ed25519 public key for this account (32 bytes) */
    val ed25519PublicKey: ByteArray? = null,

    /** Topic key for Gossip announcements (32 bytes) */
    val topicKey: ByteArray? = null,

    /** Current manifest version number (monotonically increasing) */
    var manifestVersion: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Account

        if (username != other.username) return false
        if (peerId != other.peerId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + peerId.hashCode()
        return result
    }
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE passwordHash = :hashedPassword AND username = :username LIMIT 1")
    fun getByLogin(username: String, hashedPassword: String): Account?

    @Query("SELECT * FROM account WHERE loggedIn LIMIT 1")
    fun activeAccount(): Account?

    @Insert
    fun insert(account: Account)

    @Query("UPDATE account SET loggedIn = 0")
    fun logout()

    @Query("UPDATE account SET loggedIn = 1 where account.peerId = :peerId")
    fun activate(peerId: String)

    @Update
    fun update(account: Account)

    // Gossip-related methods
    @Query("UPDATE account SET manifestVersion = :version WHERE username = :username")
    fun updateManifestVersion(username: String, version: Long)

    @Query("SELECT manifestVersion FROM account WHERE loggedIn LIMIT 1")
    fun getManifestVersion(): Long?

    @Query("UPDATE account SET ed25519PublicKey = :publicKey, topicKey = :topicKey WHERE username = :username")
    fun updateGossipKeys(username: String, publicKey: ByteArray, topicKey: ByteArray)
}