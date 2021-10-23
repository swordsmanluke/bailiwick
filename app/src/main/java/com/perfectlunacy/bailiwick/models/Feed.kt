package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

// TODO: Add remaining Feed types
class Feed(private val bw: Bailiwick, private val peerId: PeerId, private var feedId: ContentId) {
    companion object {
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
            return postCids.map { cid ->
                Post(bw, cipher, author, cid)
            }
        }

    val postCids: List<ContentId>
        get() = record.posts

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
        record.posts.add(postCid)
        record.updatedAt = Calendar.getInstance().timeInMillis
    }

    fun save(cipher: Encryptor): ContentId {
        return bw.store(record, cipher)
    }
}