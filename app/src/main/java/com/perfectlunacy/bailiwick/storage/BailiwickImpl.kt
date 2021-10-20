package com.perfectlunacy.bailiwick.storage

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.ciphers.RSAEncryptor
import com.perfectlunacy.bailiwick.models.*
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.*
import java.security.KeyPair
import java.security.SecureRandom
import java.util.*
import javax.crypto.spec.SecretKeySpec

class BailiwickImpl(override val ipfs: IPFS, override val keyPair: KeyPair, private val db: BailiwickDatabase):
    Bailiwick {
    companion object {
        val TAG = "BailiwickImpl"
        const val VERSION = "0.2"
        const val SHORT_TIMEOUT = 10L
        const val LONG_TIMEOUT = 30L
    }

    override val peerId: PeerId = ipfs.peerID

    override val account: Account?
        get() = db.accountDao().activeAccount()

    private var _subscriptionFile: Subscriptions? = null
    override var subscriptions: Subscriptions
        get() {
            if(_subscriptionFile == null) {
                val encFile = ipfs.getData(bailiwickAccount.subscriptionsCid, 10)
                val kp = keyPair
                val acctJson = String(RSAEncryptor(kp.private, kp.public).decrypt(encFile))

                _subscriptionFile = Gson().fromJson(acctJson, Subscriptions::class.java)
            }

            return _subscriptionFile!!
        }
        set(value) {

        }

    private var _manifest: Manifest? = null
    override var manifest: Manifest
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
            val acct = account!!
            acct.rootCid = newRoot
            acct.sequence += 1
            db.accountDao().update(acct)
            _manifest = value

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
            val acct = account!!
            acct.rootCid = newRoot
            acct.sequence += 1
            db.accountDao().update(acct)
            _identity = value
        }

    private var _keyFile: KeyFile? = null
    override var keyFile: KeyFile
        get() {
            if(_keyFile == null) {
                val rsa = RSAEncryptor(keyPair.private, keyPair.public)
                _keyFile = retrieve(bailiwickAccount.keyFileCid, rsa, KeyFile::class.java)
            }

            return _keyFile!!
        }
        set(value) {
            val rsa = RSAEncryptor(keyPair.private, keyPair.public)
            val keyFileCid = store(value, rsa)

            // Update our account file
            bailiwickAccount = BailiwickAccount(peerId, keyFileCid, bailiwickAccount.subscriptionsCid)
        }

    private val sequence: Int
        get() = account?.sequence ?: 1

    private var _bwAcct: BailiwickAccount? = null
    private var bailiwickAccount: BailiwickAccount
        get() {
            if(_bwAcct == null) {
                val rsa = RSAEncryptor(keyPair.private, keyPair.public)
                val acctCid = ipfs.resolveNode(account!!.rootCid, mutableListOf("bw", VERSION, "account.json"), 10)

                _bwAcct = retrieve(acctCid!!, rsa, BailiwickAccount::class.java)
            }
            return _bwAcct!!
        }
        set(value) {
            val rsa = RSAEncryptor(keyPair.private, keyPair.public)
            val cid = store(value, rsa)

            val newRoot = addFileToDir("bw/$VERSION", "account.json", cid)
            publishRoot(newRoot)
            val acct = account!!
            acct.rootCid = newRoot
            acct.sequence += 1
            db.accountDao().update(acct)

            _bwAcct = value
        }

    override fun newAccount(username: String, password: String): Account {

        val hash = Sha1Signature().sign(password.toByteArray()) // TODO: Salt
        val passwordHash = Base64.getEncoder().encode(hash).toString()

        val rootCid = initializeBailiwick(ipfs.peerID)
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

    override fun manifestFor(peerId: PeerId, encryptor: Encryptor): Manifest? {
        val manCid = cidForPath(peerId, "bw/$VERSION/manifest.json", 0) ?: return null
        return retrieve(manCid, encryptor, Manifest::class.java)
    }

    override fun encryptorForKey(keyId: String): Encryptor {
        if(keyId == "user_private") {
            return RSAEncryptor(keyPair.private, keyPair.public)
        }

        if (keyId == "public") {
            return NoopEncryptor()
        }

        val key = keyFile.keys[keyId]?.lastOrNull()!!
        return AESEncryptor(SecretKeySpec(Base64.getDecoder().decode(key), "AES"))
    }

    override fun store(data: ByteArray): ContentId {
        return ipfs.storeData(data)
    }

    override fun <T> store(thing: T, cipher: Encryptor): ContentId {
        return store(cipher.encrypt(Gson().toJson(thing).toByteArray()))
    }

    override fun download(cid: ContentId): ByteArray? {
        return ipfs.getData(cid, 10)
    }

    override fun <T> retrieve(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T? {
        val data = download(cid) ?: return null
        return Gson().fromJson(String(cipher.decrypt(data)), clazz)
    }

    override fun addToDir(dir: ContentId, filename: String, cid: ContentId): ContentId {
        TODO("Not yet implemented")
    }

    override fun publishRoot(newRoot: ContentId) {
        val seq = 1 + (account?.sequence ?: 0)
        Log.i(TAG, "publishing new root @$newRoot. Old root: ${account?.rootCid}")
        ipfs.publishName(newRoot, seq, LONG_TIMEOUT)
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

    private fun initializeBailiwick(myPeerId: PeerId): String {
        var verCid = ipfs.createEmptyDir()!!
        Log.i(TAG, "Created version dir. Adding files...")

        // Now create and add our basic files
        val kp = keyPair
        val rsaEnc = RSAEncryptor(kp.private, kp.public)
        val b64Enc = Base64.getEncoder()
        val b64Dec = Base64.getDecoder()
        var acctFile: BailiwickAccount? = null
        var keyFile: KeyFile? = null

        // Create encrypted account file
        // TODO: val key = newAesKey()
        val keyBytes = ByteArray(16)
        SecureRandom().nextBytes(keyBytes)

        val publicEncKey = b64Enc.encodeToString(keyBytes)
        val keys = mapOf(Pair("$myPeerId:everyone", listOf(publicEncKey)))
        Log.d(TAG, "Public key: $publicEncKey")

        keyFile = KeyFile(keys)
        val keyFileCid = ipfs.storeData(rsaEnc.encrypt(Gson().toJson(keyFile).toByteArray()))
        Log.i(TAG, "Created key file @ ${keyFileCid}")

        val subscriptions = Gson().toJson(Subscriptions(emptyList(), emptyMap()))
        val subsFileCid = ipfs.storeData(rsaEnc.encrypt(subscriptions.toByteArray()))
        Log.i(TAG, "Created subs file @ ${subsFileCid}")

        acctFile = BailiwickAccount(
            myPeerId,
            keyFileCid,
            subsFileCid
        )

        val acctFileCid = store(acctFile, rsaEnc)
        Log.i(TAG, "Created acct file @ ${acctFileCid}")
        verCid = ipfs.addLinkToDir(verCid, "account.json", acctFileCid)!!

        // Create the subscriber-facing manifest
        val publicSubsKey = keyFile!!.keys.get("$myPeerId:everyone")?.lastOrNull()!!
        Log.i(TAG, "Public subs key: $publicSubsKey")
        val encKey = b64Dec.decode(publicSubsKey)
        Log.i(TAG, "Found public subscriber encryption key")
        val aes = AESEncryptor(SecretKeySpec(encKey, "AES"))

        val everyoneFeed = Feed(Calendar.getInstance().timeInMillis, emptyList(), emptyList(), emptyList(), "")
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
        var ipnsRecord = ipfs.resolveName(pid, minSequence.toLong(), LONG_TIMEOUT)
        if (ipnsRecord == null) {
            Log.e(TAG, "No IPNS record found for pid: $pid")
            return null
        }

        Log.i(TAG, "hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")

        // TODO: This only works if I already know the expected sequence number.
        var tries = 0
        while (ipnsRecord!!.sequence < minSequence && tries < 10) {
            ipnsRecord = ipfs.resolveName(pid, 0, LONG_TIMEOUT)!!
            Log.i(TAG, "Checking ${path} again... hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")
            tries += 1
        }

        val link = ("/ipfs/" + ipnsRecord.hash + "/" + path).replace("//","/")
        Log.i(TAG, "Resolving $link")
        return ipfs.resolveNode(link, LONG_TIMEOUT)
    }

    data class PathNode(val name: String, val parent: PathNode?, var cid: ContentId)
}