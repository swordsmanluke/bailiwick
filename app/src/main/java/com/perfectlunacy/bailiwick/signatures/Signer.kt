package com.perfectlunacy.bailiwick.signatures

interface Signer {
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray): Boolean
}