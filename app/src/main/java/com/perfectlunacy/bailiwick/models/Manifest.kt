package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheReader
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheWriter

class Manifest(val cipher: Encryptor, val ipfs: IPFSCacheReader, val cid: ContentId) {
    companion object {
        const val TAG="Manifest"
        @JvmStatic
        fun create(ipfsCache: IPFSCacheWriter, ipfs: IPFS, everyoneFeed: ContentId, cipher: Encryptor): ContentId {
            val json = Gson().toJson(ManifestRecord(mutableListOf(everyoneFeed)))
            val data = cipher.encrypt(json.toByteArray())
            val cid = ipfs.storeData(data)
            ipfsCache.store(cid, data)
            return cid
        }
    }

    data class ManifestRecord(val feeds: MutableList<ContentId>)

    private var _record: ManifestRecord? = null
    private val record: ManifestRecord?
        get() {
            if(_record== null) {
                _record = ipfs.retrieve(cid, cipher, ManifestRecord::class.java)
            }

            return _record
        }

    val feeds: List<Feed>
        get() {
            return record?.feeds?.map { feedCid ->
                Feed(cipher, ipfs, feedCid)
            } ?: emptyList()
        }

    val feedCids: List<ContentId>
        get() {
            return record?.feeds ?: emptyList()
        }

    fun updateFeed(feed: Feed, cipher: Encryptor): ContentId {
        TODO("Store feed")
    }

}