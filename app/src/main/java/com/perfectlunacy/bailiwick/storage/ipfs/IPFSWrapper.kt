package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.google.common.io.BaseEncoding
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import threads.lite.cid.Cid
import threads.lite.core.TimeoutCloseable
import threads.lite.utils.Link
import java.util.*

class IPFSWrapper(private val ipfs: threads.lite.IPFS): IPFS {
    companion object {
        const val TAG = "IPFSWrapper"
    }

    fun String.toCid(): Cid {
        return Cid(BaseEncoding.base32().decode(this))
    }

    override fun bootstrap(context: Context){
        // TODO: Move this into splashscreen?
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val linkProperties = connectivityManager.getLinkProperties(network)!!
        val interfaceName = linkProperties.interfaceName
        ipfs.updateNetwork(interfaceName!!)

        ipfs.bootstrap()
        Log.i("IPFSWrapper", "Relays: ${ipfs.relays}")
        Log.i("IPFSWrapper", "Bootstrap completed.")
    }

    override val peerID: PeerId
        get() = ipfs.peerID.toBase32()

    override fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray {
        return ipfs.getData(cid.toCid(), TimeoutCloseable(timeoutSeconds))
    }

    override fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>? {
        return ipfs.getLinks(cid.toCid(), resolveChildren, TimeoutCloseable(timeoutSeconds))
    }

    override fun storeData(data: ByteArray): ContentId {
        return ipfs.storeData(data).key
    }

    override fun createEmptyDir(): ContentId? {
        return ipfs.createEmptyDir()!!.key.also{
            Log.i(TAG, "Created new empty dir @$it")
        }
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        return ipfs.addLinkToDir(dirCid.toCid(), name, cid.toCid())?.key
    }

    override fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord? {
        val rec = ipfs.resolveName(peerId, sequence, TimeoutCloseable(timeoutSeconds)) ?: return null

        return IPNSRecord(rec.hash, rec.sequence)
    }

    override fun resolveNode(link: String, timeoutSeconds: Long): ContentId? {
        return ipfs.resolveNode(link, TimeoutCloseable(timeoutSeconds))?.cid?.key
    }

    override fun resolveNode(root: ContentId, path: MutableList<String>, timeoutSeconds: Long): ContentId? {
        return ipfs.resolveNode(root.toCid(), path, TimeoutCloseable(timeoutSeconds))?.cid?.key
    }

    override fun publishName(root: ContentId, sequence: Int, timeoutSeconds: Long) {
        ipfs.publishName(root.toCid(), sequence, TimeoutCloseable(timeoutSeconds))
    }
}