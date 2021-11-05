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
        val plaintext = ciphers.mapNotNull { cipher ->
            try {
                val plainText = cipher.decrypt(data)
                if (validator.invoke(plainText)) {
                    plainText
                } else {
                    Log.d(TAG, "Decryption validation failure with cipher $cipher")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failure with cipher $cipher: ${e.message}")
                null
            }

        }.firstOrNull()

        if(plaintext==null) {
            Log.w(TAG,"Could not decrypt data")
        } else {
            Log.i(TAG, "Decryption succeeded")
        }

        return plaintext ?: byteArrayOf()
    }

}