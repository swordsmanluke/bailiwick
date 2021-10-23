package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId

class Action(val bw: Bailiwick, val cipher: Encryptor, val cid: ContentId) {

    companion object {
        @JvmStatic
        fun create(bw: Bailiwick, cipher: Encryptor, type: ActionType, metadata: Map<String, String>): ContentId {
            val record = ActionRecord(type, metadata)
            return bw.store(record, cipher)
        }

        @JvmStatic
        fun updateKeyAction(bw: Bailiwick, cipher: Encryptor, key: String): ContentId {
            return create(bw, cipher, ActionType.UpdateKey, mapOf(Pair("key", key)))
        }
    }

    enum class ActionType { Delete, UpdateKey, Introduce }
    data class ActionRecord(val type: ActionType, val metadata: Map<String, String>)

    private var _record: ActionRecord? = null
    private val record: ActionRecord
        get() {
            if (_record == null) {
                _record = bw.retrieve(cid, cipher, ActionRecord::class.java)
            }
            return _record!!
        }

    val type: ActionType
        get() = record.type

    fun get(key: String): String? = record.metadata.get(key)



}