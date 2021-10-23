package com.perfectlunacy.bailiwick.storage

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.*
import com.perfectlunacy.bailiwick.models.BailiwickAccount
import com.perfectlunacy.bailiwick.models.Circles
import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.models.Users
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import java.security.KeyPair
import java.security.SecureRandom
import java.util.*
import javax.crypto.spec.SecretKeySpec

class BailiwickImpl(override val ipfs: IPFS, override val keyPair: KeyPair, private val db: BailiwickDatabase, private val cache: IpfsCache):
    Bailiwick {
    companion object {
        val TAG = "BailiwickImpl"
        const val VERSION = "0.2"
        const val SHORT_TIMEOUT = 10L
        const val LONG_TIMEOUT = 30L
        const val USER_PRIVATE = "user_private"
    }

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
    override var ipfsManifest: Manifest
        get() {
            if (_manifest == null) {
                val aes = encryptorForKey("$peerId:everyone")
                val manCid = cidForPath(peerId, "bw/$VERSION/manifest.json", sequence)!!
                _manifest = retrieve(manCid, aes, Manifest::class.java)
            }
            return _manifest!!
        }

        set(value) {
            val aes = encryptorForKey("$peerId:everyone")
            val newRoot = addFileToDir("bw/$VERSION", "manifest.json", store(value, aes))
            publishRoot(newRoot)

            _manifest = value
        }

    private var _realManifest: com.perfectlunacy.bailiwick.models.Manifest? = null
    override var manifest: com.perfectlunacy.bailiwick.models.Manifest
        get() {
            if (_realManifest == null) {
                _realManifest = com.perfectlunacy.bailiwick.models.Manifest.fromIPFS(this, peerId, ipfsManifest)
            }
            return _realManifest!!
        }

        set(value) {
            TODO("Cannot be set yet")
        }


    private var _identity: Identity? = null
    override var identity: Identity
        get() {
            if(_identity == null) {
                val cid = cidForPath(peerId, "bw/$VERSION/identity.json", sequence)!!
                _identity = retrieve(cid, NoopEncryptor(), Identity::class.java)
            }
            return _identity!!
        }
        set(value) {
            val newRoot = addFileToDir("bw/$VERSION", "identity.json", store(value, NoopEncryptor()))
            publishRoot(newRoot)
            _identity = value
        }

    private var _keyFile: KeyFile? = null
    override var keyFile: KeyFile
        get() {
            if(_keyFile == null) {
                val rsa = encryptorForKey(USER_PRIVATE)
                _keyFile = retrieve(bailiwickAccount.keyFileCid, rsa, KeyFile::class.java)
            }

            return _keyFile!!
        }
        set(value) {
            val rsa = encryptorForKey(USER_PRIVATE)
            val keyFileCid = store(value, rsa)

            // Update our account file
            bailiwickAccount.keyFileCid = keyFileCid
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
        val rootCid = initializeBailiwick(ipfs.peerID, publicName, profilePicCid ?: "")
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

    override fun manifestFor(peerId: PeerId, encryptor: Encryptor, minSequence: Int): Manifest? {
        val manCid = cidForPath(peerId, "bw/$VERSION/manifest.json", minSequence) ?: return null
        return retrieve(manCid, encryptor, Manifest::class.java)
    }

    override fun encryptorForKey(keyId: String): Encryptor {
        if(keyId == USER_PRIVATE) {
            Log.i(TAG, "Retrieving encryptor with pub key ${Base64.getEncoder().encodeToString(keyPair.public.encoded)}")
            return RsaWithAesEncryptor(keyPair.private, keyPair.public)
        }

        if (keyId == "public") {
            return NoopEncryptor()
        }

        val key = keyFile.keys[keyId]?.lastOrNull()!!
        return AESEncryptor(SecretKeySpec(Base64.getDecoder().decode(key), "AES"))
    }

    override fun encryptorForPeer(peerId: PeerId): Encryptor {
        val ciphers = keyFile.keys.getOrDefault(peerId, emptyList()).map { key ->
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
        return cidForPath(peerId, path, 0)
    }

    override fun store(data: ByteArray): ContentId {
        val cid = ipfs.storeData(data)
        cache.cache(cid, data)
        return cid
    }

    override fun <T> store(thing: T, cipher: Encryptor): ContentId {
        return store(cipher.encrypt(Gson().toJson(thing).toByteArray()))
    }

    override fun download(cid: ContentId): ByteArray? {
        return cache.get(cid) ?: run {
            val data = ipfs.getData(cid, 10)
            cache.cache(cid, data)
            data
        }
    }

    override fun <T> retrieve(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T? {
        val data = download(cid) ?: return null
        val rawJson = String(cipher.decrypt(data))
        Log.d(TAG, "Parsing ${clazz.simpleName} from '${rawJson}'")
        return Gson().fromJson(rawJson, clazz)
    }

    override fun addBailiwickFile(filename: String, cid: ContentId) {
        publishRoot(addFileToDir("bw/$VERSION", filename, cid))
    }

    override fun publishRoot(newRoot: ContentId) {
        val seq = 1 + (account?.sequence ?: 0)
        Log.i(TAG, "publishing new root @$newRoot. Old root: ${account?.rootCid}")
        ipfs.publishName(newRoot, seq, LONG_TIMEOUT)
        account?.let {
            it.rootCid = newRoot
            it.sequence = seq
            db.accountDao().update(it)
        }
    }

    override fun sign(post: Post): Post {
        val unsigned = Gson().toJson(post)
        val signature = Base64.getEncoder()
            .encodeToString(
                RsaSignature(
                    keyPair.public,
                    keyPair.private
                ).sign(unsigned.toByteArray())
            )
        return Post(post.timestamp, post.parentCid, post.text, post.files, signature)
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

    private fun initializeBailiwick(myPeerId: PeerId, name: String, profilePicCid: ContentId): String {
        var verCid = ipfs.createEmptyDir()!!
        Log.i(TAG, "Created version dir. Adding files...")

        // Now create and add our basic files
        val rsa = encryptorForKey(USER_PRIVATE)
        val b64Enc = Base64.getEncoder()
        val b64Dec = Base64.getDecoder()
        var keyFile: KeyFile? = null

        // Create encrypted account file
        // TODO: val key = newAesKey()
        val keyBytes = ByteArray(16)
        SecureRandom().nextBytes(keyBytes)

        val publicEncKey = b64Enc.encodeToString(keyBytes)
        val keys = mapOf(Pair("$myPeerId:everyone", listOf(publicEncKey)), Pair(myPeerId, listOf(publicEncKey)))
        Log.d(TAG, "Public key: $publicEncKey")

        keyFile = KeyFile(keys)
        val keyFileCid = store(keyFile, rsa)
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
        val publicSubsKey = keyFile.keys.get("$myPeerId:everyone")?.lastOrNull()!!
        Log.i(TAG, "Public subs key: $publicSubsKey")
        val encKey = b64Dec.decode(publicSubsKey)
        Log.i(TAG, "Found public subscriber encryption key")
        val aes = AESEncryptor(SecretKeySpec(encKey, "AES"))

        val identity = Identity(name, profilePicCid)
        val publicIdCid = store(identity, NoopEncryptor())
        verCid= ipfs.addLinkToDir(verCid, "identity.json", publicIdCid)!!

        // The <everyone> Feed which is created by default inherits the public Identity,
        // but this can be later changed. So, store an encrypted copy and we'll overwrite
        // it later if needed.
        val feedIdCid = store(identity, aes)
        val everyoneFeed = Feed(Calendar.getInstance().timeInMillis, emptyList(), emptyList(), emptyList(), feedIdCid)

        val feedCid = store(everyoneFeed, aes)
        val manifestCid = store(Manifest(listOf(feedCid)), aes)
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
        val root = PathNode("/", null, cidForPath(peerId, "", sequence)!!)
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
        var ipnsRecord = ipfs.resolveName(pid, 0, LONG_TIMEOUT)
        if (ipnsRecord == null) {
            Log.e(TAG, "No IPNS record found for pid: $pid")
            return null
        }

        Log.i(TAG, "hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")

        // TODO: This only works if I already know the expected sequence number.
        var tries = 0
        while (ipnsRecord!!.sequence < minSequence && tries < 10) {
            ipnsRecord = ipfs.resolveName(pid, minSequence.toLong(), LONG_TIMEOUT) ?: ipnsRecord
            Log.i(TAG, "Checking ${path} again... hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")
            tries += 1
        }

        val link = ("/ipfs/" + ipnsRecord.hash + "/" + path).replace("//","/")
        Log.i(TAG, "Resolving $link")
        return ipfs.resolveNode(link, LONG_TIMEOUT)
    }

    data class PathNode(val name: String, val parent: PathNode?, var cid: ContentId)
}