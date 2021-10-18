package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import threads.lite.utils.Link // TODO: hide this implementation detail

interface IPFS {
    val peerID: PeerId
    fun bootstrap(context: Context)
    fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray
    fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>?
    fun storeData(data: ByteArray): ContentId
    fun createEmptyDir(): ContentId?
    fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId?
    fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord?
    fun resolveNode(link: String, timeoutSeconds: Long): ContentId?
    fun resolveNode(root: ContentId, path: MutableList<String>, timeoutSeconds: Long): ContentId?
    fun publishName(root: ContentId, sequence: Int, timeoutSeconds: Long)
}

data class IPNSRecord(val hash: String, val sequence: Long)