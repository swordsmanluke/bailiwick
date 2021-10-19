package com.perfectlunacy.bailiwick.ciphers

interface Encryptor {
    fun encrypt(data: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}