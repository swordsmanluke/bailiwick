package com.perfectlunacy.bailiwick.ciphers

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.SecureRandom

class RSAEncryptorTest {

    @Test
    fun testCipherWorks() {
        val keyGen = KeyPairGenerator.getInstance("RSA");
        val random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(2048, random);
        val pair = keyGen.genKeyPair()

        val cipher = RSAEncryptor(pair.private, pair.public)
        val data = "Test message".toByteArray()
        val ciphered = cipher.encrypt(data)
        val deciphered = cipher.decrypt(ciphered)

        assertEquals(String(data), String(deciphered))
        assertNotEquals(String(data), String(ciphered))
    }

}