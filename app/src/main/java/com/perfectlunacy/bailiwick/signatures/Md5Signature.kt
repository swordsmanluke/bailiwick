package com.perfectlunacy.bailiwick.signatures

import java.security.MessageDigest

class Md5Signature: Signer {
    override fun sign(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(data)
        return md.digest()
    }

    override fun verify(data: ByteArray, signature: ByteArray): Boolean {
        val md = MessageDigest.getInstance("MD5")
        md.update(data)
        return signature.contentEquals(md.digest())
    }
}