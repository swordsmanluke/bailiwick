package com.perfectlunacy.bailiwick.ciphers

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.math.ec.rfc7748.X25519
import java.math.BigInteger
import java.security.MessageDigest

/**
 * X25519 key agreement (ECDH) for deriving shared secrets.
 *
 * This enables two parties to derive the same shared secret using their
 * private keys and each other's public keys, which can then be used
 * to derive symmetric encryption keys.
 *
 * Flow:
 * 1. Alice and Bob exchange X25519 public keys
 * 2. Alice computes: sharedSecret = ECDH(alice_private, bob_public)
 * 3. Bob computes: sharedSecret = ECDH(bob_private, alice_public)
 * 4. Both derive the same sharedSecret
 * 5. Use HKDF to derive AES keys from sharedSecret
 */
object X25519KeyAgreement {

    private const val KEY_SIZE = 32

    /**
     * Perform X25519 key agreement.
     *
     * @param myPrivateKey My X25519 private key (32 bytes)
     * @param peerPublicKey Peer's X25519 public key (32 bytes)
     * @return Shared secret (32 bytes)
     */
    fun agree(myPrivateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(myPrivateKey.size == KEY_SIZE) { "Private key must be 32 bytes" }
        require(peerPublicKey.size == KEY_SIZE) { "Public key must be 32 bytes" }

        val privateKeyParams = X25519PrivateKeyParameters(myPrivateKey, 0)
        val publicKeyParams = X25519PublicKeyParameters(peerPublicKey, 0)

        val agreement = X25519Agreement()
        agreement.init(privateKeyParams)

        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicKeyParams, sharedSecret, 0)

        return sharedSecret
    }

    /**
     * Derive an AES-256 key from a shared secret using HKDF-SHA256.
     *
     * @param sharedSecret The ECDH shared secret
     * @param context A context string for domain separation (e.g., "manifest", "circle-123")
     * @param salt Optional salt for HKDF (defaults to empty)
     * @return 32-byte AES-256 key
     */
    fun deriveKey(sharedSecret: ByteArray, context: String, salt: ByteArray = ByteArray(0)): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val params = HKDFParameters(sharedSecret, salt, context.toByteArray())
        hkdf.init(params)

        val derivedKey = ByteArray(32)
        hkdf.generateBytes(derivedKey, 0, 32)
        return derivedKey
    }

    /**
     * Derive multiple keys from a shared secret.
     *
     * @param sharedSecret The ECDH shared secret
     * @param contexts List of context strings for each key
     * @return Map of context -> derived key
     */
    fun deriveKeys(sharedSecret: ByteArray, contexts: List<String>): Map<String, ByteArray> {
        return contexts.associateWith { context -> deriveKey(sharedSecret, context) }
    }

    /**
     * Convert Ed25519 private key to X25519 private key.
     *
     * The conversion uses SHA-512 hash of the Ed25519 seed, taking the first 32 bytes
     * and applying the X25519 clamping operation.
     */
    fun ed25519PrivateToX25519(ed25519Private: Ed25519PrivateKeyParameters): ByteArray {
        // Get the seed (private key bytes)
        val seed = ed25519Private.encoded

        // Hash with SHA-512
        val digest = MessageDigest.getInstance("SHA-512")
        val hash = digest.digest(seed)

        // Take first 32 bytes and apply X25519 clamping
        val x25519Private = hash.copyOf(32)
        clampX25519PrivateKey(x25519Private)

        return x25519Private
    }

    /**
     * Convert Ed25519 public key to X25519 public key.
     *
     * This is a mathematically defined conversion from the Ed25519 curve point
     * to the equivalent X25519 curve point.
     */
    fun ed25519PublicToX25519(ed25519Public: Ed25519PublicKeyParameters): ByteArray {
        val edPoint = ed25519Public.encoded
        val x25519Public = ByteArray(32)

        // Use BouncyCastle's conversion
        convertEdwardsToMontgomery(edPoint, x25519Public)

        return x25519Public
    }

    /**
     * Apply X25519 key clamping to a private key.
     * This ensures the key is valid for X25519 operations.
     */
    private fun clampX25519PrivateKey(key: ByteArray) {
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
    }

    /**
     * Convert Edwards curve point (Ed25519) to Montgomery curve point (X25519).
     *
     * The formula is: u = (1 + y) / (1 - y) mod p
     * where y is the Edwards y-coordinate and u is the Montgomery u-coordinate.
     */
    private fun convertEdwardsToMontgomery(edwardsPoint: ByteArray, montgomeryPoint: ByteArray) {
        // BouncyCastle provides this conversion internally
        // We use a simplified approach: derive X25519 keypair from Ed25519 private
        // For public key conversion, we need to compute the curve point conversion

        // The Ed25519 public key is encoded as the y-coordinate with sign bit
        // We need to convert to X25519 u-coordinate

        // Use BouncyCastle's internal functions via X25519
        // Create a temporary private key to generate the corresponding public
        // This is a workaround since BC doesn't expose direct conversion

        // For now, use the standard conversion algorithm
        // u = (1 + y) / (1 - y) in the field

        // Since BouncyCastle's Ed25519 and X25519 use the same underlying field,
        // we can use field arithmetic. However, this is complex.

        // Simpler approach: The X25519 public key can be derived from the X25519 private key
        // which we already derive from Ed25519 private key.
        // For verification purposes, we accept Ed25519 public keys and derive X25519 ourselves.

        // This implementation uses the birational map between curves
        val y = ByteArray(32)
        System.arraycopy(edwardsPoint, 0, y, 0, 32)

        // Clear the sign bit to get pure y coordinate
        y[31] = (y[31].toInt() and 0x7F).toByte()

        // Compute u = (1 + y) / (1 - y) mod p
        // This requires field arithmetic in GF(2^255 - 19)
        computeMontgomeryU(y, montgomeryPoint)
    }

    /**
     * Compute Montgomery u-coordinate from Edwards y-coordinate.
     * u = (1 + y) / (1 - y) mod p, where p = 2^255 - 19
     */
    private fun computeMontgomeryU(y: ByteArray, u: ByteArray) {
        // The field prime for Curve25519: p = 2^255 - 19
        val p = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
        
        // Convert y from little-endian bytes to BigInteger
        val yReversed = y.reversedArray()
        val yInt = BigInteger(1, yReversed)
        
        // Compute u = (1 + y) / (1 - y) mod p
        // Division in a field is multiplication by modular inverse
        val one = BigInteger.ONE
        val numerator = one.add(yInt).mod(p)
        val denominator = one.subtract(yInt).mod(p)
        
        // Compute modular inverse of denominator
        val denominatorInv = denominator.modInverse(p)
        
        // u = numerator * denominatorInv mod p
        val uInt = numerator.multiply(denominatorInv).mod(p)
        
        // Convert back to little-endian bytes
        var uBytes = uInt.toByteArray()
        
        // Remove leading zero if present (BigInteger adds it for positive numbers)
        if (uBytes.size > 32 && uBytes[0] == 0.toByte()) {
            uBytes = uBytes.copyOfRange(1, uBytes.size)
        }
        
        // Reverse to little-endian and pad to 32 bytes
        uBytes = uBytes.reversedArray()
        
        // Copy to output, padding with zeros if needed
        u.fill(0)
        System.arraycopy(uBytes, 0, u, 0, minOf(uBytes.size, 32))
    }

    /**
     * Generate a new X25519 keypair (for standalone use without Ed25519).
     */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val privateKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(privateKey)
        clampX25519PrivateKey(privateKey)

        val publicKey = ByteArray(32)
        X25519.generatePublicKey(privateKey, 0, publicKey, 0)

        return Pair(privateKey, publicKey)
    }

    /**
     * Derive X25519 public key from X25519 private key.
     */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE) { "Private key must be 32 bytes" }
        val publicKey = ByteArray(32)
        X25519.generatePublicKey(privateKey, 0, publicKey, 0)
        return publicKey
    }
}
