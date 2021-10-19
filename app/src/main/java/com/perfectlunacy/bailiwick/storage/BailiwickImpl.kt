package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.RSAEncryptor
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.*
import java.security.KeyFactory
import java.security.KeyPair
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.spec.SecretKeySpec

class BailiwickImpl(override val ipfs: IPFS, private val keyPair: KeyPair, private val db: BailiwickDatabase):
    Bailiwick {
    companion object {
        val TAG = "IpfsLiteStore"
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
    override var manifest: Manifest
        get() = TODO("Not yet implemented")
        set(value) {}
    override var identity: Identity
        get() = TODO("Not yet implemented")
        set(value) {}
    override var keys: KeyFile
        get() = TODO("Not yet implemented")
        set(value) {}

    private val sequence: Int
        get() = account?.sequence ?: 1

    private var _bwAcct: BailiwickAccount? = null
    private var bailiwickAccount: BailiwickAccount
        get() {
            if(_bwAcct == null) {
                // move down the directory until we find the account file
                val bwCid =
                    ipfs.getLinks(account!!.rootCid, false, 10)
                        ?.find { it.name == "bw" }?.cid!!
                val versionCid = ipfs.getLinks(bwCid.String(), false, 10)
                    ?.find { it.name == VERSION }?.cid!!
                val acctCid = ipfs.getLinks(versionCid.String(), false, 10)
                    ?.find { it.name == "account.json" }?.cid!!
                val acctEncrypted = ipfs.getData(acctCid.String(), 10)

                val kp = keyPair
                val acctJson = String(RSAEncryptor(kp.private, kp.public).decrypt(acctEncrypted))

                _bwAcct = Gson().fromJson(acctJson, BailiwickAccount::class.java)
            }
            return _bwAcct!!
        }
        set(value) {
            val kp = keyPair
            val acctJson = Gson().toJson(value)
            val acctEncrypted = RSAEncryptor(kp.private, kp.public).encrypt(acctJson.toByteArray())
            val acctCid = ipfs.storeData(acctEncrypted)

            val newRoot = addFileToDir("bw/$VERSION", "account.json", acctCid)
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
            sequence,
            false
        )

        db.accountDao().insert(account)
        db.accountDao().activate(account.peerId)
        return account
    }

    override fun store(data: ByteArray): ContentId {
        return ipfs.storeData(data)
    }

    override fun download(cid: ContentId): ByteArray? {
        return ipfs.getData(cid, 10)
    }

    override fun addToDir(dir: ContentId, filename: String, cid: ContentId): ContentId {
        TODO("Not yet implemented")
    }

    override fun publishRoot(newRoot: ContentId) {
        ipfs.publishName(newRoot, 1 + (account?.sequence ?: 0), LONG_TIMEOUT)
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
        val keys = mapOf(Pair("$myPeerId:public", listOf(publicEncKey)))
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

        val encAcctFile = rsaEnc.encrypt(Gson().toJson(acctFile).toByteArray())

        val acctFileCid = ipfs.storeData(encAcctFile)
        Log.i(TAG, "Created acct file @ ${acctFileCid}")
        verCid = ipfs.addLinkToDir(verCid, "account.json", acctFileCid)!!

        // Create the subscriber-facing manifest
        val publicSubsKey = keyFile!!.keys.get("$myPeerId:public")?.lastOrNull()!!
        Log.i(TAG, "Public subs key: $publicSubsKey")
        val encKey = b64Dec.decode(publicSubsKey)
        Log.i(TAG, "Found public subscriber encryption key")

        val manifest = Gson().toJson(Manifest(emptyList(), emptyList()))
        val aes = AESEncryptor(SecretKeySpec(encKey, "AES"))
        val IV = ByteArray(16)
        SecureRandom().nextBytes(IV)
        val ciphertext = aes.encrypt(manifest.toByteArray(), IV)
        val manifestCid = ipfs.storeData(ciphertext)
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
        val dirs = path.split("/")
        val peerId = threads.lite.cid.PeerId(peerId.toByteArray())
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

    private fun cidForPath(pid: threads.lite.cid.PeerId, path: String, minSequence: Int): ContentId? {
        var ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, SHORT_TIMEOUT)
        if (ipnsRecord == null) {
            return null
        }

        Log.i(TAG, "hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")

        // TODO: This only works if I already know the expected sequence number.
        while (ipnsRecord!!.sequence < minSequence) {
            ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, SHORT_TIMEOUT)!!
            Log.i(TAG, "Checking again... hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")
        }

        val link = "/ipfs/" + ipnsRecord.hash + path
        Log.i(TAG, "Resolving $link")
        return ipfs.resolveNode(link, SHORT_TIMEOUT)
    }

    data class PathNode(val name: String, val parent: PathNode?, var cid: ContentId)
}