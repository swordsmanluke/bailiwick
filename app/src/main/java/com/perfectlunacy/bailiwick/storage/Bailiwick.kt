package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.ipfs.*

typealias PeerId=String
typealias ContentId=String

interface Bailiwick {
    val ipfs: IPFS
    val peerId: PeerId
    val account: Account?
    var subscriptions: Subscriptions
    var manifest: Manifest
    var identity: Identity
    var keys: KeyFile

    fun newAccount(username: String, password: String): Account

    fun store(data: ByteArray): ContentId
    fun download(cid: ContentId): ByteArray?
    fun addToDir(dir: ContentId, filename: String, cid: ContentId): ContentId
    fun publishRoot(newRoot: ContentId)

    /***
     * TODO later:
     * fun login(username: String, password: String): Account?
     *
     * fun sendAction(action: Action, peerId: PeerId)
     *
     */

}