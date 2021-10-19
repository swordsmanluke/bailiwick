package com.perfectlunacy.bailiwick.ciphers

import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

/***
 * An "Encryptor" that doesn't do anything. Used to meet the interface when publishing public data
 */

class NoopEncryptor: Encryptor {
    override fun encrypt(data: ByteArray): ByteArray {
        return data
    }

    override fun decrypt(data: ByteArray): ByteArray {
        return data
    }
}