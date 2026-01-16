package com.perfectlunacy.bailiwick.ciphers

import android.util.Log
import java.lang.RuntimeException

class MultiCipher(val ciphers: List<Encryptor>, val validator: (ByteArray)->Boolean): Encryptor {
    companion object {
        const val TAG = "MultiCipher"
    }
    override fun encrypt(data: ByteArray): ByteArray {
        throw RuntimeException("MultiCipher can only be used for decryption")
    }

    override fun decrypt(data: ByteArray): ByteArray {
        Log.d(TAG, "Attempting decryption of ${data.size} bytes with ${ciphers.size} ciphers")
        val plaintext = ciphers.mapNotNull { cipher ->
            try {
                val plainText = cipher.decrypt(data)
                // Reject empty results immediately
                if (plainText.isEmpty()) {
                    Log.d(TAG, "Cipher ${cipher.javaClass.simpleName}: returned empty, skipping")
                    return@mapNotNull null
                }
                Log.d(TAG, "Cipher ${cipher.javaClass.simpleName}: input=${data.size}, output=${plainText.size}, first4=${plainText.take(4).map { "%02x".format(it) }}")
                if (valid(plainText)) {
                    Log.i(TAG, "CIPHER SUCCESS: ${cipher.javaClass.simpleName} produced ${plainText.size} bytes")
                    plainText
                } else {
                    Log.d(TAG, "Decryption validation failure with cipher ${cipher.javaClass.simpleName}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failure with cipher ${cipher.javaClass.simpleName}: ${e.message}")
                null
            }

        }.firstOrNull()

        if(plaintext==null) {
            Log.w(TAG,"Could not decrypt data")
        } else {
            Log.i(TAG, "Decryption succeeded with ${plaintext.size} bytes")
        }

        return plaintext ?: byteArrayOf()
    }

    private fun valid(plainText: ByteArray): Boolean {
        return try {
            validator.invoke(plainText)
        } catch(e: java.lang.Exception) {
            false
        }
    }

}