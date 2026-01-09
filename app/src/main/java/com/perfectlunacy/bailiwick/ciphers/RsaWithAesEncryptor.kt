package com.perfectlunacy.bailiwick.ciphers

import android.util.Log
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

class RsaWithAesEncryptor(private val privateKey: PrivateKey?, private val publicKey: PublicKey?): Encryptor {
    override fun encrypt(data: ByteArray): ByteArray {
        // RSA doesn't "do" large blocks of plaintext. Instead, encrypt the message with AES
        // and send the RSA-encrypted-key along with it.
        val key = KeyGenerator.getInstance("AES").generateKey()
        val aes = AESEncryptor(key)

        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, publicKey)

        val encKey = rsa.doFinal(key.encoded)
        val ciphertext = aes.encrypt(data)

        return encKey + ciphertext
    }

    override fun decrypt(data: ByteArray): ByteArray {
        Log.d(TAG, "Attempting to decrypt ${data.size} bytes")
        if(data.size < 256 + 32) { // rsa + aes iv + aes block
            Log.d(TAG, "Provided block is too small for RSA + AES.")
            return byteArrayOf()
        }
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKey = data.sliceArray(0..255)
        val key = SecretKeySpec(rsa.doFinal(aesKey), "AES")
        val aes = AESEncryptor(key)

        return aes.decrypt(data.sliceArray(256 until data.size))
    }

    companion object {
        const val TAG = "RsaWithAesEncryptor"
    }
}