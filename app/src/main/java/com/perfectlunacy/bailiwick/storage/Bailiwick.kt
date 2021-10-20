package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.*
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.ipfs.*
import java.security.KeyPair

typealias PeerId=String
typealias ContentId=String

interface Bailiwick {
    val ipfs: IPFS
    val peerId: PeerId
    val account: Account?
    var subscriptions: Subscriptions
    var manifest: Manifest
    var identity: Identity
    var keyFile: KeyFile
    val keyPair: KeyPair

    fun newAccount(username: String, password: String): Account
    fun manifestFor(peerId: PeerId, encryptor: Encryptor): Manifest?
    fun encryptorForKey(keyId: String): Encryptor

    fun store(data: ByteArray): ContentId
    fun <T>store(thing :T, cipher: Encryptor): ContentId
    fun download(cid: ContentId): ByteArray?
    fun <T> retrieve(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T?
    fun addToDir(dir: ContentId, filename: String, cid: ContentId): ContentId
    fun publishRoot(newRoot: ContentId)
    fun sign(post: Post): Post

    /***
     * TODO later:
     * fun login(username: String, password: String): Account?
     *
     * fun sendAction(action: Action, peerId: PeerId)
     *
     */

}