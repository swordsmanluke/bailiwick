package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.BailiwickAccount
import com.perfectlunacy.bailiwick.models.Users
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ipfs.*
import java.security.KeyPair
import java.security.PublicKey
import java.util.*

typealias PeerId=String
typealias ContentId=String

interface Bailiwick {
    val ipfs: IPFS
    val peerId: PeerId
    val bailiwickAccount: BailiwickAccount
    val users: Users
    val account: Account?
    var subscriptions: Subscriptions
    var ipfsManifest: com.perfectlunacy.bailiwick.models.ipfs.Manifest
    val manifest: com.perfectlunacy.bailiwick.models.Manifest
    var identity: Identity
    var keyFile: KeyFile
    val keyPair: KeyPair

    fun newAccount(publicName: String, username: String, password: String, profilePicCid: ContentId?): Account
    fun manifestFor(peerId: PeerId, encryptor: Encryptor): Manifest?
    fun encryptorForKey(keyId: String): Encryptor
    fun encryptorForPeer(peerId: PeerId): Encryptor

    fun cidForPath(path: String): ContentId?
    fun cidForPath(peerId: PeerId, path: String): ContentId?
    fun store(data: ByteArray): ContentId
    fun <T>store(thing :T, cipher: Encryptor): ContentId
    fun download(cid: ContentId): ByteArray?
    fun <T> retrieve(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T?
    fun addBailiwickFile(filename: String, cid: ContentId)
    fun publishRoot(newRoot: ContentId)
    fun sign(post: Post): Post
    fun addSubscription(peerId: PeerId, identityCid: ContentId, publicKey: PublicKey, circles: List<String>)
    fun createIntroductionMessage(identityCid: ContentId, password: String): ByteArray
    fun createIntroductionMessage(uuid: UUID, identityCid: ContentId, password: String): ByteArray

    /***
     * TODO later:
     * fun login(username: String, password: String): Account?
     *
     * fun sendAction(action: Action, peerId: PeerId)
     *
     */

}