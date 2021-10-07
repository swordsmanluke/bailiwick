package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import java.io.File
import threads.lite.IPFS
import threads.lite.cid.Cid
import threads.lite.cid.PeerId
import threads.lite.core.Closeable
import java.lang.RuntimeException


class IpfsLiteStore(val ipfs: IPFS, private val peer_id: String): BailiwickNetwork, Closeable {
    companion object {
        val TAG = "IpfsLiteStore"
    }

    private var sequence = 0L

    private val version = "0.1"
    private val baseIPNS = "/ipfs/$peer_id/bw/$version"

    override fun myId(): String {
        return peer_id
    }

    override fun store(data: String): Cid {
        return ipfs.storeData(data.toByteArray())
    }

    override fun publish_posts(data: String): Cid {
        val cid = ipfs.storeData(data.toByteArray())
        ipfs.publishName(cid, 1, this)
        return cid
    }

    override fun retrieve(key: Cid): String {
        return ipfs.getText(key, this) ?: ""
    }

    override fun retrieve_posts(key: String): String {
        val resName = ipfs.resolveName(key, 100, this)
        val cid = resName?.hash ?: return ""

        return retrieve(Cid(cid.toByteArray()))
    }

    override fun retrieve_file(key: Cid): File? {
        TODO("Not yet implemented")
    }

    // TODO: This should be managed externally.
    override var identity: Identity
        get(){
            val resName = ipfs.resolveName("$baseIPNS/identity", 100, this)
            val cid: String? = resName?.hash
            return if (cid.isNullOrBlank()){
                // FIXME: if we have no identity, we should return something else
                Identity("", "", null)
            } else {
                val identityJson = retrieve(Cid(cid.toByteArray()))
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