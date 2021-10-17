package com.perfectlunacy.bailiwick.ciphers

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESEncryptor(private val key: SecretKey) {
    fun encrypt(data: ByteArray, IV: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val ivSpec = IvParameterSpec(IV)
        c.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        return c.doFinal(data)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val IV = data.sliceArray(0..16)
        val c = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        val keySpec = SecretKeySpec(key.encoded, "AES")
        val ivSpec = IvParameterSpec(IV)
        c.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        return c.doFinal(data.sliceArray(16..data.size))
    }
}