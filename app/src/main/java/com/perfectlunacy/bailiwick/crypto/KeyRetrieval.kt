package com.perfectlunacy.bailiwick.crypto

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.NodeId
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Retrieves cryptographic keys from storage.
 */
object KeyRetrieval {

    /**
     * Get the raw AES key bytes for a circle.
     *
     * This is used when sharing the key with new circle members.
     *
     * @param keyDao Database access for key metadata
     * @param filesDir App's files directory
     * @param circleId The circle ID
     * @param cipher Cipher to decrypt the keystore file
     * @return The raw AES key bytes
     * @throws IllegalStateException if no key exists for the circle
     */
    fun getKeyBytesForCircle(keyDao: KeyDao, filesDir: Path, circleId: Int, cipher: Encryptor): ByteArray {
        // Use firstOrNull to get the newest key (ORDER BY id DESC)
        // Must match EncryptorFactory.forCircle which also uses firstOrNull
        val key = keyDao.keysFor("circle:$circleId").firstOrNull()
            ?: throw IllegalStateException("No key found for circle $circleId")
        val alias = key.alias

        val store = KeyStorage.loadKeyFile(filesDir, cipher)

        val keyRec = store.keys.find { it.alias == alias }
            ?: throw IllegalStateException("Key with alias $alias not found in keystore for circle $circleId")
        val keyBytes = Base64.getDecoder().decode(keyRec.encKey)
        val keyId = keyBytes.take(4).map { "%02x".format(it) }.joinToString("")
        android.util.Log.i("KeyRetrieval", "getKeyBytesForCircle($circleId): alias=$alias, keyId=$keyId, size=${keyBytes.size}")
        return keyBytes
    }

    /**
     * Get a peer's public key.
     *
     * @param userDao Database access for user public keys
     * @param nodeId The peer's node ID
     * @return The public key, or null if not found
     */
    fun getPublicKeyForPeer(userDao: UserDao, nodeId: NodeId): PublicKey? {
        val key = userDao.publicKeyFor(nodeId) ?: return null
        val publicKeyData = Base64.getDecoder().decode(key)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyData))
    }
}
