package com.perfectlunacy.bailiwick.models

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

// TODO: Add remaining Feed types
class Feed(private val bw: Bailiwick, private val peerId: PeerId, private var feedId: ContentId) {
    companion object {
        const val TAG = "Feed"
        @JvmStatic
        fun create(bw: Bailiwick, identityCid: ContentId, cipher: Encryptor): ContentId {
            val now = Calendar.getInstance().timeInMillis
            return bw.store(FeedRecord(now, UUID.randomUUID(), mutableListOf(), mutableListOf(), mutableListOf(), identityCid), cipher)
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
            val cipher = bw.encryptorForPeer(peerId)
            _record = bw.retrieve(feedId, cipher, FeedRecord::class.java)
        }

        return _record!!
    }

    val uuid: UUID
        get() = record.uuid

    val updatedAt: Long
        get() = record.updatedAt

    val posts: List<Post>
        get() {
            val cipher= bw.encryptorForPeer(peerId)
            val author = UserIdentity.fromIPFS(bw, cipher, identityCid)
            Log.i(TAG, "Found ${postCids.count()} posts")
            return postCids.map { cid ->
                Post(bw, cipher, author, cid)
            }
        }

    val postCids: List<ContentId>
        get() = record.posts

    val actions: List<Action>
        get() = actionCids.mapNotNull { cid ->
            val cipher = RsaWithAesEncryptor(bw.keyPair.private, bw.keyPair.public)
            try {
                // There will be lots of actions which are not for us
                // We won't be able to decrypt them.
                Action(bw, cipher, cid)
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
            val cipher = bw.encryptorForPeer(peerId)
            return UserIdentity.fromIPFS(bw, cipher, identityCid)
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

    fun save(cipher: Encryptor): ContentId {
        return bw.store(record, cipher)
    }

}