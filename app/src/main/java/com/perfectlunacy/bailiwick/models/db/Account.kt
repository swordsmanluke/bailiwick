package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import java.security.PublicKey

@Entity
data class Account(@PrimaryKey val username: String,
                   val passwordHash: String,
                   val peerId: String,
                   val rootCid: String,
                   val sequence: Int,
                   val loggedIn: Boolean)

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
}