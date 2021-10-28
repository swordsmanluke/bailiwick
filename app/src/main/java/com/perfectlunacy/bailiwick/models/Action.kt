package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.signatures.Signer
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheReader
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheWriter

class Action(val cipher: Encryptor, val ipfs: IPFSCacheReader, val cid: ContentId) {

    companion object {
        @JvmStatic
        fun create(ipfs: IPFS, ipfsCache: IPFSCacheWriter, cipher: Encryptor, type: ActionType, metadata: Map<String, String>): ContentId {
            val record = Gson().toJson(ActionRecord(type, metadata))
            val data = cipher.encrypt(record.toByteArray())
            val cid = ipfs.storeData(data)
            ipfsCache.store(cid, data)
            return cid
        }

        @JvmStatic
        fun updateKeyAction(ipfs: IPFS, ipfsCache: IPFSCacheWriter, cipher: Encryptor, key: String): ContentId {
            return create(ipfs, ipfsCache, cipher, ActionType.UpdateKey, mapOf(Pair("key", key)))
        }
    }

    enum class ActionType { Delete, UpdateKey, Introduce }
    data class ActionRecord(val type: ActionType, val metadata: Map<String, String>)

    private var _record: ActionRecord? = null
    private val record: ActionRecord
        get() {
            if (_record == null) {
                _record = ipfs.retrieve(cid, cipher, ActionRecord::class.java)
            }
            return _record!!
        }

    val type: ActionType
        get() = record.type

    fun get(key: String): String? = record.metadata.get(key)



}