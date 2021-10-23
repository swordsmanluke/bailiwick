package com.perfectlunacy.bailiwick.models

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

class Manifest(val bw: Bailiwick, val peerId: PeerId) {
    companion object {
        const val TAG="Manifest"
        @JvmStatic
        fun create(bw: Bailiwick, everyoneFeed: ContentId, cipher: Encryptor): ContentId {
            return bw.store(ManifestRecord(mutableListOf(everyoneFeed)), cipher)
        }
    }

    data class ManifestRecord(val feeds: MutableList<ContentId>)

    private var _record: ManifestRecord? = null
    private val record: ManifestRecord?
        get() {
            if(_record== null) {
                val cipher = bw.encryptorForPeer(peerId)
                val manifestCid = bw.cidForPath(peerId,"bw/${BailiwickImpl.VERSION}/manifest.json")

                if(manifestCid != null) {
                    _record = bw.retrieve(manifestCid, cipher, ManifestRecord::class.java)
                } else {
                    Log.i(TAG, "Could not find Manifest record for $peerId")
                }
            }

            return _record
        }

    val feeds: List<Feed>
        get() {
            return record?.feeds?.map { feedCid ->
                Feed(bw, peerId, feedCid)
            } ?: emptyList()
        }

    fun updateFeed(feed: Feed, cipher: Encryptor): ContentId {
        val idx = feeds.indexOfFirst { it.uuid == feed.uuid }
        record!!.let {
            it.feeds.removeAt(idx)
            it.feeds.add(0, feed.save(cipher)) // order doesn't matter
        }

        return bw.store(record, cipher)
    }

}