package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.RSAEncryptor
import threads.lite.IPFS
import threads.lite.cid.Cid
import threads.lite.cid.PeerId
import threads.lite.core.ClosedException
import threads.lite.core.TimeoutCloseable
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

class BWFileManager(private val ipfs: IPFS, private val context: Context) {

    private var _sequence: Int = currentSequence(ipfs.peerID)
    val sequence: Int
        get() = _sequence

    fun initBase(myPeerId: PeerId) {
        if(cidForPath(myPeerId, "/bw", sequence) != null) {
            Log.i(TAG, "/bw already exists - skipping initialization")
            return
        }

        Log.w(TAG, "Initializing Bailiwick structure. This will delete any existing data!!!")
        val versionCid = ipfs.createEmptyDir()!!
        val bwDir = ipfs.createEmptyDir()!!
        val bwCid = ipfs.addLinkToDir(bwDir, VERSION, versionCid)!!
        val root = ipfs.createEmptyDir()!!
        val rootCid = ipfs.addLinkToDir(root, "bw", bwCid)!!
        Log.i(TAG, "Created Bailiwick structure. Publishing...")

        // Finally, publish the baseDir to IPNS
        publishRoot(myPeerId, rootCid)
        Log.i(TAG, "Published empty dirs with root: ${rootCid.String()}")
    }

    fun manifestFor(peerId: PeerId, minSequence: Int): Manifest? {
        return xFor<Manifest>(peerId,"/bw/0.1/manifest.json", minSequence, null)
    }

    fun identityFor(peerId: PeerId, minSequence: Int): Identity? {
        val kp = keyPair
        val encryptor = RSAEncryptor(kp.private, kp.public)

        return xFor<Identity>(peerId,"/bw/0.1/identity.json", minSequence, encryptor)
    }

    fun storeIdentity(myPeerId: PeerId, identity: Identity) {
        val kp = keyPair
        val encryptor = RSAEncryptor(kp.private, kp.public)
        val plaintext = Gson().toJson(identity)
        val ciphertext = encryptor.encrypt(plaintext.toByteArray())

        val cid = ipfs.storeData(ciphertext)
        val newRootCid = addFileToDir(myPeerId, BASE_PATH, "identity.json", cid)
        Log.i(TAG, "Publishing new root cid: ${newRootCid.String()}")

        publishRoot(myPeerId, newRootCid)
    }

    private fun currentSequence(peerId: PeerId): Int {
        // Query a few times to get the latest IPNS record, then take our most recent sequence number
        return (0..3).map {
            val ipnsRecord = ipfs.resolveName(peerId.toBase32(), 0, TimeoutCloseable(SHORT_TIMEOUT))!!
            ipnsRecord.sequence.toInt()
        }.maxOrNull()!!
    }

    private fun publishRoot(myPeerId: PeerId, newRootCid: Cid) {
        _sequence += 1
        Log.i(TAG, "Publishing new files with sequence $sequence")
        ipfs.publishName(newRootCid, sequence, TimeoutCloseable(LONG_TIMEOUT))
    }

    private inline fun <reified T> xFor(peerId: PeerId, path: String, minSequence: Int, encryptor: RSAEncryptor?): T? {
        val cid = cidForPath(peerId, path, minSequence) ?: return null
        val ciphertext = ipfs.getData(cid, TimeoutCloseable(LONG_TIMEOUT))
        val plaintext = String(encryptor?.decrypt(ciphertext) ?: ciphertext)
        return Gson().fromJson(plaintext, T::class.java)
    }

    private fun addFileToDir(peerId: PeerId, path: String, filename: String, content: Cid): Cid {
        Log.i(TAG,"Adding $filename to $path")
        val dirs = path.split("/")
        var dir = ""
        val root = PathNode("/",null, cidForPath(peerId, "", sequence)!!)
        var parent = root

        // collect the list of dirs to cids
        dirs.forEach{ d ->
            dir += "/$d"
            val cid = cidForPath(peerId, dir, sequence)!!
            val newNode = PathNode(d, parent, cid)
            parent = newNode
        }

        // add a file to its parent... and then update every one of its parents
        var cidToAdd = content
        var name = filename
        while(true) {
            val newParentCid = ipfs.addLinkToDir(parent.cid, name, cidToAdd)!!
            Log.i(TAG, "Linking $name -> ${parent.name} (${newParentCid.String()})")
            name = parent.name
            parent.cid = newParentCid
            cidToAdd = newParentCid
            if(parent.parent == null) { break }
            parent = parent.parent!!
        }

        return parent.cid
    }

    private fun cidForPath(pid: PeerId, path: String, minSequence: Int): Cid? {
        var ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, TimeoutCloseable(SHORT_TIMEOUT))!!
        Log.i(TAG, "hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")

        // TODO: This only works if I already know the expected sequence number.
        while (ipnsRecord.sequence < minSequence) {
            ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, TimeoutCloseable(SHORT_TIMEOUT))!!
            Log.i(TAG, "Checking again... hash: ${ipnsRecord.hash}  seq: ${ipnsRecord.sequence}")
        }

        val link = IPFS.IPFS_PATH + ipnsRecord.hash + path
        Log.i(TAG, "Resolving $link")
        return try {
            ipfs.resolveNode(link, TimeoutCloseable(SHORT_TIMEOUT))?.cid
        } catch(e: ClosedException) {
            Log.e(TAG, "Timeout: ${e.message}")
            null
        }
    }

    private val keyPair: KeyPair
        get() {
            val decoder = Base64.getDecoder()

            val privateKeyData = decoder.decode(privateKeyString)
            val publicKeyData = decoder.decode(publicKeyString)

            val publicKey = KeyFactory.getInstance("RSA").
                                generatePublic(X509EncodedKeySpec(publicKeyData))

            val privateKey = KeyFactory.getInstance("RSA").
                                generatePrivate(PKCS8EncodedKeySpec(privateKeyData))

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

    data class PathNode(val name: String, val parent: PathNode?, var cid: Cid);

    companion object {
        const val TAG = "BWFileManager"
        const val VERSION = "0.1" // TODO use this value everywhere
        const val BASE_PATH = "bw/$VERSION"

        const val SHORT_TIMEOUT = 10L
        const val LONG_TIMEOUT = 30L
    }
}