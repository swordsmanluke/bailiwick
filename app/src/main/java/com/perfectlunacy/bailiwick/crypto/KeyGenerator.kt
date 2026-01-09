package com.perfectlunacy.bailiwick.crypto

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Key
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.models.db.KeyType
import java.nio.file.Path
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.KeyGenerator as JavaKeyGenerator

/**
 * Generates cryptographic keys and stores them securely.
 *
 * Uses Android KeyStore for secure hardware-backed storage when available.
 */
object KeyGenerator {

    /**
     * Generate a new AES key for a circle.
     *
     * The key is stored in:
     * 1. Android KeyStore (for secure operations)
     * 2. Encrypted keystore file (for backup/export)
     * 3. Room database (for lookup by circle ID)
     *
     * @param keyDao Database access for key metadata
     * @param filesDir App's files directory for encrypted keystore
     * @param circleId The circle this key is for
     * @param cipher Cipher to encrypt the keystore file
     * @return The alias used to reference this key
     */
    fun generateAesKeyForCircle(keyDao: KeyDao, filesDir: Path, circleId: Long, cipher: Encryptor): String {
        val alias = UUID.randomUUID().toString()
        val keyGen = JavaKeyGenerator.getInstance("AES")
        val key = keyGen.generateKey()

        // Store in encrypted keystore file
        val keyFile = KeyStorage.loadKeyFile(filesDir, cipher)
        keyFile.keys.add(KeyStorage.KeyStoreRecord(alias, Base64.getEncoder().encodeToString(key.encoded), "Secret"))
        KeyStorage.saveKeyFile(filesDir, keyFile, cipher)

        // Add key to AndroidKeyStore
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        val entry = KeyStore.SecretKeyEntry(key)
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .build()
        ks.setEntry(alias, entry, protection)

        // Store metadata in database
        keyDao.insert(Key("circle:$circleId", alias, "AES", KeyType.Secret))

        return alias
    }
}
