package com.perfectlunacy.bailiwick.crypto

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.AesGcmEncryptor
import com.perfectlunacy.bailiwick.ciphers.Ed25519Keyring
import com.perfectlunacy.bailiwick.ciphers.X25519KeyAgreement
import com.perfectlunacy.bailiwick.ciphers.fromKeyAgreement
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.util.Base64

/**
 * Utility for encrypting and decrypting circle keys during peer key exchange.
 *
 * Uses X25519 ECDH key agreement to derive a shared secret, then AES-GCM
 * for authenticated encryption of the circle key.
 */
object KeyEncryption {
    private const val TAG = "KeyEncryption"
    private const val KEY_EXCHANGE_CONTEXT = "circle-key-exchange"

    /**
     * Encrypt a circle key for a specific peer using X25519 key agreement.
     *
     * @param circleKey The raw AES key bytes to encrypt (typically 16 or 32 bytes)
     * @param myKeyring Our Ed25519Keyring for deriving X25519 private key
     * @param peerEd25519PublicKey The peer's Ed25519 public key (32 bytes)
     * @return Base64-encoded encrypted key
     */
    fun encryptKeyForPeer(
        circleKey: ByteArray,
        myKeyring: Ed25519Keyring,
        peerEd25519PublicKey: ByteArray
    ): String {
        require(peerEd25519PublicKey.size == 32) {
            "Peer Ed25519 public key must be 32 bytes, got ${peerEd25519PublicKey.size}"
        }

        // Convert peer's Ed25519 public key to X25519
        val peerEd25519Params = Ed25519PublicKeyParameters(peerEd25519PublicKey, 0)
        val peerX25519Public = X25519KeyAgreement.ed25519PublicToX25519(peerEd25519Params)

        // Log key details for debugging
        val myX25519Private = myKeyring.getX25519PrivateKey()
        Log.d(TAG, "ENCRYPT KEY: circleKey=${circleKey.size} bytes, first4=${circleKey.take(4).map { "%02x".format(it) }}")
        Log.d(TAG, "ENCRYPT KEY: peerEd25519Pub=${peerEd25519PublicKey.take(8).map { "%02x".format(it) }}")
        Log.d(TAG, "ENCRYPT KEY: peerX25519Pub=${peerX25519Public.take(8).map { "%02x".format(it) }}")
        Log.d(TAG, "ENCRYPT KEY: myX25519Priv=${myX25519Private.take(8).map { "%02x".format(it) }}")

        // Create encryptor using key agreement
        val encryptor = AesGcmEncryptor.fromKeyAgreement(
            myX25519Private,
            peerX25519Public,
            KEY_EXCHANGE_CONTEXT
        )

        // Encrypt and Base64 encode
        val encrypted = encryptor.encrypt(circleKey)
        Log.d(TAG, "ENCRYPT KEY: encrypted=${encrypted.size} bytes")
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * Decrypt a circle key received from a peer.
     *
     * @param encryptedKeyB64 Base64-encoded encrypted key
     * @param myKeyring Our Ed25519Keyring for deriving X25519 private key
     * @param peerEd25519PublicKey The peer's Ed25519 public key (32 bytes)
     * @return The decrypted AES key bytes, or null on failure
     */
    fun decryptKeyFromPeer(
        encryptedKeyB64: String,
        myKeyring: Ed25519Keyring,
        peerEd25519PublicKey: ByteArray
    ): ByteArray? {
        if (peerEd25519PublicKey.size != 32) {
            Log.e(TAG, "Invalid peer public key size: ${peerEd25519PublicKey.size}")
            return null
        }

        return try {
            // Convert peer's Ed25519 public key to X25519
            val peerEd25519Params = Ed25519PublicKeyParameters(peerEd25519PublicKey, 0)
            val peerX25519Public = X25519KeyAgreement.ed25519PublicToX25519(peerEd25519Params)

            // Log key details for debugging
            val myX25519Private = myKeyring.getX25519PrivateKey()
            Log.d(TAG, "DECRYPT KEY: peerEd25519Pub=${peerEd25519PublicKey.take(8).map { "%02x".format(it) }}")
            Log.d(TAG, "DECRYPT KEY: peerX25519Pub=${peerX25519Public.take(8).map { "%02x".format(it) }}")
            Log.d(TAG, "DECRYPT KEY: myX25519Priv=${myX25519Private.take(8).map { "%02x".format(it) }}")

            // Create decryptor using key agreement (same shared secret as encryptor)
            val decryptor = AesGcmEncryptor.fromKeyAgreement(
                myX25519Private,
                peerX25519Public,
                KEY_EXCHANGE_CONTEXT
            )

            // Decode and decrypt
            val encrypted = Base64.getDecoder().decode(encryptedKeyB64)
            Log.d(TAG, "DECRYPT KEY: encrypted=${encrypted.size} bytes")
            val decrypted = decryptor.decrypt(encrypted)

            if (decrypted.isEmpty()) {
                Log.w(TAG, "DECRYPT KEY: Decryption produced empty result (auth tag mismatch)")
                null
            } else {
                Log.d(TAG, "DECRYPT KEY: SUCCESS - decrypted=${decrypted.size} bytes, first4=${decrypted.take(4).map { "%02x".format(it) }}")
                decrypted
            }
        } catch (e: Exception) {
            Log.e(TAG, "DECRYPT KEY: Failed - ${e.message}", e)
            null
        }
    }
}
