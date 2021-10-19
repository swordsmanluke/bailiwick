package com.perfectlunacy.bailiwick.ciphers

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESEncryptor(private val key: SecretKey): Encryptor {
    override fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val c = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val ivSpec = IvParameterSpec(iv)
        c.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        return ivSpec.iv + c.doFinal(data)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        val IV = data.sliceArray(0..15)
        val c = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val ivSpec = IvParameterSpec(IV)
        c.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        return c.doFinal(data.sliceArray(16 until data.size))
    }
}