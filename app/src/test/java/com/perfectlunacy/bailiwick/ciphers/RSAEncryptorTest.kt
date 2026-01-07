package com.perfectlunacy.bailiwick.ciphers

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security

class RSAEncryptorTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            // Register Bouncy Castle for PKCS7PADDING support in JVM tests
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun testCipherWorks() {
        val keyGen = KeyPairGenerator.getInstance("RSA");
        val random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(2048, random);
        val pair = keyGen.genKeyPair()

        val cipher = RsaWithAesEncryptor(pair.private, pair.public)
        val data = "Test message".toByteArray()
        val ciphered = cipher.encrypt(data)
        val deciphered = cipher.decrypt(ciphered)

        assertEquals(String(data), String(deciphered))
        assertNotEquals(String(data), String(ciphered))
    }

}