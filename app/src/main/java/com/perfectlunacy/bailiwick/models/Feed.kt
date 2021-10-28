package com.perfectlunacy.bailiwick.models

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheReader
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheWriter
import java.util.*

// TODO: Add remaining Feed types
class Feed(private val cipher: Encryptor, private val ipfs: IPFSCacheReader, private var feedId: ContentId) {
    companion object {
        const val TAG = "Feed"
        @JvmStatic
        fun create(ipfsCache: IPFSCacheWriter, ipfs: IPFS, identityCid: ContentId, posts: List<ContentId>, actions: List<ContentId>, cipher: Encryptor): ContentId {
            val now = Calendar.getInstance().timeInMillis
            val rec = FeedRecord(now, UUID.randomUUID(), posts.toMutableList(), mutableListOf(), actions.toMutableList(), identityCid)
            val json = Gson().toJson(rec)
            val cipherText = cipher.encrypt(json.toByteArray())
            val cid = ipfs.storeData(cipherText)
            ipfsCache.store(cid, cipherText)

            return cid
        }
    }

    data class FeedRecord(var updatedAt: Long,
                          val uuid: UUID,
                          val posts: MutableList<ContentId>,
                          val interactions: MutableList<ContentId>,
                          val actions: MutableList<ContentId>,
                          var identity: ContentId)

    private var _record: FeedRecord? = null
    private val record: FeedRecord
    get() {
        if(_record == null) {
            try {
                _record = ipfs.retrieve(feedId, cipher, FeedRecord::class.java)
            } catch(e: Exception) {
                Log.e(TAG, e.stackTraceToString())
            }
        }

        return _record ?: FeedRecord(Calendar.getInstance().timeInMillis, UUID.randomUUID(), mutableListOf(), mutableListOf(), mutableListOf(), "")
    }

    val uuid: UUID
        get() = record.uuid

    val updatedAt: Long
        get() = record.updatedAt

    val posts: List<Post>
        get() {
            val author = UserIdentity(cipher, ipfs, identityCid)
            Log.i(TAG, "Found ${postCids.count()} posts")
            return postCids.map { cid ->
                Post(cipher, ipfs, author, cid)
            }
        }

    val postCids: List<ContentId>
        get() = record.posts

    val actions: List<Action>
        get() = actionCids.mapNotNull { cid ->
            try {
                // There will be lots of actions which are not for us
                // We won't be able to decrypt them.
                Action(cipher, ipfs, cid)
            } catch (e: Exception) {
                // TODO: What exception is actually expected here
                null
            }
        }

    val actionCids: List<ContentId>
        get() = record.actions

    private val identityCid: ContentId
        get() = record.identity

    val identity: UserIdentity
        get() {
            return UserIdentity(cipher, ipfs, identityCid)
        }

    fun addPost(postCid: ContentId) {
        Log.i(TAG, "Adding post")
        record.posts.add(postCid)
        record.updatedAt = Calendar.getInstance().timeInMillis
    }

    fun addAction(actionCid: ContentId) {
        record.actions.add(actionCid)
        record.updatedAt = Calendar.getInstance().timeInMillis
    }
}