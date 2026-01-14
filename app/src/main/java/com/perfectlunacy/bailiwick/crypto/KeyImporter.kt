package com.perfectlunacy.bailiwick.crypto

import android.util.Base64
import android.util.Log
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.util.GsonProvider
import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Imports user identity and keys from an encrypted backup file.
 */
class KeyImporter {

    companion object {
        private const val TAG = "KeyImporter"
        private const val SUPPORTED_VERSION = 1
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_LENGTH = 256
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Result of an import operation.
     */
    sealed class ImportResult {
        data class Success(
            val identity: Identity,
            val privateKey: PrivateKey,
            val publicKey: PublicKey,
            val createdAt: Long,
            val exportedAt: Long
        ) : ImportResult()

        data class Error(val message: String, val cause: Throwable? = null) : ImportResult()
    }

    /**
     * Import identity and keys from an encrypted backup.
     *
     * @param input Stream to read the encrypted backup from
     * @param password Password to decrypt the backup
     * @return ImportResult indicating success or failure
     */
    fun import(input: InputStream, password: String): ImportResult {
        return try {
            Log.i(TAG, "Starting import")

            // Read and parse export file
            val fileContent = input.bufferedReader().use { it.readText() }
            val exportFile = GsonProvider.gson.fromJson(fileContent, KeyExporter.ExportFile::class.java)

            // Validate version
            if (exportFile.version != SUPPORTED_VERSION) {
                return ImportResult.Error("Unsupported backup version: ${exportFile.version}")
            }

            // Decode salt and IV
            val salt = Base64.decode(exportFile.salt, Base64.NO_WRAP)
            val iv = Base64.decode(exportFile.iv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(exportFile.encryptedData, Base64.NO_WRAP)

            // Derive decryption key
            val secretKey = deriveKey(password, salt)

            // Decrypt
            val plaintext = try {
                decrypt(ciphertext, secretKey, iv)
            } catch (e: Exception) {
                Log.w(TAG, "Decryption failed - likely wrong password", e)
                return ImportResult.Error("Invalid password or corrupted file")
            }

            // Parse decrypted data
            val exportData = GsonProvider.gson.fromJson(
                String(plaintext, Charsets.UTF_8),
                KeyExporter.ExportData::class.java
            )

            // Reconstruct keys
            val privateKey = reconstructPrivateKey(exportData.privateKey)
            val publicKey = reconstructPublicKey(exportData.publicKey)

            // Create identity (without ID - will be assigned on insert)
            val identity = Identity(
                blobHash = null,
                owner = exportData.identity.nodeId,
                name = exportData.identity.name,
                profilePicHash = exportData.identity.profilePicHash.ifEmpty { null }
            )

            Log.i(TAG, "Import successful: ${identity.name}")

            ImportResult.Success(
                identity = identity,
                privateKey = privateKey,
                publicKey = publicKey,
                createdAt = exportData.createdAt,
                exportedAt = exportData.exportedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult.Error("Import failed: ${e.message}", e)
        }
    }

    /**
     * Validate a backup file without fully importing it.
     * Useful for checking password before committing to import.
     */
    fun validate(input: InputStream, password: String): ImportResult {
        return import(input, password)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun decrypt(ciphertext: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun reconstructPrivateKey(base64Key: String): PrivateKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private fun reconstructPublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }
}
