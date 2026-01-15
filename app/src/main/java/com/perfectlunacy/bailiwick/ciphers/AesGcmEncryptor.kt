package com.perfectlunacy.bailiwick.ciphers

import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM authenticated encryption.
 *
 * GCM (Galois/Counter Mode) provides:
 * - Confidentiality (encryption)
 * - Authenticity (tamper detection)
 * - No padding required
 *
 * Format: [12-byte nonce][ciphertext][16-byte auth tag]
 *
 * Key size: 256 bits (32 bytes)
 * Nonce size: 96 bits (12 bytes) - randomly generated per encryption
 * Auth tag size: 128 bits (16 bytes)
 */
class AesGcmEncryptor(private val key: ByteArray) : Encryptor {

    init {
        require(key.size == KEY_SIZE) { "AES-256 requires a 32-byte key, got ${key.size}" }
    }

    private val secretKey = SecretKeySpec(key, "AES")

    override fun encrypt(data: ByteArray): ByteArray {
        // Generate random nonce
        val nonce = ByteArray(NONCE_SIZE)
        SecureRandom().nextBytes(nonce)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(data)

        // Prepend nonce to ciphertext (ciphertext includes auth tag)
        return nonce + ciphertext
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if (data.size < NONCE_SIZE + TAG_SIZE) {
            Log.w(TAG, "AES-GCM decrypt failed: data too short (${data.size} bytes)")
            return byteArrayOf()
        }

        return try {
            val nonce = data.sliceArray(0 until NONCE_SIZE)
            val ciphertext = data.sliceArray(NONCE_SIZE until data.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.w(TAG, "AES-GCM decrypt failed: ${e.message}")
            byteArrayOf()
        }
    }

    companion object {
        private const val TAG = "AesGcmEncryptor"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 32        // 256 bits
        private const val NONCE_SIZE = 12      // 96 bits (recommended for GCM)
        private const val TAG_SIZE = 16        // 128 bits
        private const val TAG_SIZE_BITS = 128

        /**
         * Create an encryptor from a Base64-encoded key.
         */
        fun fromBase64Key(keyB64: String): AesGcmEncryptor {
            val key = java.util.Base64.getDecoder().decode(keyB64)
            return AesGcmEncryptor(key)
        }

        /**
         * Generate a new random AES-256 key.
         */
        fun generateKey(): ByteArray {
            val key = ByteArray(KEY_SIZE)
            SecureRandom().nextBytes(key)
            return key
        }

        /**
         * Create an encryptor with a newly generated key.
         */
        fun withNewKey(): Pair<AesGcmEncryptor, ByteArray> {
            val key = generateKey()
            return Pair(AesGcmEncryptor(key), key)
        }
    }
}

/**
 * Extension for creating an AesGcmEncryptor from X25519 key agreement.
 */
fun AesGcmEncryptor.Companion.fromKeyAgreement(
    myPrivateKey: ByteArray,
    peerPublicKey: ByteArray,
    context: String = "encryption"
): AesGcmEncryptor {
    val sharedSecret = X25519KeyAgreement.agree(myPrivateKey, peerPublicKey)
    val derivedKey = X25519KeyAgreement.deriveKey(sharedSecret, context)
    return AesGcmEncryptor(derivedKey)
}
