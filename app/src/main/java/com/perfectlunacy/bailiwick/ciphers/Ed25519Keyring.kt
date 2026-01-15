package com.perfectlunacy.bailiwick.ciphers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.perfectlunacy.bailiwick.signatures.Ed25519Signer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom
import java.util.Base64

/**
 * Manages the device's Ed25519 keypair for identity and signing.
 * X25519 keys for encryption are derived from Ed25519 keys.
 *
 * Key sizes:
 * - Ed25519 public key: 32 bytes
 * - Ed25519 private key: 32 bytes
 * - X25519 public key: 32 bytes (derived)
 * - X25519 private key: 32 bytes (derived)
 */
class Ed25519Keyring private constructor(
    val publicKey: Ed25519PublicKeyParameters,
    val privateKey: Ed25519PrivateKeyParameters
) {
    companion object {
        private const val TAG = "Ed25519Keyring"
        private const val PREFS_NAME = "ed25519_keyring"
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_TOPIC = "topic_key"

        /**
         * Initialize the device keyring, creating keys if they don't exist.
         */
        fun create(context: Context): Ed25519Keyring {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val existingPrivate = prefs.getString(KEY_PRIVATE, null)
            val existingPublic = prefs.getString(KEY_PUBLIC, null)

            return if (existingPrivate != null && existingPublic != null) {
                Log.i(TAG, "Loading existing Ed25519 keypair")
                loadKeyPair(existingPrivate, existingPublic)
            } else {
                Log.i(TAG, "Generating new Ed25519 keypair")
                val keyring = generateKeyPair()
                saveKeyPair(prefs, keyring)
                keyring
            }
        }

        /**
         * Get or create a topic key for Gossip subscriptions.
         * The topic key is a random 32-byte key shared with peers during introduction.
         */
        fun getOrCreateTopicKey(context: Context): ByteArray {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingKey = prefs.getString(KEY_TOPIC, null)

            return if (existingKey != null) {
                Base64.getDecoder().decode(existingKey)
            } else {
                Log.i(TAG, "Generating new topic key")
                val newKey = ByteArray(32)
                SecureRandom().nextBytes(newKey)
                prefs.edit()
                    .putString(KEY_TOPIC, Base64.getEncoder().encodeToString(newKey))
                    .apply()
                newKey
            }
        }

        /**
         * Get the topic key as a Base64-encoded string for sharing.
         */
        fun getTopicKeyString(context: Context): String {
            return Base64.getEncoder().encodeToString(getOrCreateTopicKey(context))
        }

        /**
         * Create a keyring from existing key bytes (for import).
         */
        fun fromPrivateKey(privateKeyBytes: ByteArray): Ed25519Keyring {
            val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val publicKey = privateKey.generatePublicKey()
            return Ed25519Keyring(publicKey, privateKey)
        }

        private fun generateKeyPair(): Ed25519Keyring {
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keyPair = generator.generateKeyPair()

            val privateKey = keyPair.private as Ed25519PrivateKeyParameters
            val publicKey = keyPair.public as Ed25519PublicKeyParameters

            return Ed25519Keyring(publicKey, privateKey)
        }

        private fun loadKeyPair(privateB64: String, publicB64: String): Ed25519Keyring {
            val privateBytes = Base64.getDecoder().decode(privateB64)
            val publicBytes = Base64.getDecoder().decode(publicB64)

            val privateKey = Ed25519PrivateKeyParameters(privateBytes, 0)
            val publicKey = Ed25519PublicKeyParameters(publicBytes, 0)

            return Ed25519Keyring(publicKey, privateKey)
        }

        private fun saveKeyPair(prefs: SharedPreferences, keyring: Ed25519Keyring) {
            val privateB64 = Base64.getEncoder().encodeToString(keyring.privateKey.encoded)
            val publicB64 = Base64.getEncoder().encodeToString(keyring.publicKey.encoded)

            prefs.edit()
                .putString(KEY_PRIVATE, privateB64)
                .putString(KEY_PUBLIC, publicB64)
                .apply()

            Log.i(TAG, "Saved Ed25519 keypair to preferences")
        }
    }

    /**
     * Get the public key as a 32-byte array.
     */
    fun getPublicKeyBytes(): ByteArray = publicKey.encoded

    /**
     * Get the private key as a 32-byte array.
     */
    fun getPrivateKeyBytes(): ByteArray = privateKey.encoded

    /**
     * Get the Base64-encoded public key string for sharing (44 characters).
     */
    fun getPublicKeyString(): String = Base64.getEncoder().encodeToString(publicKey.encoded)

    /**
     * Get the X25519 public key derived from Ed25519 for key agreement.
     */
    fun getX25519PublicKey(): ByteArray = X25519KeyAgreement.ed25519PublicToX25519(publicKey)

    /**
     * Get the X25519 private key derived from Ed25519 for key agreement.
     */
    fun getX25519PrivateKey(): ByteArray = X25519KeyAgreement.ed25519PrivateToX25519(privateKey)

    /**
     * Create a signer using this keyring.
     */
    fun signer(): Ed25519Signer = Ed25519Signer(privateKey, publicKey)

    /**
     * Perform X25519 key agreement with a peer's public key.
     * Returns a shared secret that can be used to derive encryption keys.
     */
    fun keyAgreement(peerPublicKey: ByteArray): ByteArray {
        return X25519KeyAgreement.agree(getX25519PrivateKey(), peerPublicKey)
    }

    /**
     * Derive an AES-256 key for encrypting data to/from a specific peer.
     */
    fun deriveEncryptionKey(peerPublicKey: ByteArray, context: String = "encryption"): ByteArray {
        val sharedSecret = keyAgreement(peerPublicKey)
        return X25519KeyAgreement.deriveKey(sharedSecret, context)
    }
}
