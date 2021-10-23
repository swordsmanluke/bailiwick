package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.ipfs.FileDef
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import java.util.*

class Post(val bw: Bailiwick, val cipher: Encryptor, val author: UserIdentity, val postCid: ContentId) {

    companion object {
        @JvmStatic
        fun create(bw: Bailiwick, cipher: Encryptor, parentCid: String?, text: String, files: List<FileDef>): ContentId {
            val record = PostRecord(Calendar.getInstance().timeInMillis,
                parentCid,
                text,
                files,
                "")

            val signer = RsaSignature(bw.keyPair.public, bw.keyPair.private)
            record.sign(signer)

            return bw.store(record, cipher)
        }
    }

    data class PostRecord(val timestamp: Long,
                          val parentCid: String?,
                          val text: String,
                          val files: List<FileDef>,
                          var signature: String) {

        fun sign(signer: RsaSignature) {
            signature = Base64.getEncoder().encodeToString(signer.sign(unsigned.toByteArray()))
        }

        fun verify(signer: RsaSignature): Boolean {
            return signer.verify(unsigned.toByteArray(), Base64.getDecoder().decode(signature))

        }

        val unsigned: String
            get() = "$timestamp:$parentCid:$text:${files.map { it.cid }.sorted()}"
    }

    private var _record: PostRecord? = null
    private val record: PostRecord
        get() {
            if(_record == null) {
                _record = bw.retrieve(postCid, cipher, PostRecord::class.java)
            }
            return _record!!
        }

    val timestamp: Long
        get() = record.timestamp

    val parentCid: String?
        get() = record.parentCid

    val text: String
        get() = record.text

    val files: List<FileDef>
        get() = record.files

    val signature: String
        get() = record.signature

    fun isSigValid(signer: RsaSignature): Boolean {
        return record.verify(signer)
    }

    override fun hashCode(): Int {
        return record.signature.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Post

        if (postCid != other.postCid) return false

        return true
    }
}