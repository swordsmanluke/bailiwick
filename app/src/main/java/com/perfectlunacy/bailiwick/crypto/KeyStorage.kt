package com.perfectlunacy.bailiwick.crypto

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Key
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.models.db.KeyType
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.util.GsonProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Manages storage and retrieval of cryptographic keys.
 *
 * Keys are stored in multiple locations:
 * - Android KeyStore: For secure, hardware-backed operations
 * - Encrypted JSON file: For backup and cross-device sync
 * - Room database: For metadata and lookup
 */
object KeyStorage {
    private const val TAG = "KeyStorage"

    /**
     * Store an AES key received from a peer.
     *
     * This method first removes any existing keys for this peer to avoid
     * accumulating stale or invalid keys. Only the most recent key should be used.
     *
     * @param keyDao Database access for key metadata
     * @param nodeId The peer's node ID
     * @param key Base64-encoded AES key
     */
    fun storeAesKey(keyDao: KeyDao, nodeId: NodeId, key: String) {
        // Clean up old keys for this peer first
        val oldKeys = keyDao.keysFor(nodeId).filter { it.type == KeyType.Secret }
        if (oldKeys.isNotEmpty()) {
            Log.d(TAG, "Removing ${oldKeys.size} old keys for $nodeId before storing new key")
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            for (oldKey in oldKeys) {
                try {
                    ks.deleteEntry(oldKey.alias)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete old key ${oldKey.alias} from KeyStore: ${e.message}")
                }
            }
            keyDao.deleteKeysFor(nodeId, KeyType.Secret)
        }

        // Store the new key
        val keyBytes = Base64.getDecoder().decode(key)
        val pk = SecretKeySpec(keyBytes, "AES")
        val keyId = keyBytes.take(4).map { "%02x".format(it) }.joinToString("")
        Log.d(TAG, "Storing key for $nodeId: keyId=$keyId, size=${keyBytes.size} bytes")

        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        val entry = KeyStore.SecretKeyEntry(pk)
        val alias = UUID.randomUUID().toString()
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .build()
        ks.setEntry(alias, entry, protection)

        // Store key with keyBytes so we can decrypt without AndroidKeyStore
        keyDao.insert(Key(nodeId, alias, "AES", KeyType.Secret, key))
        Log.i(TAG, "Stored new key for $nodeId: alias=$alias, keyId=$keyId, keyBytes=${key.take(8)}...")
    }

    /**
     * Store a peer's public key.
     *
     * @param filesDir App's files directory
     * @param nodeId The peer's node ID
     * @param publicKey Base64-encoded public key
     * @param cipher Cipher to encrypt the keystore file
     */
    fun storePublicKey(filesDir: Path, nodeId: NodeId, publicKey: String, cipher: Encryptor) {
        val keyFile = loadKeyFile(filesDir, cipher)
        keyFile.keys.find { it.alias == "$nodeId:public" }.also {
            if (it != null) {
                Log.w(TAG, "We already have a public key for node $nodeId")
                return
            }
        }

        keyFile.keys.add(KeyStoreRecord("$nodeId:public", publicKey, KeyType.Public.toString()))
        saveKeyFile(filesDir, keyFile, cipher)
    }

    /**
     * Load the encrypted keystore file.
     */
    internal fun loadKeyFile(filesDir: Path, cipher: Encryptor): KeyFile {
        val f = Path(filesDir.pathString, "bwcache", "keystore.json").toFile()
        val rawJson = if (f.exists()) {
            BufferedInputStream(FileInputStream(f)).use { input ->
                val readString = String(cipher.decrypt(input.readBytes()))
                if (readString.isBlank()) {
                    "{keys: []}"
                } else {
                    readString
                }
            }
        } else {
            "{keys: []}"
        }

        return GsonProvider.gson.fromJson(rawJson, KeyFile::class.java)
    }

    /**
     * Save the encrypted keystore file.
     */
    internal fun saveKeyFile(filesDir: Path, keyFile: KeyFile, cipher: Encryptor) {
        val f = Path(filesDir.pathString, "bwcache", "keystore.json").toFile()
        f.parentFile?.mkdirs()

        val encryptedFile = cipher.encrypt(GsonProvider.gson.toJson(keyFile).toByteArray())
        BufferedOutputStream(FileOutputStream(f)).use { file ->
            file.write(encryptedFile)
        }
    }

    /**
     * Internal data class representing the encrypted keystore file format.
     */
    data class KeyFile(val keys: MutableList<KeyStoreRecord>)

    /**
     * Internal data class representing a single key record.
     */
    data class KeyStoreRecord(val alias: String, val encKey: String, val type: String)
}
