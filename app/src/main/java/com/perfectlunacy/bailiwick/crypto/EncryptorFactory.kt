package com.perfectlunacy.bailiwick.crypto

import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.storage.NodeId
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.Path

/**
 * Factory for creating Encryptor instances from stored keys.
 *
 * This is a stateless utility class that creates appropriate ciphers
 * based on keys stored in the database.
 */
object EncryptorFactory {

    /**
     * Create an encryptor for decrypting content from a peer.
     *
     * SECURITY: Only tries AES keys we have received from the peer.
     * Content MUST be encrypted - we no longer accept plaintext content.
     * This prevents unauthorized access to Circle content.
     *
     * @param keyDao Database access for keys
     * @param nodeId The peer's node ID
     * @param validator Function to validate if decryption succeeded
     * @return MultiCipher that tries all available ciphers
     */
    fun forPeer(keyDao: KeyDao, nodeId: NodeId, validator: (ByteArray) -> Boolean): Encryptor {
        val ciphers: MutableList<Encryptor> = keyDao.keysFor(nodeId).mapNotNull { key ->
            key.secretKey?.let { k -> AESEncryptor(k) }
        }.toMutableList()
        // SECURITY: Do NOT add NoopEncryptor - content MUST be encrypted with a shared key

        return MultiCipher(ciphers, validator)
    }

    /**
     * Create an encryptor for a specific circle.
     *
     * IMPORTANT: This reads the key from keystore.json to ensure consistency
     * with the key sent to peers via KeyRetrieval.getKeyBytesForCircle().
     * Previously used AndroidKeyStore which could become out of sync.
     *
     * @param keyDao Database access for keys
     * @param circleId The circle's ID
     * @return AESEncryptor with the circle's key
     * @throws IllegalStateException if no key exists for the circle
     */
    fun forCircle(keyDao: KeyDao, circleId: Long): Encryptor {
        val curKey = keyDao.keysFor("circle:$circleId").firstOrNull()
            ?: throw IllegalStateException("No key found for circle $circleId")
        val alias = curKey.alias

        // Read key from keystore.json - the SAME source used for key exchange
        // This ensures the key we encrypt with matches what we send to peers
        val bw = Bailiwick.getInstance()
        val filesDir = Path(bw.cacheDir.parentFile?.path ?: bw.cacheDir.path)
        val keystoreCipher = RsaWithAesEncryptor(bw.keyring.privateKey, bw.keyring.publicKey)

        val keyFile = KeyStorage.loadKeyFile(filesDir, keystoreCipher)
        val keyRec = keyFile.keys.find { it.alias == alias }
            ?: throw IllegalStateException("Key with alias $alias not found in keystore.json for circle $circleId")

        val keyBytes = Base64.getDecoder().decode(keyRec.encKey)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val keyId = keyBytes.take(4).map { "%02x".format(it) }.joinToString("")
        android.util.Log.i("EncryptorFactory", "forCircle($circleId): alias=$alias, keyId=$keyId (from keystore.json)")

        return AESEncryptor(secretKey)
    }
}
