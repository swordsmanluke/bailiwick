package com.perfectlunacy.bailiwick.crypto

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports user identity data to an encrypted backup file.
 *
 * The export file format:
 * - First 16 bytes: Salt for PBKDF2
 * - Next 12 bytes: IV for AES-GCM
 * - Remaining bytes: AES-256-GCM encrypted JSON payload
 *
 * The JSON payload contains:
 * - Iroh secret key (Ed25519 private key for node identity)
 * - Identity data (display name, avatar hash)
 * - Account username (password is not exported)
 * - Creation timestamp and format version
 */
object KeyExporter {
    private const val TAG = "KeyExporter"

    // Cryptographic parameters
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IROH_SECRET_KEY_PREF = "iroh_secret_key"
    private const val IROH_CONFIG_PREFS = "iroh_config"

    // Current export format version
    private const val EXPORT_VERSION = 1

    /**
     * Export identity data to an encrypted file.
     *
     * @param context Application context
     * @param db Database instance
     * @param password Password to encrypt the export
     * @return Encrypted export data as byte array
     * @throws ExportException if export fails
     */
    suspend fun export(
        context: Context,
        db: BailiwickDatabase,
        password: String
    ): ByteArray {
        Log.i(TAG, "Starting identity export")

        // Gather identity data
        val account = db.accountDao().activeAccount()
            ?: throw ExportException("No account found to export")

        val identity = db.identityDao().identitiesFor(account.peerId).firstOrNull()
            ?: throw ExportException("No identity found for account")

        // Get Iroh secret key from SharedPreferences
        val prefs = context.getSharedPreferences(IROH_CONFIG_PREFS, Context.MODE_PRIVATE)
        val secretKeyBase64 = prefs.getString(IROH_SECRET_KEY_PREF, null)
            ?: throw ExportException("No Iroh secret key found")

        // Build export payload
        val payload = ExportPayload(
            version = EXPORT_VERSION,
            createdAt = System.currentTimeMillis(),
            secretKey = secretKeyBase64,
            username = account.username,
            displayName = identity.name,
            avatarHash = identity.profilePicHash
        )

        val payloadJson = Gson().toJson(payload)
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)

        Log.d(TAG, "Payload size: ${payloadBytes.size} bytes")

        // Generate cryptographic material
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        // Derive encryption key from password
        val key = deriveKey(password, salt)

        // Encrypt payload
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(payloadBytes)

        // Combine: salt + iv + encrypted data
        val result = ByteArray(salt.size + iv.size + encrypted.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(encrypted, 0, result, salt.size + iv.size, encrypted.size)

        Log.i(TAG, "Export complete: ${result.size} bytes")
        return result
    }

    /**
     * Save export data to a file.
     *
     * @param data Encrypted export data
     * @param outputFile File to write to
     */
    fun saveToFile(data: ByteArray, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(data)
        Log.i(TAG, "Saved export to ${outputFile.absolutePath}")
    }

    /**
     * Get recommended filename for export.
     */
    fun getRecommendedFilename(displayName: String): String {
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        return "bailiwick-${safeName}-${timestamp}.bwkey"
    }

    /**
     * Derive AES key from password using PBKDF2.
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    /**
     * Export payload structure.
     */
    data class ExportPayload(
        @SerializedName("v") val version: Int,
        @SerializedName("ts") val createdAt: Long,
        @SerializedName("sk") val secretKey: String,
        @SerializedName("un") val username: String,
        @SerializedName("dn") val displayName: String,
        @SerializedName("av") val avatarHash: String?
    )

    /**
     * Exception for export errors.
     */
    class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
