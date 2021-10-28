package com.perfectlunacy.bailiwick.signatures

import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

class RsaSignature(val pubKey: PublicKey?, val privKey: PrivateKey?) : Signer {
    override fun sign(data: ByteArray): ByteArray {
        val signer: Signature = Signature.getInstance("SHA1withRSA")
        signer.initSign(privKey!!)
        signer.update(data)
        return signer.sign()
    }

    override fun verify(data: ByteArray, signature: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA1withRSA")
        verifier.initVerify(pubKey!!)
        verifier.update(data)
        return verifier.verify(signature)
    }

}