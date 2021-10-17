package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import threads.lite.IPFS
import threads.lite.core.TimeoutCloseable
import java.io.File


class IpfsLiteStore(val ipfs: IPFS, private val context: Context): BailiwickNetwork {
    companion object {
        val TAG = "IpfsLiteStore"
    }

    // TODO: Inject BWFileManager
    val files = BWFileManager(ipfs, context)

    override fun myId(): String {
        return ipfs.peerID.toBase32()
    }

    override fun newAccount(username: String, passwordHash: String): Account {
        val rootCid = files.initializeBailiwick(ipfs.peerID)
        Log.i(BWFileManager.TAG, "Created Bailiwick structure. Publishing...")
        // Finally, publish the baseDir to IPNS
        files.publishRoot(rootCid)

        return Account(
            username, passwordHash,
            ipfs.peerID.toBase32(),
            rootCid,
            files.sequence,
            false)
    }

    override fun store(data: String): String {
        val cid = ipfs.storeData(data.toByteArray())
        return cid.key
    }

    override fun publish_posts(data: String): String {
        val cid = ipfs.storeData(data.toByteArray())
        ipfs.publishName(cid, 1, TimeoutCloseable(30))
        return cid.key
    }

    override fun retrieve(key: String): String {
        val cid = ipfs.resolve(key, TimeoutCloseable(30))
        return ipfs.getText(cid!!, TimeoutCloseable(30)) ?: ""
    }

    override fun retrieve_posts(key: String): String {
        val cid = ipfs.resolve(key, TimeoutCloseable(30))

        return retrieve(cid!!.key)
    }

    override fun retrieve_file(key: String): File? {
        TODO("Not yet implemented")
    }

    // TODO: This should be managed externally.
    override var identity: Identity?
        get() {
            return files.identityFor(ipfs.peerID, files.sequence)
        }
        set(value) {
            //TODO: Write Identity to a file <PEERID>/bw/v0.1/identity <-- IPLD?
            Log.i(TAG, "Trying to create identity with ${value}")
            files.storeIdentity(ipfs.peerID, value!!)
        }

}