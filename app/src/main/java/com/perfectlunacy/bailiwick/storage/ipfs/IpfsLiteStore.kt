package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.storage.DistHashTable
import com.perfectlunacy.bailiwick.storage.ipfs.lite.CID
import com.perfectlunacy.bailiwick.storage.ipfs.lite.Closeable
import com.perfectlunacy.bailiwick.storage.ipfs.lite.IPFS


class IpfsLiteStore(val ipfs: IPFS, private val peer_id: String): DistHashTable, Closeable {
    companion object {
        val TAG = "IpfsLiteStore"
    }

    private var sequence = 0L

    override fun myId(): String {
        return peer_id
    }

    override fun store(data: String): String {
        return ipfs.storeData(data.toByteArray())!!.cid
    }

    override fun updateIdentity(name: String) {
        val idMap = HashMap<String, String>()
        idMap.put("name", name)
        val data = Gson().toJson(idMap)

        // TODO: This is wrong.

        val cid = ipfs.storeData(data.toByteArray())
        ipfs.publishName(CID("${myId()}/identity"), this, { 1 })
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

    override val identity: Identity
        get(){
            val identityJson = retrieve("${myId()}/identity")
            return if (identityJson.isBlank()) {
                Identity("")
            } else {
                Gson().fromJson(identityJson, Identity::class.java)
            }
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