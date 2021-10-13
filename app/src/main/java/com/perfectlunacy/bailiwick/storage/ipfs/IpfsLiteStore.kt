package com.perfectlunacy.bailiwick.storage.ipfs

import com.google.gson.Gson
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import java.io.File
import threads.lite.IPFS
import threads.lite.core.Closeable
import threads.lite.core.TimeoutCloseable


class IpfsLiteStore(val ipfs: IPFS, private val peer_id: String): BailiwickNetwork, Closeable {
    companion object {
        val TAG = "IpfsLiteStore"
    }

    private var sequence = 0L

    private val version = "0.1"
    private val baseIPNS = "/ipns/$peer_id/bw/$version"

    override fun myId(): String {
        return peer_id
    }

    override fun store(data: String): String {
        val cid = ipfs.storeData(data.toByteArray())
        return cid.key
    }

    override fun publish_posts(data: String): String {
        val cid = ipfs.storeData(data.toByteArray())
        ipfs.publishName(cid, 1, this)
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
    override var identity: Identity
        get(){
            val cid = ipfs.resolve("$baseIPNS/identity",TimeoutCloseable(10))?.key
            return if (cid.isNullOrBlank()){
                // FIXME: if we have no identity, we should return something else
                Identity("", "", null)
            } else {
                val identityJson = retrieve(String(cid.toByteArray()))
                return if (identityJson.isBlank()) {
                    // FIXME: if we have no identity, we should return something else
                    Identity("", "", null)
                } else {
                    Gson().fromJson(identityJson, Identity::class.java)

                }
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

    override fun isClosed(): Boolean {
        return false
    }

    private data class User(val pid: String)
}