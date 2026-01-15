package com.perfectlunacy.bailiwick.signatures

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer as BouncyCastleEd25519Signer
import java.util.Base64

/**
 * Ed25519 signature implementation.
 *
 * Ed25519 provides:
 * - 128-bit security level
 * - 64-byte signatures
 * - Fast signing and verification
 * - Deterministic signatures (no random nonce needed)
 */
class Ed25519Signer(
    private val privateKey: Ed25519PrivateKeyParameters,
    private val publicKey: Ed25519PublicKeyParameters
) : Signer {

    override fun sign(data: ByteArray): ByteArray {
        val signer = BouncyCastleEd25519Signer()
        signer.init(true, privateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verify(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = BouncyCastleEd25519Signer()
            verifier.init(false, publicKey)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sign data and return Base64-encoded signature.
     */
    fun signToString(data: ByteArray): String {
        return Base64.getEncoder().encodeToString(sign(data))
    }

    /**
     * Verify a Base64-encoded signature.
     */
    fun verifyFromString(data: ByteArray, signatureB64: String): Boolean {
        return try {
            val signature = Base64.getDecoder().decode(signatureB64)
            verify(data, signature)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /**
         * Create a verifier from a public key (for verifying signatures from peers).
         */
        fun verifierFromPublicKey(publicKeyBytes: ByteArray): Ed25519Verifier {
            val publicKey = Ed25519PublicKeyParameters(publicKeyBytes, 0)
            return Ed25519Verifier(publicKey)
        }

        /**
         * Create a verifier from a Base64-encoded public key.
         */
        fun verifierFromPublicKeyString(publicKeyB64: String): Ed25519Verifier {
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyB64)
            return verifierFromPublicKey(publicKeyBytes)
        }
    }
}

/**
 * Ed25519 verifier for checking signatures from peers.
 * Only has public key, cannot sign.
 */
class Ed25519Verifier(private val publicKey: Ed25519PublicKeyParameters) {

    fun verify(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = BouncyCastleEd25519Signer()
            verifier.init(false, publicKey)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    fun verifyFromString(data: ByteArray, signatureB64: String): Boolean {
        return try {
            val signature = Base64.getDecoder().decode(signatureB64)
            verify(data, signature)
        } catch (e: Exception) {
            false
        }
    }
}
