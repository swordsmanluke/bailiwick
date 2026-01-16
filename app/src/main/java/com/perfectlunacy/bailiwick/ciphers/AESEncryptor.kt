package com.perfectlunacy.bailiwick.ciphers

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESEncryptor(private val key: SecretKey): Encryptor {
    // Get first 4 bytes of key for identification in logs
    private val keyId: String by lazy {
        try {
            key.encoded?.take(4)?.map { "%02x".format(it) }?.joinToString("") ?: "hardware-backed"
        } catch (e: Exception) {
            "hardware-backed"  // AndroidKeyStore keys can't export encoded bytes
        }
    }

    override fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val c = Cipher.getInstance("AES/CBC/PKCS7PADDING")
        c.init(Cipher.ENCRYPT_MODE, key)

        val result = c.iv + c.doFinal(data)
        Log.d(TAG, "ENCRYPT: keyId=$keyId, input=${data.size}, output=${result.size}")
        return result
    }

    override fun decrypt(data: ByteArray): ByteArray {
        Log.d(TAG, "DECRYPT START: keyId=$keyId, input=${data.size} bytes")

        if(data.size % 16 != 0) {
            Log.w(TAG, "DECRYPT FAIL: keyId=$keyId, block size wrong (${data.size} not divisible by 16)")
            return byteArrayOf()
        }

        val IV = data.sliceArray(0..15)
        val c = Cipher.getInstance("AES/CBC/PKCS7PADDING")
        val ivSpec = IvParameterSpec(IV)

        try {
            c.init(Cipher.DECRYPT_MODE, key, ivSpec)
        } catch (e: Exception) {
            Log.e(TAG, "DECRYPT FAIL: keyId=$keyId, cipher init failed: ${e.message}")
            return byteArrayOf()
        }

        val result = try {
            c.doFinal(data.sliceArray(16 until data.size))
        } catch (e: Exception) {
            Log.e(TAG, "DECRYPT FAIL: keyId=$keyId, doFinal failed: ${e.message}")
            return byteArrayOf()
        }

        // Sanity check: decrypted size should be close to (input - 16 bytes IV - up to 16 bytes padding)
        // If output is way smaller than expected, the key is wrong and padding was misinterpreted
        val expectedMinSize = data.size - 32  // IV + max padding
        if (result.size < expectedMinSize * 0.9) {  // Allow 10% tolerance
            Log.w(TAG, "DECRYPT FAIL: keyId=$keyId, size mismatch: input=${data.size}, output=${result.size}, expected>=${expectedMinSize}")
            return byteArrayOf()  // Reject - likely wrong key causing bad padding interpretation
        }

        Log.d(TAG, "DECRYPT OK: keyId=$keyId, input=${data.size}, output=${result.size}, first4=${result.take(4).map { "%02x".format(it) }}")
        return result
    }

    companion object {
        const val TAG = "AESEncryptor"

        /**
         * Creates an AESEncryptor from a password string.
         * Uses MD5 to derive a 128-bit key from the password.
         *
         * Note: MD5 is used for backwards compatibility. For new implementations,
         * consider using a proper key derivation function like PBKDF2.
         */
        fun fromPassword(password: String): AESEncryptor {
            val md = MessageDigest.getInstance("MD5")
            md.update(password.toByteArray())
            val key = SecretKeySpec(md.digest(), "AES")
            return AESEncryptor(key)
        }
    }
}