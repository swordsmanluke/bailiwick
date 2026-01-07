package com.perfectlunacy.bailiwick

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Manages the device's RSA keypair for identity and encryption.
 * Keys are stored in Android KeyStore for security.
 */
class DeviceKeyring private constructor(
    val publicKey: PublicKey,
    val privateKey: PrivateKey
) {
    companion object {
        private const val TAG = "DeviceKeyring"
        private const val KEY_ALIAS = "bailiwick_device_key"
        private const val KEYSTORE_TYPE = "AndroidKeyStore"

        /**
         * Initialize the device keyring, creating keys if they don't exist.
         */
        fun create(context: Context): DeviceKeyring {
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            keyStore.load(null)

            val keyPair = if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.i(TAG, "Loading existing device keypair")
                val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
                val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
                KeyPair(publicKey, privateKey)
            } else {
                Log.i(TAG, "Generating new device keypair")
                generateKeyPair()
            }

            return DeviceKeyring(keyPair.public, keyPair.private)
        }

        private fun generateKeyPair(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                KEYSTORE_TYPE
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT or
                    KeyProperties.PURPOSE_SIGN or
                    KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(2048)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .build()

            keyPairGenerator.initialize(spec)
            return keyPairGenerator.generateKeyPair()
        }

        /**
         * Get the Base64-encoded public key string for sharing.
         */
        fun getPublicKeyString(publicKey: PublicKey): String {
            return java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
        }
    }
}
