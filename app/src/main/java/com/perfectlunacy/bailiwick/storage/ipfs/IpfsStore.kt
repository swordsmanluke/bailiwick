package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.Link
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

/***
 * Wraps IPFS network _and_ the cache layer in order to provide a single object
 * which can be passed to whatever needs it.
 */
class IpfsStore(private val cache: IPFSCache, private val ipfs: IPFS, private val ipns: IPNS) : IPNS, IPFS, IPFSCacheWriter, IPFSCacheReader {
    override val peerID: PeerId
        get() = ipfs.peerID

    override fun bootstrap(context: Context) {
        ipfs.bootstrap(context)
    }

    override fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray {
        if(cache.cacheContains(cid)) {
            return cache.downloadFromCache(cid)!!
        }

        val data = ipfs.getData(cid, timeoutSeconds)
        cache.putInCache(cid, data)
        return data
    }

    override fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>? {
        return ipfs.getLinks(cid, resolveChildren, timeoutSeconds)
    }

    override fun storeData(data: ByteArray): ContentId {
        val cid = ipfs.storeData(data)
        cache.putInCache(cid, data)
        return cid
    }

    override fun createEmptyDir(): ContentId? {
        return ipfs.createEmptyDir()
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        return ipfs.addLinkToDir(dirCid, name, cid)
    }

    override fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord? {
        return ipfs.resolveName(peerId, sequence, timeoutSeconds)
    }

    override fun resolveNode(link: String, timeoutSeconds: Long): ContentId? {
        return ipfs.resolveNode(link, timeoutSeconds)
    }

    override fun resolveNode(root: ContentId, path: String, timeoutSeconds: Long): ContentId? {
        return ipfs.resolveNode(root, path, timeoutSeconds)
    }

    override fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long) {
        ipfs.publishName(root, sequence, timeoutSeconds)
    }

    /*** Cache functions ***/

    override fun cacheContains(cid: ContentId): Boolean {
        return cache.cacheContains(cid)
    }

    override fun putInCache(cid: ContentId, content: ByteArray) {
        cache.putInCache(cid, content)
    }

    override fun <T> retrieveFromCache(cid: ContentId, cipher: Encryptor, clazz: Class<T>): T? {
        return cache.retrieveFromCache(cid, cipher, clazz)
    }

    override fun downloadFromCache(cid: ContentId): ByteArray? {
        return cache.downloadFromCache(cid)
    }

    override fun publishRoot(root: ContentId) {
        ipns.publishRoot(root)
    }

    override fun cidForPath(peerId: PeerId, path: String, minSequence: Int): ContentId? {
        return ipns.cidForPath(peerId, path, minSequence)
    }

    override fun sequenceFor(peerId: PeerId): Long {
        return ipns.sequenceFor(peerId)
    }
}