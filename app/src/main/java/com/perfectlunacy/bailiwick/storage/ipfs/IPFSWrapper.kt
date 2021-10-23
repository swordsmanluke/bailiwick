package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.google.common.io.BaseEncoding
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import threads.lite.cid.Cid
import threads.lite.core.ClosedException
import threads.lite.core.TimeoutCloseable
import threads.lite.utils.Link
import java.util.*

class IPFSWrapper(private val ipfs: threads.lite.IPFS): IPFS {
    companion object {
        const val TAG = "IPFSWrapper"
    }

    fun String.toCid(): Cid {
        Log.d(TAG, "Converting $this to a Cid")
        return try {
            Cid.decode(this)
        } catch(e: IllegalStateException) {
            Cid(BaseEncoding.base32().decode(this))
        }
    }

    override fun bootstrap(context: Context){
        // TODO: Move this into splashscreen?
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val linkProperties = connectivityManager.getLinkProperties(network)!!
        val interfaceName = linkProperties.interfaceName
        ipfs.updateNetwork(interfaceName!!)

        ipfs.bootstrap()
        Log.i(TAG, "Bootstrap completed.")
    }

    override val peerID: PeerId
        get() = ipfs.peerID.toBase32()

    override fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray {
        Log.i(TAG, "GetData: $cid")
        return ipfs.getData(cid.toCid(), TimeoutCloseable(timeoutSeconds))
    }

    override fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>? {
        Log.i(TAG, "getLinks: $cid")
        return try {
            ipfs.getLinks(cid.toCid(), resolveChildren, TimeoutCloseable(timeoutSeconds))
        } catch (e: ClosedException) {
            Log.e(TAG, "timeout retrieving $cid")
            null
        }
    }

    override fun storeData(data: ByteArray): ContentId {
        val cid = ipfs.storeData(data).key
        Log.i(TAG, "stored data: $cid")
        return cid
    }

    override fun createEmptyDir(): ContentId? {
        return ipfs.createEmptyDir()!!.key.also{
            Log.i(TAG, "Created new empty dir @$it")
        }
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        return ipfs.addLinkToDir(dirCid.toCid(), name, cid.toCid())?.key.also {
            Log.i(TAG, "Linked $it -> $name ($cid)")
        }
    }

    override fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord? {
        val rec = ipfs.resolveName(peerId, sequence, TimeoutCloseable(timeoutSeconds)) ?: return null
        Log.i(TAG, "Resolved $peerId:$sequence to ${rec.hash}:${rec.sequence}")
        return IPNSRecord(Calendar.getInstance().timeInMillis, rec.hash, rec.sequence)
    }

    override fun resolveNode(link: String, timeoutSeconds: Long): ContentId? {
        return try {
            ipfs.resolveNode(link, TimeoutCloseable(timeoutSeconds))?.cid?.key.also {
                Log.i(TAG, "resolved path $link to node: $it")
            }
        } catch(e: Exception) {
            Log.e(TAG,"Failed to resolve $link: $e\n${e.stackTraceToString()}")
            null
        }

    }

    override fun resolveNode(root: ContentId, path: String, timeoutSeconds: Long): ContentId? {
        return try {
            ipfs.resolveNode(root.toCid(), path.split("/").filter { it.isNotBlank() }, TimeoutCloseable(timeoutSeconds))?.cid?.key.also {
                Log.i(TAG, "resolved path $path to node: $it")
            }
        } catch(e: Exception) {
            Log.e(TAG,"Failed to resolve $path: $e\n" +
                    "${e.stackTraceToString()}")
            null
        }

    }

    override fun publishName(root: ContentId, sequence: Int, timeoutSeconds: Long) {
        Log.i(TAG, "Publishing new root cid: $root:$sequence")
        ipfs.publishName(root.toCid(), sequence, TimeoutCloseable(timeoutSeconds))
    }
}