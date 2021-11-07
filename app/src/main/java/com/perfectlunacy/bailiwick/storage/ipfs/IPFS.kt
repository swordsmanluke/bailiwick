package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import com.perfectlunacy.bailiwick.models.ipfs.Link
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

interface IPFS: IpfsReader, IpfsWriter

interface IpfsReader {
    val peerID: PeerId
    fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray
    fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>?
    fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord?
    fun resolveNode(link: String, timeoutSeconds: Long): ContentId?
    fun resolveNode(root: ContentId, path: String, timeoutSeconds: Long): ContentId?
}

interface IpfsWriter {
    fun bootstrap(context: Context)
    fun storeData(data: ByteArray): ContentId
    fun createEmptyDir(): ContentId?
    fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId?
    fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long)
}

data class IPNSRecord(val retrievedAt: Long, val hash: String, val sequence: Long)