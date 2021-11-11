package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.SecretKey

enum class KeyType{
    Private,
    Secret,
    Public
}

@Entity(indices = [Index(value = ["alias"], unique = true)])
data class Key(val key: String, val alias: String, val algo: String, val type: KeyType){
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    val secretKey: SecretKey?
        get() {
            if(type == KeyType.Private) { return null }

            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        }

    val privateKey: PrivateKey?
        get() {
            if(type == KeyType.Secret) { return null }

            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            return (ks.getEntry(alias, null) as KeyStore.PrivateKeyEntry).privateKey
        }
}

@Dao
interface KeyDao {
    @Query("SELECT * FROM `key` WHERE `key` = :key ORDER BY id DESC")
    fun keysFor(key: String): List<Key>

    @Query("SELECT * FROM `key` WHERE alias = :peerId AND type = \"Public\"")
    fun pubKeyFor(peerId: String): Key

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(key: Key): Long
}