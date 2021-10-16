package com.perfectlunacy.bailiwick.signatures

import java.security.MessageDigest

class Sha1Signature {
    fun sign(text: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(text)
        return md.digest()
    }

    fun verify(text: ByteArray, hash: ByteArray): Boolean {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(text)
        return hash.contentEquals(md.digest())
    }
}