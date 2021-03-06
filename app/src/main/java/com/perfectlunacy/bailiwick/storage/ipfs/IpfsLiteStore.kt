package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.ipfs.lite.CID
import com.perfectlunacy.bailiwick.storage.ipfs.lite.Closeable
import com.perfectlunacy.bailiwick.storage.ipfs.lite.IPFS
import java.io.File


class IpfsLiteStore(val ipfs: IPFS, private val peer_id: String): BailiwickNetwork, Closeable {
    companion object {
        val TAG = "IpfsLiteStore"
    }

    private var sequence = 0L

    private val version = "0.1"
    private val baseIPNS = "$peer_id/bw/$version/"

    override fun myId(): String {
        return peer_id
    }

    override fun store(data: String): String {
        return ipfs.storeData(data.toByteArray())!!.cid
    }

    override fun publish_posts(data: String): String {
        val cid = ipfs.storeData(data.toByteArray())!!
        ipfs.publishName(cid, this, { 1 })

        val ipnsRec = ipnsRecord(cid.cid, peer_id)

        // TODO: store and use this
        val usersIKnow: List<User> = emptyList()

        for(user in usersIKnow) {
            val success = ipfs.push(user.pid, ipnsRec)
            if (!success) { Log.e(TAG, "Failed to push IPNS record to ${user.pid}") }
        }

        return ipfs.id()?.id!!
    }

    override fun retrieve(key: String): String {
        return ipfs.getText(CID(key)) ?: ""
    }

    override fun retrieve_posts(key: String): String {
        val resName = ipfs.resolveName(key, 100, this)
        val cid = resName?.hash ?: return ""

        return retrieve(cid)
    }

    override fun retrieve_file(key: String): File? {
        TODO("Not yet implemented")
    }

    // TODO: This should be managed externally.
    override var identity: Identity
        get(){
            val identityJson = retrieve("$baseIPNS/identity")
            return if (identityJson.isBlank()) {
                // FIXME: if we have no identity, we should return something else
                Identity("", "", null)
            } else {
                Gson().fromJson(identityJson, Identity::class.java)
            }
        }
        set(value) {
            //TODO: Write Identity to a file <PEERID>/bw/v0.1/identity <-- IPLD?
        }

    private fun ipnsRecord(
        cid: String,
        host: String?
    ): HashMap<String?, String?> {
        val hashMap: HashMap<String?, String?> = HashMap()
        hashMap["ipns"] = cid
        hashMap["pid"] = host // TODO remove in the future
        hashMap["seq"] = "" + sequence
        return hashMap
    }

    override val isClosed: Boolean
        get() = false // Let's see what this does

    private data class User(val pid: String)
}