package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.PeerId
import java.security.KeyStore
import java.security.PrivateKey
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

enum class KeyType{
    Private,
    Secret
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

    @Insert
    fun insert(key: Key): Long
}