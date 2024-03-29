package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import com.perfectlunacy.bailiwick.models.db.SequenceDao
import com.perfectlunacy.bailiwick.models.ipfs.Link
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.security.PrivateKey
import java.security.PublicKey

interface IPFS: IpfsReader, IpfsWriter

interface IpfsReader {
    val peerID: PeerId
    val publicKey: PublicKey
    val privateKey: PrivateKey
    fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray
    fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>?
    fun resolveName(peerId: PeerId, sequenceDao: SequenceDao, timeoutSeconds: Long): IPNSRecord?
    fun resolveNode(link: String, timeoutSeconds: Long): ContentId?
    fun resolveNode(root: ContentId, path: String, timeoutSeconds: Long): ContentId?
    fun resolveBailiwickFile(rootCid: ContentId, filename: String, timeoutSeconds: Long): ContentId?
}

interface IpfsWriter {
    fun bootstrap(context: Context)
    fun storeData(data: ByteArray): ContentId
    fun createEmptyDir(): ContentId?
    fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId?
    fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long)
    fun provide(cid: ContentId, timeoutSeconds: Long)
    fun isConnected(): Boolean
}

data class IPNSRecord(val retrievedAt: Long, val hash: String, val sequence: Long)