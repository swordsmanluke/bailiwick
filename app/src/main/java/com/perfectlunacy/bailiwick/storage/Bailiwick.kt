package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.*
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import java.security.KeyPair

typealias PeerId=String
typealias ContentId=String

interface Bailiwick {
    val ipnsDao: IpnsCacheDao
    val cache: IPFSCache
    val peers: List<PeerId>
    val ipfs: IPFS
    val peerId: PeerId
    val bailiwickAccount: BailiwickAccount
    val users: Users
    val account: Account?
    val circles: Circles
    val keyring: Keyring

    val manifest: Manifest
    var identity: UserIdentity

    val keyPair: KeyPair

    fun newAccount(publicName: String, username: String, password: String, profilePicCid: ContentId?): Account
    fun manifestFor(peerId: PeerId): Manifest?
    fun encryptorForKey(keyId: String): Encryptor
    fun encryptorForPeer(peerId: PeerId): Encryptor

    fun cidForPath(path: String): ContentId?
    fun cidForPath(peerId: PeerId, path: String): ContentId?
    fun store(data: ByteArray): ContentId
    fun <T>store(thing :T, cipher: Encryptor): ContentId
    fun download(cid: ContentId): ByteArray?
    fun <T> retrieve(cid: ContentId?, cipher: Encryptor, clazz: Class<T>): T?
    fun addBailiwickFile(filename: String, cid: ContentId)
    fun publishRoot(newRoot: ContentId)
    fun createIntroduction(identityCid: ContentId, password: String): ByteArray
    fun createIntroductionResponse(identityCid: ContentId, password: String): ByteArray

    /***
     * TODO later:
     * fun login(username: String, password: String): Account?
     *
     * fun sendAction(action: Action, peerId: PeerId)
     *
     */

}