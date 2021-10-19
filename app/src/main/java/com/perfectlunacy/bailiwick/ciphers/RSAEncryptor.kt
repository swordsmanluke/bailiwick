package com.perfectlunacy.bailiwick.ciphers

import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

class RSAEncryptor(private val privateKey: PrivateKey, private val publicKey: PublicKey): Encryptor {
    override fun encrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA")
        c.init(Cipher.ENCRYPT_MODE, publicKey)

        return c.doFinal(data)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA")
        c.init(Cipher.DECRYPT_MODE, privateKey)

        return c.doFinal(data)
    }
}