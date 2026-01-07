package com.perfectlunacy.bailiwick.signatures

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator

class Md5SignatureTest {

    private val signer = Md5Signature()

    @Test
    fun `sign produces 16 byte hash`() {
        val data = "Test data".toByteArray()

        val signature = signer.sign(data)

        assertEquals(16, signature.size)
    }

    @Test
    fun `sign is deterministic`() {
        val data = "Same data".toByteArray()

        val sig1 = signer.sign(data)
        val sig2 = signer.sign(data)

        assertArrayEquals(sig1, sig2)
    }

    @Test
    fun `different data produces different signatures`() {
        val sig1 = signer.sign("Data A".toByteArray())
        val sig2 = signer.sign("Data B".toByteArray())

        assertFalse(sig1.contentEquals(sig2))
    }

    @Test
    fun `verify returns true for valid signature`() {
        val data = "Message to sign".toByteArray()
        val signature = signer.sign(data)

        assertTrue(signer.verify(data, signature))
    }

    @Test
    fun `verify returns false for wrong data`() {
        val data = "Original message".toByteArray()
        val signature = signer.sign(data)

        assertFalse(signer.verify("Modified message".toByteArray(), signature))
    }

    @Test
    fun `verify returns false for wrong signature`() {
        val data = "Message".toByteArray()
        val wrongSignature = ByteArray(16) { 0 }

        assertFalse(signer.verify(data, wrongSignature))
    }

    @Test
    fun `empty data can be signed`() {
        val signature = signer.sign(byteArrayOf())

        assertEquals(16, signature.size)
        assertTrue(signer.verify(byteArrayOf(), signature))
    }
}

class Sha1SignatureTest {

    private val signer = Sha1Signature()

    @Test
    fun `sign produces 20 byte hash`() {
        val data = "Test data".toByteArray()

        val signature = signer.sign(data)

        assertEquals(20, signature.size)
    }

    @Test
    fun `sign is deterministic`() {
        val data = "Same data".toByteArray()

        val sig1 = signer.sign(data)
        val sig2 = signer.sign(data)

        assertArrayEquals(sig1, sig2)
    }

    @Test
    fun `different data produces different signatures`() {
        val sig1 = signer.sign("Data A".toByteArray())
        val sig2 = signer.sign("Data B".toByteArray())

        assertFalse(sig1.contentEquals(sig2))
    }

    @Test
    fun `verify returns true for valid signature`() {
        val data = "Message to sign".toByteArray()
        val signature = signer.sign(data)

        assertTrue(signer.verify(data, signature))
    }

    @Test
    fun `verify returns false for wrong data`() {
        val data = "Original message".toByteArray()
        val signature = signer.sign(data)

        assertFalse(signer.verify("Modified message".toByteArray(), signature))
    }

    @Test
    fun `verify returns false for wrong signature`() {
        val data = "Message".toByteArray()
        val wrongSignature = ByteArray(20) { 0 }

        assertFalse(signer.verify(data, wrongSignature))
    }
}

class RsaSignatureTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.genKeyPair()

    private val signer = RsaSignature(keyPair.public, keyPair.private)

    @Test
    fun `sign produces signature`() {
        val data = "Test message".toByteArray()

        val signature = signer.sign(data)

        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `verify returns true for valid signature`() {
        val data = "Message to sign".toByteArray()
        val signature = signer.sign(data)

        assertTrue(signer.verify(data, signature))
    }

    @Test
    fun `verify returns false for wrong data`() {
        val data = "Original message".toByteArray()
        val signature = signer.sign(data)

        assertFalse(signer.verify("Modified message".toByteArray(), signature))
    }

    @Test
    fun `verify returns false for wrong signature`() {
        val data = "Message".toByteArray()
        val wrongSignature = ByteArray(256) { 0 }

        assertFalse(signer.verify(data, wrongSignature))
    }

    @Test
    fun `different keys produce different signatures`() {
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.genKeyPair()
        val otherSigner = RsaSignature(otherKeyPair.public, otherKeyPair.private)

        val data = "Same message".toByteArray()
        val sig1 = signer.sign(data)
        val sig2 = otherSigner.sign(data)

        assertFalse(sig1.contentEquals(sig2))
    }

    @Test
    fun `signature from one key cannot be verified with another`() {
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.genKeyPair()
        val otherSigner = RsaSignature(otherKeyPair.public, otherKeyPair.private)

        val data = "Secret message".toByteArray()
        val signature = signer.sign(data)

        // Other signer should not be able to verify our signature
        assertFalse(otherSigner.verify(data, signature))
    }

    @Test
    fun `empty message can be signed and verified`() {
        val signature = signer.sign(byteArrayOf())

        assertTrue(signer.verify(byteArrayOf(), signature))
    }

    @Test
    fun `long message can be signed`() {
        val longMessage = ByteArray(10000) { it.toByte() }
        val signature = signer.sign(longMessage)

        assertTrue(signer.verify(longMessage, signature))
    }
}
