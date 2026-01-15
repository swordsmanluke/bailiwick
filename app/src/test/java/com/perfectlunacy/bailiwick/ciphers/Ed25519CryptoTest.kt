package com.perfectlunacy.bailiwick.ciphers

import com.perfectlunacy.bailiwick.signatures.Ed25519Signer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class Ed25519SignerTest {

    private fun generateKeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        return Pair(
            keyPair.private as Ed25519PrivateKeyParameters,
            keyPair.public as Ed25519PublicKeyParameters
        )
    }

    @Test
    fun `sign produces 64 byte signature`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Test message".toByteArray()

        val signature = signer.sign(data)

        assertEquals(64, signature.size)
    }

    @Test
    fun `sign is deterministic`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Same data".toByteArray()

        val sig1 = signer.sign(data)
        val sig2 = signer.sign(data)

        assertArrayEquals(sig1, sig2)
    }

    @Test
    fun `verify returns true for valid signature`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Message to sign".toByteArray()

        val signature = signer.sign(data)

        assertTrue(signer.verify(data, signature))
    }

    @Test
    fun `verify returns false for wrong data`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Original message".toByteArray()
        val signature = signer.sign(data)

        assertFalse(signer.verify("Modified message".toByteArray(), signature))
    }

    @Test
    fun `verify returns false for wrong signature`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Message".toByteArray()
        val wrongSignature = ByteArray(64) { 0 }

        assertFalse(signer.verify(data, wrongSignature))
    }

    @Test
    fun `different keys produce different signatures`() {
        val (privateKey1, publicKey1) = generateKeyPair()
        val (privateKey2, publicKey2) = generateKeyPair()
        val signer1 = Ed25519Signer(privateKey1, publicKey1)
        val signer2 = Ed25519Signer(privateKey2, publicKey2)

        val data = "Same message".toByteArray()
        val sig1 = signer1.sign(data)
        val sig2 = signer2.sign(data)

        assertFalse(sig1.contentEquals(sig2))
    }

    @Test
    fun `signature from one key cannot be verified with another`() {
        val (privateKey1, publicKey1) = generateKeyPair()
        val (privateKey2, publicKey2) = generateKeyPair()
        val signer1 = Ed25519Signer(privateKey1, publicKey1)
        val signer2 = Ed25519Signer(privateKey2, publicKey2)

        val data = "Secret message".toByteArray()
        val signature = signer1.sign(data)

        assertFalse(signer2.verify(data, signature))
    }

    @Test
    fun `signToString produces Base64 signature`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Test message".toByteArray()

        val signatureB64 = signer.signToString(data)

        // Base64 of 64 bytes = 88 characters (with padding)
        assertTrue(signatureB64.length in 86..88)
        // Should be decodable
        val decoded = Base64.getDecoder().decode(signatureB64)
        assertEquals(64, decoded.size)
    }

    @Test
    fun `verifyFromString works with Base64 signature`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Test message".toByteArray()

        val signatureB64 = signer.signToString(data)

        assertTrue(signer.verifyFromString(data, signatureB64))
    }

    @Test
    fun `public key is 32 bytes`() {
        val (_, publicKey) = generateKeyPair()

        assertEquals(32, publicKey.encoded.size)
    }

    @Test
    fun `private key is 32 bytes`() {
        val (privateKey, _) = generateKeyPair()

        assertEquals(32, privateKey.encoded.size)
    }

    @Test
    fun `verifier can verify without private key`() {
        val (privateKey, publicKey) = generateKeyPair()
        val signer = Ed25519Signer(privateKey, publicKey)
        val data = "Message".toByteArray()
        val signature = signer.sign(data)

        // Create verifier from public key only
        val verifier = Ed25519Signer.verifierFromPublicKey(publicKey.encoded)

        assertTrue(verifier.verify(data, signature))
    }
}

class X25519KeyAgreementTest {

    @Test
    fun `generateKeyPair produces 32 byte keys`() {
        val (privateKey, publicKey) = X25519KeyAgreement.generateKeyPair()

        assertEquals(32, privateKey.size)
        assertEquals(32, publicKey.size)
    }

    @Test
    fun `key agreement produces same secret for both parties`() {
        val (alicePrivate, alicePublic) = X25519KeyAgreement.generateKeyPair()
        val (bobPrivate, bobPublic) = X25519KeyAgreement.generateKeyPair()

        val aliceSecret = X25519KeyAgreement.agree(alicePrivate, bobPublic)
        val bobSecret = X25519KeyAgreement.agree(bobPrivate, alicePublic)

        assertArrayEquals(aliceSecret, bobSecret)
    }

    @Test
    fun `shared secret is 32 bytes`() {
        val (alicePrivate, _) = X25519KeyAgreement.generateKeyPair()
        val (_, bobPublic) = X25519KeyAgreement.generateKeyPair()

        val sharedSecret = X25519KeyAgreement.agree(alicePrivate, bobPublic)

        assertEquals(32, sharedSecret.size)
    }

    @Test
    fun `different key pairs produce different secrets`() {
        val (alicePrivate, alicePublic) = X25519KeyAgreement.generateKeyPair()
        val (bobPrivate, bobPublic) = X25519KeyAgreement.generateKeyPair()
        val (carolPrivate, carolPublic) = X25519KeyAgreement.generateKeyPair()

        val aliceBobSecret = X25519KeyAgreement.agree(alicePrivate, bobPublic)
        val aliceCarolSecret = X25519KeyAgreement.agree(alicePrivate, carolPublic)

        assertFalse(aliceBobSecret.contentEquals(aliceCarolSecret))
    }

    @Test
    fun `deriveKey produces 32 byte key`() {
        val sharedSecret = ByteArray(32) { it.toByte() }

        val derivedKey = X25519KeyAgreement.deriveKey(sharedSecret, "test")

        assertEquals(32, derivedKey.size)
    }

    @Test
    fun `deriveKey is deterministic`() {
        val sharedSecret = ByteArray(32) { it.toByte() }

        val key1 = X25519KeyAgreement.deriveKey(sharedSecret, "context")
        val key2 = X25519KeyAgreement.deriveKey(sharedSecret, "context")

        assertArrayEquals(key1, key2)
    }

    @Test
    fun `different contexts produce different keys`() {
        val sharedSecret = ByteArray(32) { it.toByte() }

        val key1 = X25519KeyAgreement.deriveKey(sharedSecret, "context1")
        val key2 = X25519KeyAgreement.deriveKey(sharedSecret, "context2")

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun `publicFromPrivate derives correct public key`() {
        val (privateKey, publicKey) = X25519KeyAgreement.generateKeyPair()

        val derivedPublic = X25519KeyAgreement.publicFromPrivate(privateKey)

        assertArrayEquals(publicKey, derivedPublic)
    }

    @Test
    fun `ed25519 to x25519 private key conversion produces valid key`() {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val ed25519Private = keyPair.private as Ed25519PrivateKeyParameters

        val x25519Private = X25519KeyAgreement.ed25519PrivateToX25519(ed25519Private)

        assertEquals(32, x25519Private.size)
        // Key should be clamped (specific bits set/cleared)
        assertEquals(0, x25519Private[0].toInt() and 7) // Bottom 3 bits cleared
        assertEquals(64, x25519Private[31].toInt() and 64) // Bit 6 set
        assertEquals(0, x25519Private[31].toInt() and 128) // Top bit cleared
    }

    @Test
    fun `x25519 keys derived from ed25519 can perform key agreement`() {
        // Generate two Ed25519 keypairs
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))

        val aliceEd = generator.generateKeyPair()
        val bobEd = generator.generateKeyPair()

        val alicePrivate = aliceEd.private as Ed25519PrivateKeyParameters
        val bobPrivate = bobEd.private as Ed25519PrivateKeyParameters

        // Derive X25519 keys
        val aliceX25519Private = X25519KeyAgreement.ed25519PrivateToX25519(alicePrivate)
        val bobX25519Private = X25519KeyAgreement.ed25519PrivateToX25519(bobPrivate)

        val aliceX25519Public = X25519KeyAgreement.publicFromPrivate(aliceX25519Private)
        val bobX25519Public = X25519KeyAgreement.publicFromPrivate(bobX25519Private)

        // Both should derive the same shared secret
        val aliceSecret = X25519KeyAgreement.agree(aliceX25519Private, bobX25519Public)
        val bobSecret = X25519KeyAgreement.agree(bobX25519Private, aliceX25519Public)

        assertArrayEquals(aliceSecret, bobSecret)
    }
}

class AesGcmEncryptorTest {

    @Test
    fun `encrypt and decrypt round trip`() {
        val key = AesGcmEncryptor.generateKey()
        val encryptor = AesGcmEncryptor(key)
        val plaintext = "Hello, World!".toByteArray()

        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `ciphertext is larger than plaintext`() {
        val key = AesGcmEncryptor.generateKey()
        val encryptor = AesGcmEncryptor(key)
        val plaintext = "Test".toByteArray()

        val ciphertext = encryptor.encrypt(plaintext)

        // Ciphertext = 12 byte nonce + plaintext + 16 byte tag
        assertEquals(plaintext.size + 12 + 16, ciphertext.size)
    }

    @Test
    fun `different encryptions produce different ciphertexts`() {
        val key = AesGcmEncryptor.generateKey()
        val encryptor = AesGcmEncryptor(key)
        val plaintext = "Same message".toByteArray()

        val ciphertext1 = encryptor.encrypt(plaintext)
        val ciphertext2 = encryptor.encrypt(plaintext)

        // Different nonces should produce different ciphertexts
        assertFalse(ciphertext1.contentEquals(ciphertext2))
    }

    @Test
    fun `wrong key fails to decrypt`() {
        val key1 = AesGcmEncryptor.generateKey()
        val key2 = AesGcmEncryptor.generateKey()
        val encryptor1 = AesGcmEncryptor(key1)
        val encryptor2 = AesGcmEncryptor(key2)
        val plaintext = "Secret message".toByteArray()

        val ciphertext = encryptor1.encrypt(plaintext)
        val decrypted = encryptor2.decrypt(ciphertext)

        // Should return empty on auth failure
        assertTrue(decrypted.isEmpty())
    }

    @Test
    fun `tampered ciphertext fails to decrypt`() {
        val key = AesGcmEncryptor.generateKey()
        val encryptor = AesGcmEncryptor(key)
        val plaintext = "Secret message".toByteArray()

        val ciphertext = encryptor.encrypt(plaintext)
        // Tamper with the ciphertext
        ciphertext[20] = (ciphertext[20].toInt() xor 0xFF).toByte()

        val decrypted = encryptor.decrypt(ciphertext)

        assertTrue(decrypted.isEmpty())
    }

    @Test
    fun `empty plaintext can be encrypted`() {
        val key = AesGcmEncryptor.generateKey()
        val encryptor = AesGcmEncryptor(key)
        val plaintext = byteArrayOf()

        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `large plaintext can be encrypted`() {
        val key = AesGcmEncryptor.generateKey()
        val encryptor = AesGcmEncryptor(key)
        val plaintext = ByteArray(100000) { it.toByte() }

        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `generateKey produces 32 byte key`() {
        val key = AesGcmEncryptor.generateKey()

        assertEquals(32, key.size)
    }

    @Test
    fun `fromBase64Key works`() {
        val key = AesGcmEncryptor.generateKey()
        val keyB64 = Base64.getEncoder().encodeToString(key)

        val encryptor = AesGcmEncryptor.fromBase64Key(keyB64)
        val plaintext = "Test".toByteArray()

        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `withNewKey returns working encryptor and key`() {
        val (encryptor, key) = AesGcmEncryptor.withNewKey()
        val plaintext = "Test".toByteArray()

        val ciphertext = encryptor.encrypt(plaintext)

        // Create new encryptor with same key
        val encryptor2 = AesGcmEncryptor(key)
        val decrypted = encryptor2.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong key size throws exception`() {
        val wrongKey = ByteArray(16) // 128-bit instead of 256-bit
        AesGcmEncryptor(wrongKey)
    }
}

class KeyAgreementEncryptionTest {

    @Test
    fun `end to end encryption via key agreement`() {
        // Alice and Bob generate keypairs
        val (alicePrivate, alicePublic) = X25519KeyAgreement.generateKeyPair()
        val (bobPrivate, bobPublic) = X25519KeyAgreement.generateKeyPair()

        // Alice encrypts to Bob
        val aliceSharedSecret = X25519KeyAgreement.agree(alicePrivate, bobPublic)
        val aliceKey = X25519KeyAgreement.deriveKey(aliceSharedSecret, "message")
        val aliceEncryptor = AesGcmEncryptor(aliceKey)

        val plaintext = "Hello Bob, this is Alice!".toByteArray()
        val ciphertext = aliceEncryptor.encrypt(plaintext)

        // Bob decrypts from Alice
        val bobSharedSecret = X25519KeyAgreement.agree(bobPrivate, alicePublic)
        val bobKey = X25519KeyAgreement.deriveKey(bobSharedSecret, "message")
        val bobEncryptor = AesGcmEncryptor(bobKey)

        val decrypted = bobEncryptor.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `third party cannot decrypt`() {
        val (alicePrivate, alicePublic) = X25519KeyAgreement.generateKeyPair()
        val (bobPrivate, bobPublic) = X25519KeyAgreement.generateKeyPair()
        val (evePrivate, _) = X25519KeyAgreement.generateKeyPair()

        // Alice encrypts to Bob
        val aliceKey = X25519KeyAgreement.deriveKey(
            X25519KeyAgreement.agree(alicePrivate, bobPublic),
            "message"
        )
        val ciphertext = AesGcmEncryptor(aliceKey).encrypt("Secret".toByteArray())

        // Eve tries to decrypt with her key agreement with Alice
        val eveKey = X25519KeyAgreement.deriveKey(
            X25519KeyAgreement.agree(evePrivate, alicePublic),
            "message"
        )
        val decrypted = AesGcmEncryptor(eveKey).decrypt(ciphertext)

        assertTrue(decrypted.isEmpty())
    }
}
