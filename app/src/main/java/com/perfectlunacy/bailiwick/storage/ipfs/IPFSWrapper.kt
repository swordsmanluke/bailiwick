package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.util.Log
import com.google.common.io.BaseEncoding
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.models.ipfs.Link
import com.perfectlunacy.bailiwick.models.ipfs.LinkType
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import threads.lite.cid.Cid
import threads.lite.core.ClosedException
import threads.lite.core.TimeoutCloseable
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import android.net.NetworkCapabilities
import com.perfectlunacy.bailiwick.models.db.Sequence
import com.perfectlunacy.bailiwick.models.db.SequenceDao
import com.perfectlunacy.bailiwick.workers.IpfsPublishWorker


class IPFSWrapper(private val ipfs: threads.lite.IPFS, val keyPair: KeyPair): IPFS {
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val linkProperties = connectivityManager.getLinkProperties(network)!!
        val interfaceName = linkProperties.interfaceName
        ipfs.updateNetwork(interfaceName!!)

        val request = NetworkRequest.Builder()
        request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        request.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)

        connectivityManager.registerNetworkCallback(request.build(), object:ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network: Network) {
                ipfs.bootstrap()
                ipfs.relays(threads.lite.IPFS.TIMEOUT_BOOTSTRAP.toLong())
                Log.i(TAG, "Bootstrap completed.")

                IpfsPublishWorker.enqueue(context)
            }
        })
    }

    override val peerID: PeerId
        get() = ipfs.peerID.toBase58()

    override val publicKey: PublicKey
        get() = keyPair.public

    override val privateKey: PrivateKey
        get() = keyPair.private

    override fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray {
        Log.i(TAG, "GetData: $cid")
        return ipfs.getData(cid.toCid(), TimeoutCloseable(timeoutSeconds))
    }

    override fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>? {
        Log.i(TAG, "getLinks: $cid")
        return try {
            val links = ipfs.getLinks(cid.toCid(), resolveChildren, TimeoutCloseable(timeoutSeconds))?.map{
                Link(it.name, it.cid.key, if(it.isDirectory) { LinkType.Dir } else { LinkType.File } )
            }?.toMutableList()

            Log.i(TAG, "Found links ${links?.map{it.name}?.joinToString(",") } for $cid" )

            links
        } catch (e: ClosedException) {
            Log.e(TAG, "timeout retrieving $cid")
            null
        }
    }

    override fun storeData(data: ByteArray): ContentId {
        val cid = ipfs.storeData(data)
        ipfs.provide(cid, TimeoutCloseable(30))
        Log.i(TAG, "stored data: ${cid.String()}")
        return cid.String()
    }

    override fun createEmptyDir(): ContentId? {
        return ipfs.createEmptyDir()!!.key.also{
            Log.i(TAG, "Created new empty dir @$it")
        }
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        return ipfs.addLinkToDir(dirCid.toCid(), name, cid.toCid())?.key.also {
            Log.i(TAG, "Linked $dirCid -> $name ($cid): $it")
        }
    }

    override fun resolveName(peerId: PeerId, sequenceDao: SequenceDao, timeoutSeconds: Long): IPNSRecord? {
        val seq = sequenceDao.find(peerId).let {
            if(it == null) {
                val seq = Sequence(peerId, 0)
                sequenceDao.insert(seq) // TODO: Make this update only after successful downloads
                seq
            } else {
                it
            }
        }

        var rec = ipfs.resolveName(peerId, seq.sequence, TimeoutCloseable(timeoutSeconds))
        Log.i(TAG, "Resolved $peerId:${seq.sequence} to '${rec?.hash ?: "null"}':${rec?.sequence ?: ""}")
        var tries = 0
        while ((rec == null || rec.hash.isBlank()) && tries < 10) {
            tries += 1
            rec = ipfs.resolveName(peerId, seq.sequence, TimeoutCloseable(timeoutSeconds))
            Log.i(TAG, "Resolved $peerId:${seq.sequence} to '${rec?.hash ?: ""}':${rec?.sequence ?: 0}")
        }
        if(rec == null) {
            Log.w(TAG, "Failed to resolve $peerId:${seq.sequence}")
            return null
        }

        if(rec.sequence > seq.sequence) {
            Log.i(TAG, "Updating $peerId sequence to ${rec.sequence}")
            sequenceDao.updateSequence(peerId, rec.sequence)
        }

        return IPNSRecord(Calendar.getInstance().timeInMillis, rec.hash, rec.sequence)
    }

    override fun resolveBailiwickFile(rootCid: ContentId, filename: String, timeoutSeconds: Long): ContentId? {
        val timeout = timeoutSeconds / 4 // Keep the total timeout as expected
        Log.i(TAG, "Searching links for $filename")
        try {
            val links = ipfs.getLinks(rootCid.toCid(), true, TimeoutCloseable(timeout))
            if(links == null) {
                Log.e(TAG, "Failed to locate ipns root links")
                return null
            }

            val displayLinks: (String, List<threads.lite.utils.Link>) -> String = {header, links ->
                "$header links: [${links.map { "${it.name}: ${it.cid.String()}" }.joinToString(", ")}]"
            }

            Log.i(TAG, displayLinks.invoke("Root", links))
            val bwCid = links.find { it.name == "bw" }
            if(bwCid == null) {
                Log.e(TAG, "Failed to locate bwCid")
                return null
            }

            val bwLinks = ipfs.getLinks(bwCid.cid, true, TimeoutCloseable(timeout))
            if(bwLinks == null) {
                Log.e(TAG, "Failed to locate bw links")
                return null
            }

            Log.i(TAG, displayLinks.invoke("/bw", bwLinks))
            val verCid = bwLinks.find { it.name == Bailiwick.VERSION }
            if(verCid == null) {
                Log.e(TAG, "Failed to locate ipfs links")
                return null
            }

            val verLinks = ipfs.getLinks(verCid.cid, true, TimeoutCloseable(timeout)) ?: return null
            Log.i(TAG, displayLinks.invoke("/bw/0.2", verLinks))

            return verLinks.find { it.name == filename }?.cid?.String()
        } catch(e: Exception){
            Log.e(TAG, "Failed to resolve file '$filename'", e)
            return null
        }
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
            val paths = path.split("/").filter { it.isNotBlank() }
            ipfs.resolveNode(root.toCid(), paths, TimeoutCloseable(timeoutSeconds))?.cid?.key.also {
                Log.i(TAG, "resolved path $path to node: $it")
            }
        } catch(e: Exception) {
            Log.e(TAG,"Failed to resolve $path: $e\n" +
                    e.stackTraceToString()
            )
            null
        }

    }

    override fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long) {
        Log.i(TAG, "Publishing new root cid: $root:$sequence")
        ipfs.publishName(root.toCid(), sequence.toInt(), TimeoutCloseable(timeoutSeconds))
    }

    override fun provide(cid: ContentId, timeoutSeconds: Long) {
        ipfs.provide(cid.toCid(), TimeoutCloseable(timeoutSeconds))
    }

    override fun isConnected(): Boolean{
        Log.i(TAG, "Connected peers: ${ipfs.peers.size}")
        return ipfs.peers.size >= 5
    }


}