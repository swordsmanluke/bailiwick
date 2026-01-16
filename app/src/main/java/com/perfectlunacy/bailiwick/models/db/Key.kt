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
data class Key(
    val key: String,
    val alias: String,
    val algo: String,
    val type: KeyType,
    /** Base64-encoded key bytes for Secret keys. Used to ensure consistency with key exchange. */
    val keyBytes: String? = null
){
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    val secretKey: SecretKey?
        get() {
            if(type == KeyType.Private) { return null }

            // If we have stored key bytes, use them directly (more reliable than AndroidKeyStore)
            if (keyBytes != null) {
                val decoded = java.util.Base64.getDecoder().decode(keyBytes)
                return javax.crypto.spec.SecretKeySpec(decoded, "AES")
            }

            // Fallback to AndroidKeyStore for backwards compatibility
            return try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
            } catch (e: Exception) {
                null
            }
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

    /**
     * Delete all keys for a given key (nodeId).
     * Used to clean up old/invalid keys before storing a new one.
     */
    @Query("DELETE FROM `key` WHERE `key` = :key AND type = :type")
    fun deleteKeysFor(key: String, type: KeyType)

    /**
     * Delete a specific key by alias.
     */
    @Query("DELETE FROM `key` WHERE alias = :alias")
    fun deleteByAlias(alias: String)
}