package com.perfectlunacy.bailiwick.ciphers

import org.junit.Assert.*
import org.junit.Test

class NoopEncryptorTest {

    private val encryptor = NoopEncryptor()

    @Test
    fun `encrypt returns same data`() {
        val data = "Hello, World!".toByteArray()

        val encrypted = encryptor.encrypt(data)

        assertArrayEquals(data, encrypted)
    }

    @Test
    fun `decrypt returns same data`() {
        val data = "Secret message".toByteArray()

        val decrypted = encryptor.decrypt(data)

        assertArrayEquals(data, decrypted)
    }

    @Test
    fun `encrypt and decrypt are symmetric`() {
        val original = "Test data for encryption".toByteArray()

        val encrypted = encryptor.encrypt(original)
        val decrypted = encryptor.decrypt(encrypted)

        assertArrayEquals(original, decrypted)
    }

    @Test
    fun `empty array works`() {
        val empty = byteArrayOf()

        assertArrayEquals(empty, encryptor.encrypt(empty))
        assertArrayEquals(empty, encryptor.decrypt(empty))
    }

    @Test
    fun `binary data works`() {
        val binary = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())

        assertArrayEquals(binary, encryptor.encrypt(binary))
        assertArrayEquals(binary, encryptor.decrypt(binary))
    }
}
