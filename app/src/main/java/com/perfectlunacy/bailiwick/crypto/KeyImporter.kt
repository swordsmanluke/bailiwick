package com.perfectlunacy.bailiwick.crypto

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.InputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Imports user identity from an encrypted backup file.
 *
 * Reads the .bwkey format created by KeyExporter:
 * - First 16 bytes: Salt for PBKDF2
 * - Next 12 bytes: IV for AES-GCM
 * - Remaining bytes: AES-256-GCM encrypted JSON payload
 */
object KeyImporter {
    private const val TAG = "KeyImporter"

    // Must match KeyExporter constants
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IROH_SECRET_KEY_PREF = "iroh_secret_key"
    private const val IROH_CONFIG_PREFS = "iroh_config"

    // Supported format versions
    private const val SUPPORTED_VERSION = 1

    /**
     * Result of an import operation.
     */
    sealed class ImportResult {
        data class Success(val data: ImportedData) : ImportResult()
        data class WrongPassword(val message: String) : ImportResult()
        data class InvalidFormat(val message: String) : ImportResult()
        data class UnsupportedVersion(val version: Int) : ImportResult()
        data class Error(val message: String, val cause: Throwable? = null) : ImportResult()
    }

    /**
     * Imported identity data.
     */
    data class ImportedData(
        val secretKey: String,      // Base64-encoded Ed25519 secret key
        val username: String,
        val displayName: String,
        val avatarHash: String?,
        val createdAt: Long
    )

    /**
     * Import identity from an encrypted backup file.
     *
     * @param input Stream containing the encrypted backup
     * @param password Password to decrypt the backup
     * @return ImportResult indicating success or failure type
     */
    fun import(input: InputStream, password: String): ImportResult {
        return try {
            Log.i(TAG, "Starting import")

            val data = input.readBytes()
            if (data.size < SALT_LENGTH + IV_LENGTH + 16) {
                return ImportResult.InvalidFormat("File too small to be a valid backup")
            }

            // Extract salt, IV, and ciphertext
            val salt = data.copyOfRange(0, SALT_LENGTH)
            val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            val ciphertext = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)

            // Derive decryption key
            val key = deriveKey(password, salt)

            // Decrypt
            val plaintext = try {
                decrypt(ciphertext, key, iv)
            } catch (e: AEADBadTagException) {
                Log.w(TAG, "Decryption failed - wrong password")
                return ImportResult.WrongPassword("Incorrect password")
            } catch (e: Exception) {
                Log.w(TAG, "Decryption failed", e)
                return ImportResult.WrongPassword("Incorrect password or corrupted file")
            }

            // Parse JSON
            val payloadJson = String(plaintext, Charsets.UTF_8)
            val payload = try {
                Gson().fromJson(payloadJson, KeyExporter.ExportPayload::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Invalid JSON in backup", e)
                return ImportResult.InvalidFormat("Corrupted backup data")
            }

            // Validate version
            if (payload.version != SUPPORTED_VERSION) {
                Log.w(TAG, "Unsupported version: ${payload.version}")
                return ImportResult.UnsupportedVersion(payload.version)
            }

            Log.i(TAG, "Import successful: ${payload.displayName}")

            ImportResult.Success(
                ImportedData(
                    secretKey = payload.secretKey,
                    username = payload.username,
                    displayName = payload.displayName,
                    avatarHash = payload.avatarHash,
                    createdAt = payload.createdAt
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult.Error("Import failed: ${e.message}", e)
        }
    }

    /**
     * Import from a file path.
     */
    fun importFromFile(file: File, password: String): ImportResult {
        return file.inputStream().use { import(it, password) }
    }

    /**
     * Apply imported data to restore identity.
     *
     * @param context Application context
     * @param data Imported identity data
     */
    fun applyImport(context: Context, data: ImportedData) {
        Log.i(TAG, "Applying import for: ${data.displayName}")

        // Store the Ed25519 secret key in SharedPreferences
        // This will be picked up by IrohWrapper on next initialization
        val prefs = context.getSharedPreferences(IROH_CONFIG_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(IROH_SECRET_KEY_PREF, data.secretKey)
            .apply()

        Log.i(TAG, "Secret key stored, app restart required to apply new identity")
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
     * Decrypt ciphertext using AES-GCM.
     */
    private fun decrypt(ciphertext: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }
}
