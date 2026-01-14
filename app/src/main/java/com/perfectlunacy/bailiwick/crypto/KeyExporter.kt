package com.perfectlunacy.bailiwick.crypto

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.util.GsonProvider
import java.io.OutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports user identity and keys to an encrypted backup file.
 *
 * File format (JSON):
 * {
 *   "version": 1,
 *   "salt": "<base64>",
 *   "iv": "<base64>",
 *   "encryptedData": "<base64>"
 * }
 *
 * Encrypted data contains:
 * {
 *   "identity": { name, nodeId, profilePicHash },
 *   "privateKey": "<base64 encoded>",
 *   "publicKey": "<base64 encoded>",
 *   "createdAt": <timestamp>,
 *   "exportedAt": <timestamp>
 * }
 */
class KeyExporter {

    companion object {
        private const val TAG = "KeyExporter"
        private const val CURRENT_VERSION = 1
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        private const val IV_LENGTH = 12  // 96 bits for GCM
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Export identity and keys to an encrypted backup.
     *
     * @param identity The user's identity to export
     * @param privateKey The private key to export
     * @param publicKey The public key to export
     * @param password Password to encrypt the backup (minimum 8 characters)
     * @param output Stream to write the encrypted backup to
     * @throws IllegalArgumentException if password is too short
     */
    fun export(
        identity: Identity,
        privateKey: PrivateKey,
        publicKey: PublicKey,
        password: String,
        output: OutputStream
    ) {
        require(password.length >= 8) { "Password must be at least 8 characters" }

        Log.i(TAG, "Exporting identity: ${identity.name}")

        // Generate random salt and IV
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        // Derive encryption key from password using PBKDF2
        val secretKey = deriveKey(password, salt)

        // Prepare export data
        val exportData = ExportData(
            identity = IdentityData(
                name = identity.name,
                nodeId = identity.owner,
                profilePicHash = identity.profilePicHash ?: ""
            ),
            privateKey = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP),
            publicKey = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
            createdAt = System.currentTimeMillis(), // TODO: Store actual creation time
            exportedAt = System.currentTimeMillis()
        )

        // Serialize and encrypt
        val plaintext = GsonProvider.gson.toJson(exportData).toByteArray(Charsets.UTF_8)
        val ciphertext = encrypt(plaintext, secretKey, iv)

        // Create export file
        val exportFile = ExportFile(
            version = CURRENT_VERSION,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            encryptedData = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )

        // Write to output
        output.write(GsonProvider.gson.toJson(exportFile).toByteArray(Charsets.UTF_8))
        output.flush()

        Log.i(TAG, "Export complete")
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun encrypt(plaintext: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        return cipher.doFinal(plaintext)
    }

    // Data classes for serialization
    data class ExportFile(
        val version: Int,
        val salt: String,
        val iv: String,
        val encryptedData: String
    )

    data class ExportData(
        val identity: IdentityData,
        val privateKey: String,
        val publicKey: String,
        val createdAt: Long,
        val exportedAt: Long
    )

    data class IdentityData(
        val name: String,
        val nodeId: String,
        val profilePicHash: String
    )
}
