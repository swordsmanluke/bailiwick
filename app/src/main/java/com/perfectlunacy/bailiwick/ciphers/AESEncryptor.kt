package com.perfectlunacy.bailiwick.ciphers

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESEncryptor(private val key: SecretKey): Encryptor {
    override fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val c = Cipher.getInstance("AES/CBC/PKCS7PADDING")
        c.init(Cipher.ENCRYPT_MODE, key)

        return c.iv + c.doFinal(data)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if(data.size % 16 != 0) {
            Log.d(TAG,"AES cannot decrypt - block size is wrong")
            return byteArrayOf()
        }

        val IV = data.sliceArray(0..15)
        val c = Cipher.getInstance("AES/CBC/PKCS7PADDING")
        val ivSpec = IvParameterSpec(IV)
        c.init(Cipher.DECRYPT_MODE, key, ivSpec)

        return c.doFinal(data.sliceArray(16 until data.size))
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