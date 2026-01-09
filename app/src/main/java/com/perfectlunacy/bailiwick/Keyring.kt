package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.crypto.KeyGenerator
import com.perfectlunacy.bailiwick.crypto.KeyRetrieval
import com.perfectlunacy.bailiwick.crypto.KeyStorage
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.NodeId
import java.nio.file.Path
import java.security.PublicKey

/**
 * Facade class for cryptographic key operations.
 *
 * This class delegates to focused utility classes for different responsibilities:
 * - [EncryptorFactory]: Creates Encryptor instances from stored keys
 * - [KeyGenerator]: Generates new cryptographic keys
 * - [KeyStorage]: Stores and persists keys
 * - [KeyRetrieval]: Retrieves keys from storage
 *
 * The static methods are maintained for backward compatibility.
 * New code should prefer using the focused classes directly for
 * better testability and clearer dependency management.
 */
class Keyring {
    companion object {
        const val TAG = "Keyring"

        // ===== Encryptor Factory Methods =====

        /**
         * Create an encryptor for decrypting content from a peer.
         * @see EncryptorFactory.forPeer
         */
        @JvmStatic
        fun encryptorForPeer(keyDao: KeyDao, nodeId: NodeId, validator: (ByteArray) -> Boolean): Encryptor {
            return EncryptorFactory.forPeer(keyDao, nodeId, validator)
        }

        /**
         * Create an encryptor for a specific circle.
         * @see EncryptorFactory.forCircle
         */
        @JvmStatic
        fun encryptorForCircle(keyDao: KeyDao, circleId: Long): Encryptor {
            return EncryptorFactory.forCircle(keyDao, circleId)
        }

        // ===== Key Generation Methods =====

        /**
         * Generate a new AES key for a circle.
         * @see KeyGenerator.generateAesKeyForCircle
         */
        @JvmStatic
        fun generateAesKey(keyDao: KeyDao, filesDir: Path, circleId: Long, cipher: Encryptor): String {
            return KeyGenerator.generateAesKeyForCircle(keyDao, filesDir, circleId, cipher)
        }

        // ===== Key Storage Methods =====

        /**
         * Store an AES key received from a peer.
         * @see KeyStorage.storeAesKey
         */
        @JvmStatic
        fun storeAesKey(keyDao: KeyDao, nodeId: NodeId, key: String) {
            KeyStorage.storeAesKey(keyDao, nodeId, key)
        }

        /**
         * Store a peer's public key.
         * @see KeyStorage.storePublicKey
         */
        @JvmStatic
        fun storePubKey(filesDir: Path, nodeId: NodeId, publicKey: String, cipher: Encryptor) {
            KeyStorage.storePublicKey(filesDir, nodeId, publicKey, cipher)
        }

        // ===== Key Retrieval Methods =====

        /**
         * Get the raw AES key bytes for a circle.
         * @see KeyRetrieval.getKeyBytesForCircle
         */
        @JvmStatic
        fun keyForCircle(keyDao: KeyDao, filesDir: Path, circleId: Int, cipher: Encryptor): ByteArray {
            return KeyRetrieval.getKeyBytesForCircle(keyDao, filesDir, circleId, cipher)
        }

        /**
         * Get a peer's public key.
         * @see KeyRetrieval.getPublicKeyForPeer
         */
        fun pubKeyFor(userDao: UserDao, nodeId: NodeId): PublicKey? {
            return KeyRetrieval.getPublicKeyForPeer(userDao, nodeId)
        }
    }

    // Legacy data classes maintained for backward compatibility with existing keystore files
    @Deprecated("Use KeyStorage.KeyFile instead", ReplaceWith("KeyStorage.KeyFile"))
    data class KeyFile(val keys: MutableList<KeyStoreRecord>)

    @Deprecated("Use KeyStorage.KeyStoreRecord instead", ReplaceWith("KeyStorage.KeyStoreRecord"))
    data class KeyStoreRecord(val alias: String, val encKey: String, val type: String)
}
