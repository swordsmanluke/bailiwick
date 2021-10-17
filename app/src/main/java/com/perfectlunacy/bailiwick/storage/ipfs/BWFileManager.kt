package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.RSAEncryptor
import threads.lite.IPFS
import threads.lite.cid.Cid
import threads.lite.cid.PeerId
import threads.lite.core.Closeable
import threads.lite.core.ClosedException
import threads.lite.core.TimeoutCloseable
import threads.lite.utils.Link
import java.security.KeyFactory
import java.security.KeyPair
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.spec.SecretKeySpec

class BWFileManager(private val ipfs: IPFS, private val context: Context) {

    private var _sequence: Int = currentSequence(ipfs.peerID)
    val sequence: Int
        get() = _sequence

    fun getLinks(cid: Cid, resolveChildren: Boolean, closeable: Closeable): MutableList<Link>? {
        return try { ipfs.getLinks(cid, resolveChildren, closeable) }
                catch(_: ClosedException) { null }
    }

    fun initializeBailiwick(myPeerId: PeerId): String {
        var dirty = false

        var verCid = ipfs.createEmptyDir()!!
        var bwCid = ipfs.addLinkToDir(ipfs.createEmptyDir()!!, VERSION, verCid)!!
        var rootCid = ipfs.addLinkToDir(ipfs.createEmptyDir()!!, "bw", bwCid)!!
        Log.i(TAG, "Created root CID. Examining links...")

        // Now add our basic files if they need to be created
        val kp = keyPair
        val rsaEnc = RSAEncryptor(kp.private, kp.public)
        val b64Enc = Base64.getEncoder()
        val b64Dec = Base64.getDecoder()
        var acctFile: BailiwickAccount? = null
        var keyFile: KeyList? = null

        // Create encrypted account file
        val files = untilNotNull { getLinks(verCid, true, TimeoutCloseable(LONG_TIMEOUT)) }
        files.find { l -> l.name == "account.json" }.let {
            if (it == null) {
                // TODO: val key = newAesKey()
                val keyBytes = ByteArray(16)
                SecureRandom().nextBytes(keyBytes)

                val publicEncKey = b64Enc.encodeToString(keyBytes)
                val keys = mapOf(Pair("$myPeerId:public", listOf(publicEncKey)))
                Log.d(TAG, "Public key: $publicEncKey")

                keyFile = KeyList(keys)
                val keyFileCid = ipfs.storeData(rsaEnc.encrypt(Gson().toJson(keyFile).toByteArray()))
                Log.i(TAG, "Created key file @ ${keyFileCid.key}")

                val subscriptions = Gson().toJson(Subscriptions(emptyList(), emptyMap()))
                val subsFileCid = ipfs.storeData(rsaEnc.encrypt(subscriptions.toByteArray()))
                Log.i(TAG, "Created subs file @ ${subsFileCid.key}")

                acctFile = BailiwickAccount(
                        myPeerId.toBase58(),
                        keyFileCid.key,
                        subsFileCid.key
                )

                val encAcctFile = rsaEnc.encrypt(Gson().toJson(acctFile).toByteArray())

                val acctFileCid = ipfs.storeData(encAcctFile)
                Log.i(TAG, "Created acct file @ ${acctFileCid.key}")
                verCid = ipfs.addLinkToDir(verCid, "account.json", acctFileCid)!!
                dirty = true
            }
        }

        // Create the subscriber-facing manifest
        files.find { l -> l.name == "manifest.json" }.let {
            if (it == null) {
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
                Log.i(TAG, "Stored encrypted manifest @ ${manifestCid.key}")

                verCid = ipfs.addLinkToDir(verCid, "manifest.json", manifestCid)!!
                dirty = true
            }
        }

        // Link all the updated directories back up
        if (dirty) {
            bwCid = ipfs.addLinkToDir(bwCid, VERSION, verCid)!!
            rootCid = ipfs.addLinkToDir(rootCid, "bw", bwCid)!!
        }

        return rootCid.String()
    }

    private fun<T> untilNotNull(function: () -> T?): T {
        var t: T? = null
        while (t == null) {
            t = function()
        }
        return t
    }

    fun initDirectoryStructure(myPeerId: PeerId): String {
        Log.w(TAG, "Initializing Bailiwick structure. This will delete any existing data!!!")
        // Try to resolve the /bw/VERSION directory, but be cautious to respect existing records
        val version = cidForPath(myPeerId, "/bw/$VERSION", sequence)
        val bw = cidForPath(myPeerId, "/bw", sequence)
        val root = cidForPath(myPeerId, "", sequence)
        if (version != null) {
            return root!!.String()
        }

        // Version is null, create it and any other directories we need
        val verCid = ipfs.createEmptyDir()!!
        val bwCid = ipfs.addLinkToDir(bw ?: ipfs.createEmptyDir()!!, VERSION, verCid)!!
        val rootCid = ipfs.addLinkToDir(root ?: ipfs.createEmptyDir()!!, "bw", bwCid)!!

        publishRoot(rootCid.String())

        Log.i(TAG, "Created empty dirs with root: ${rootCid.String()}")

        return rootCid.String()
    }

    fun manifestFor(peerId: PeerId, minSequence: Int): Manifest? {
        return xFor<Manifest>(peerId, "/bw/0.1/manifest.json", minSequence, null)
    }

    fun identityFor(peerId: PeerId, minSequence: Int): Identity? {
        val kp = keyPair
        val encryptor = RSAEncryptor(kp.private, kp.public)

        return xFor<Identity>(peerId, "/bw/0.1/identity.json", minSequence, encryptor)
    }

    fun storeIdentity(myPeerId: PeerId, identity: Identity) {
        val kp = keyPair
        val encryptor = RSAEncryptor(kp.private, kp.public)
        val plaintext = Gson().toJson(identity)
        val ciphertext = encryptor.encrypt(plaintext.toByteArray())

        val cid = ipfs.storeData(ciphertext)
        val newRootCid = addFileToDir(myPeerId, BASE_PATH, "identity.json", cid)
        Log.i(TAG, "Publishing new root cid: ${newRootCid.String()}")

        publishRoot(newRootCid.key)
    }

    private fun currentSequence(peerId: PeerId): Int {
        // Query a few times to get the latest IPNS record, then take our most recent sequence number
        return (0..3).map {
            val ipnsRecord = ipfs.resolveName(peerId.toBase32(), 0, TimeoutCloseable(SHORT_TIMEOUT))
            ipnsRecord?.sequence?.toInt() ?: 0
        }.maxOrNull()!!
    }

    fun publishRoot(newRootCid: String) {
        _sequence += 1
        Log.i(TAG, "Publishing new files with sequence $sequence")
        ipfs.publishName(Cid(newRootCid.toByteArray()), sequence, TimeoutCloseable(LONG_TIMEOUT))
    }

    private inline fun <reified T> xFor(
        peerId: PeerId,
        path: String,
        minSequence: Int,
        encryptor: RSAEncryptor?
    ): T? {
        val cid = cidForPath(peerId, path, minSequence) ?: return null
        val ciphertext = ipfs.getData(cid, TimeoutCloseable(LONG_TIMEOUT))
        val plaintext = String(encryptor?.decrypt(ciphertext) ?: ciphertext)
        return Gson().fromJson(plaintext, T::class.java)
    }

    private fun addFileToDir(peerId: PeerId, path: String, filename: String, content: Cid): Cid {
        Log.i(TAG, "Adding $filename to $path")
        val dirs = path.split("/")
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
            Log.i(TAG, "Linking $name -> ${parent.name} (${newParentCid.String()})")
            name = parent.name
            parent.cid = newParentCid
            cidToAdd = newParentCid
            if (parent.parent == null) {
                break
            }
            parent = parent.parent!!
        }

        return parent.cid
    }

    private fun cidForPath(pid: PeerId, path: String, minSequence: Int): Cid? {
        var ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, TimeoutCloseable(SHORT_TIMEOUT))
        if (ipnsRecord == null) {
            return null
        }

        Log.i(TAG, "hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")

        // TODO: This only works if I already know the expected sequence number.
        while (ipnsRecord!!.sequence < minSequence) {
            ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, TimeoutCloseable(SHORT_TIMEOUT))!!
            Log.i(TAG, "Checking again... hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")
        }

        val link = IPFS.IPFS_PATH + ipnsRecord.hash + path
        Log.i(TAG, "Resolving $link")
        return try {
            ipfs.resolveNode(link, TimeoutCloseable(SHORT_TIMEOUT))?.cid
        } catch (e: ClosedException) {
            Log.e(TAG, "Timeout: ${e.message}")
            null
        }
    }

    private val keyPair: KeyPair
        get() {
            val decoder = Base64.getDecoder()

            val privateKeyData = decoder.decode(privateKeyString)
            val publicKeyData = decoder.decode(publicKeyString)

            val publicKey =
                KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyData))

            val privateKey =
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyData))

            return KeyPair(publicKey, privateKey)
        }

    private val privateKeyString: String?
        get() {
            // Use the IPFS private key
            val sharedPref = context.getSharedPreferences("liteKey", Context.MODE_PRIVATE)
            return sharedPref.getString("privateKey", null)!!
        }

    private val publicKeyString: String?
        get() {
            // Use the IPFS private key
            val sharedPref = context.getSharedPreferences("liteKey", Context.MODE_PRIVATE)
            return sharedPref.getString("publicKey", null)!!
        }

    data class PathNode(val name: String, val parent: PathNode?, var cid: Cid)

    companion object {
        const val TAG = "BWFileManager"
        const val VERSION = "0.2" // TODO use this value everywhere
        const val BASE_PATH = "bw/$VERSION"

        const val SHORT_TIMEOUT = 10L
        const val LONG_TIMEOUT = 30L
    }
}