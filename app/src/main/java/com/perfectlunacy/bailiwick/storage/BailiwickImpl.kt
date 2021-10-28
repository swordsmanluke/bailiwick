package com.perfectlunacy.bailiwick.storage

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.*
import com.perfectlunacy.bailiwick.models.*
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import com.perfectlunacy.bailiwick.storage.ipfs.IPNS
import java.security.KeyPair
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

class BailiwickImpl(override val ipfs: IPFS, override val keyPair: KeyPair, private val db: BailiwickDatabase, override val cache: IPFSCache, private val filesDir: String):
    Bailiwick {
    companion object {
        val TAG = "BailiwickImpl"
        const val VERSION = "0.2"
        const val SHORT_TIMEOUT = 10L
        const val LONG_TIMEOUT = 30L
        const val USER_PRIVATE = "user_private"
    }

    private val ipns = IPNS(ipfs, db.accountDao())

    override val ipnsDao: IpnsCacheDao
        get() = db.ipnsCacheDao()

    override val peers: List<PeerId>
        get() = circles.all().flatMap { it.peers }.toSet().toList()

    override val peerId: PeerId = ipfs.peerID

    override val account: Account?
        get() = db.accountDao().activeAccount()

    private var _circles: Circles? = null
    override val circles: Circles
        get() {
            if(_circles == null) {
                _circles = Circles(this)
            }

            return _circles!!
        }

    override val users = Users(this)

    private var _manifest: Manifest? = null
    override var manifest: Manifest
        get() {
            if (_manifest == null) {
                // TODO: This is guaranteed to blow up
                _manifest = Manifest(this.keyring.encryptorForPeer(peerId), IPFSCache(""), "")
            }
            return _manifest!!
        }

        set(value) {
            TODO("Cannot be set yet")
        }


    private var _identity: UserIdentity? = null
    override var identity: UserIdentity
        get() {
            if(_identity == null) {
                val cid = ipns.cidForPath(peerId, "bw/$VERSION/identity.json", account?.sequence ?:0)!!
                _identity = UserIdentity(keyring.encryptorForPeer(peerId), cache, cid)
            }
            return _identity!!
        }
        set(value) {
            val newRoot = addFileToDir("bw/$VERSION", "identity.json", store(value, NoopEncryptor()))
            publishRoot(newRoot)
            _identity = value
        }

    private var _keyring: Keyring? = null
    override val keyring: Keyring
        get() {
            if( _keyring == null) {
                _keyring = KeyringImpl(this)
            }
            return _keyring!!
        }

    private val sequence: Int
        get() = account?.sequence ?: 1

    var _bwAcct: BailiwickAccount? = null
    override val bailiwickAccount: BailiwickAccount
        get() {
            if(_bwAcct == null) {
                _bwAcct = BailiwickAccount(this)
            }
            return _bwAcct!!
        }


    override fun newAccount(publicName: String, username: String, password: String, profilePicCid: ContentId?): Account {
        val hash = Sha1Signature().sign(password.toByteArray()) // TODO: Salt
        val passwordHash = Base64.getEncoder().encode(hash).toString()

        // TODO: CID of random Robot/Kitten/whatever for avatar
        val rootCid = initializeBailiwick(ipfs.peerID, publicName, profilePicCid ?: "", filesDir)
        Log.i(TAG, "Created Bailiwick structure. Publishing...")
        // Finally, publish the baseDir to IPNS
        publishRoot(rootCid)

        val account = Account(
            username, passwordHash,
            ipfs.peerID,
            rootCid,
            1,
            false
        )

        db.accountDao().insert(account)
        db.accountDao().activate(account.peerId)
        return account
    }

    override fun manifestFor(peerId: PeerId): Manifest? {
        // TODO: This is guaranteed to blow up
        val ipnsRec = ipnsDao.getPath(peerId, "bw/$VERSION/manifest.json") ?: return null
        return Manifest(this.keyring.encryptorForPeer(peerId), cache, ipnsRec.cid)
    }

    override fun encryptorForKey(keyId: String): Encryptor {
        if(keyId == USER_PRIVATE) {
            Log.i(TAG, "Retrieving encryptor with pub key ${Base64.getEncoder().encodeToString(keyPair.public.encoded)}")
            return RsaWithAesEncryptor(keyPair.private, keyPair.public)
        }

        if (keyId == "public") {
            return NoopEncryptor()
        }

        val key = keyring.secretKeys(keyId)?.lastOrNull()
        Log.i(TAG, "Looking for key $keyId ${ if(key == null) { "failed" } else {"succeeded"}}")
        return AESEncryptor(SecretKeySpec(Base64.getDecoder().decode(key!!), "AES"))
    }

    override fun encryptorForPeer(peerId: PeerId): Encryptor {
        val ciphers = (keyring.secretKeys(peerId)?: emptyList()).map { key ->
            AESEncryptor(SecretKeySpec(Base64.getDecoder().decode(key), "AES"))
        }.reversed()

        // Try all the keys we have for this Peer, including "no key at all"
        val finalCipher = MultiCipher(ciphers + NoopEncryptor()) {
            try { Gson().newJsonReader(String(it).reader()).hasNext(); true }
            catch(e: Exception) { false }
        }

        return finalCipher
    }

    override fun cidForPath(path: String): ContentId? {
        return cidForPath(peerId, path, sequence)
    }

    override fun cidForPath(peerId: PeerId, path: String): ContentId? {
        // TODO: track largest last-seen minSeq for all peerIds
        val minSeq = if(peerId == this.peerId) { account?.sequence ?: 0 } else { 0 }
        Log.d(TAG, "Searching for $peerId/$path:$minSeq")
        return cidForPath(peerId, path, minSeq)
    }

    override fun store(data: ByteArray): ContentId {
        val cid = ipfs.storeData(data)
        cache.store(cid, data)
        return cid
    }

    override fun <T> store(thing: T, cipher: Encryptor): ContentId {
        return store(cipher.encrypt(Gson().toJson(thing).toByteArray()))
    }

    override fun download(cid: ContentId): ByteArray? {
        return cache.raw(cid) ?: run {
            Log.d(TAG, "Cid $cid not in cache, retrieving from IPFS")
            if(cid.isBlank()) {
                return null
            }
            val data = ipfs.getData(cid, 10)
            Log.d(TAG, "Retrieved $cid from IPFS. Storing in Cache!")
            cache.store(cid, data)
            data
        }
    }

    override fun <T> retrieve(cid: ContentId?, cipher: Encryptor, clazz: Class<T>): T? {
        if(cid == null) { return null }
        val data = download(cid) ?: return null
        val rawJson = String(cipher.decrypt(data))
        Log.d(TAG, "Parsing ${clazz.simpleName} from '${rawJson}'")
        return Gson().fromJson(rawJson, clazz)
    }

    override fun addBailiwickFile(filename: String, cid: ContentId) {
        publishRoot(addFileToDir("bw/$VERSION", filename, cid))
    }

    override fun publishRoot(newRoot: ContentId) {
        ipns.publishRoot(newRoot)
    }

    override fun createIntroduction(identityCid: ContentId, password: String): ByteArray {
        val request = Gson().toJson(Introduction(
            false,
            peerId,
            identityCid,
            Base64.getEncoder().encodeToString(keyPair.public.encoded)))

        val aesKey = SecretKeySpec(Md5Signature().sign(password.toByteArray()), "AES")
        val aes = AESEncryptor(aesKey)
        return aes.encrypt(request.toByteArray())
    }

    override fun createIntroductionResponse(identityCid: ContentId, password: String): ByteArray {
        val request = Gson().toJson(Introduction(
            true,
            peerId,
            identityCid,
            Base64.getEncoder().encodeToString(keyPair.public.encoded)))

        val aesKey = SecretKeySpec(Md5Signature().sign(password.toByteArray()), "AES")
        val aes = AESEncryptor(aesKey)
        return aes.encrypt(request.toByteArray())
    }

    private fun initializeBailiwick(myPeerId: PeerId, name: String, profilePicCid: ContentId, filesDir: String): String {
        var verCid = ipfs.createEmptyDir()!!
        Log.i(TAG, "Created version dir. Adding files...")

        // Now create and add our basic files
        // Create encrypted account file
        val everyoneCircleKey = KeyGenerator.getInstance("AES").generateKey()
        val keyFileCid = KeyringImpl.create(this, ipfs.peerID, Base64.getEncoder().encodeToString(everyoneCircleKey.encoded))
        Log.i(TAG, "Created key file @ ${keyFileCid}")

        val circlesCid = Circles.create(this)
        Log.i(TAG, "Created Circles file @ ${circlesCid}")

        val acctFileCid = BailiwickAccount.create(this,
            myPeerId,
            keyFileCid,
            circlesCid,
            Users.create(this))

        Log.i(TAG, "Created acct file @ $acctFileCid")
        verCid = ipfs.addLinkToDir(verCid, "account.json", acctFileCid)!!

        // Create the subscriber-facing manifest
        val ipfsCache = IPFSCache(filesDir)
        val publicIdCid = UserIdentity.create(ipfs, ipfsCache, NoopEncryptor(), name, profilePicCid)
        verCid= ipfs.addLinkToDir(verCid, "identity.json", publicIdCid)!!

        val aes = AESEncryptor(everyoneCircleKey)
        val everyoneFeed = Feed.create(ipfsCache, ipfs, publicIdCid, listOf(), listOf(), aes)
        val manifestCid = Manifest.create(ipfsCache, this.ipfs, everyoneFeed, aes)
        Log.i(TAG, "Stored encrypted manifest @ ${manifestCid}")

        verCid = ipfs.addLinkToDir(verCid, "manifest.json", manifestCid)!!

        // Build our structure: bw/<VERSION>/<files>
        val bwCid = ipfs.addLinkToDir(ipfs.createEmptyDir()!!, VERSION, verCid)!!
        val rootCid = ipfs.addLinkToDir(ipfs.createEmptyDir()!!, "bw", bwCid)!!

        return rootCid
    }

    private fun<T> untilNotNull(function: () -> T?): T {
        var t: T? = null
        while (t == null) {
            t = function()
        }
        return t
    }

    private fun addFileToDir(path: String, filename: String, content: ContentId): ContentId {
        Log.i(TAG, "Adding $filename to $path")
        val dirs = path.split("/").filterNot{ it.isEmpty() }
        var dir = ""
        val rootCid = cidForPath(peerId, "", sequence)
        val root = PathNode("", null, rootCid!!)
        var parent = root

        // collect the list of dirs to cids
        dirs.forEach { d ->
            dir += "/$d"
            val cid = cidForPath(peerId, dir, sequence)!!
            val newNode = PathNode(d, parent, cid)
            parent = newNode
        }

        // add a file to its parent... and then update every one of its parents
        var cidToAdd = content
        var name = filename
        while (true) {
            val newParentCid = ipfs.addLinkToDir(parent.cid, name, cidToAdd)!!
            Log.i(TAG, "Linking $name -> ${parent.name} (${newParentCid})")
            name = parent.name
            parent.cid = newParentCid
            cidToAdd = newParentCid
            if (parent.parent == null) {
                break
            }
            parent = parent.parent!!
        }

        // Finally, return the new Root Cid
        return parent.cid
    }

    private fun cidForPath(pid: PeerId, path: String, minSequence: Int): ContentId? {
        return ipns.cidForPath(pid, path, minSequence)
    }

    data class PathNode(val name: String, val parent: PathNode?, var cid: ContentId)
}