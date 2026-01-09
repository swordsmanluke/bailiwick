package com.perfectlunacy.bailiwick.crypto

import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.storage.NodeId

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
     * Tries all available AES keys for the peer, falling back to NoopEncryptor
     * for unencrypted content. Uses the validator to determine which key works.
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
        ciphers.add(NoopEncryptor())

        return MultiCipher(ciphers, validator)
    }

    /**
     * Create an encryptor for a specific circle.
     *
     * @param keyDao Database access for keys
     * @param circleId The circle's ID
     * @return AESEncryptor with the circle's key
     * @throws IllegalStateException if no key exists for the circle
     */
    fun forCircle(keyDao: KeyDao, circleId: Long): Encryptor {
        val curKey = keyDao.keysFor("circle:$circleId").firstOrNull()
            ?: throw IllegalStateException("No key found for circle $circleId")
        val secretKey = curKey.secretKey
            ?: throw IllegalStateException("Key for circle $circleId has no secret key")
        return AESEncryptor(secretKey)
    }
}
