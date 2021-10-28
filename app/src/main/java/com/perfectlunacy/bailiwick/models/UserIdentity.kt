package com.perfectlunacy.bailiwick.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheReader
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheWriter

class UserIdentity(val cipher: Encryptor, val ipfs: IPFSCacheReader, val cid: ContentId) {

    data class Identity(var name: String, var profilePicCid: String)

    val avatar: Bitmap?
    get() {
        try {
            val picData = cipher.decrypt(ipfs.raw(profilePicCid) ?: return null)
            return BitmapFactory.decodeByteArray(picData, 0, picData.size)
        } catch(e: Exception) {
            return null
        }
    }

    var profilePicCid
        get() = record.profilePicCid
        set(value) {
            record.profilePicCid = value
        }

    var name
        get() = record.name
        set(value) {
            record.name = value
        }

    private var _record: Identity? = null
    private val record: Identity
        get() {
            if(_record == null) {
                try {
                    _record = ipfs.retrieve(cid, cipher, Identity::class.java)!!
                } catch(e: Exception) {
                    // Null Record pattern
                    _record = Identity("Unknown", "")
                }
            }

            return _record!!
        }

    companion object {
        @JvmStatic
        fun create(ipfs: IPFS, ipfsCache: IPFSCacheWriter, cipher: Encryptor, name: String, profilePicCid: ContentId): ContentId {
            val identityBytes = cipher.encrypt(Gson().toJson(Identity(name, profilePicCid)).toByteArray())
            val cid = ipfs.storeData(identityBytes)
            ipfsCache.store(cid, identityBytes)

            return cid
        }
    }
}