package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.ipfs.FileDef
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.signatures.Signer
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheReader
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheWriter
import java.util.*

class Post(val cipher: Encryptor, val ipfs: IPFSCacheReader, val author: UserIdentity, val postCid: ContentId) {

    companion object {
        @JvmStatic
        fun create(ipfs: IPFS, ipfsCache: IPFSCacheWriter, signer: Signer, cipher: Encryptor, parentCid: String?, text: String, files: List<FileDef>): ContentId {
            val record = PostRecord(Calendar.getInstance().timeInMillis,
                parentCid,
                text,
                files,
                "")

            record.sign(signer)

            val data = cipher.encrypt(Gson().toJson(record).toByteArray())
            val cid = ipfs.storeData(data)
            ipfsCache.store(cid, data)

            return cid
        }
    }

    data class PostRecord(val timestamp: Long,
                          val parentCid: String?,
                          val text: String,
                          val files: List<FileDef>,
                          var signature: String) {

        fun sign(signer: Signer) {
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
                _record = ipfs.retrieve(postCid, cipher, PostRecord::class.java)
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