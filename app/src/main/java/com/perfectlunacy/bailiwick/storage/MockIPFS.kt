package com.perfectlunacy.bailiwick.storage

import android.content.Context
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.ipfs.*
import threads.lite.utils.Link
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.Path

class MockIPFS(val filesDir: String) : IPFS {
    override val peerID: PeerId
        get() = "myPeerId"

    override fun bootstrap(context: Context) { }

    override fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray {
        val file = File(cid)
        if (file.exists() && file.isFile) {
            return FileInputStream(file).readBytes()
        }
        throw RuntimeException("Unknown CID: $cid")
    }

    override fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): MutableList<Link>? {
        TODO("No impl")
    }

    override fun storeData(data: ByteArray): ContentId {
        val name = String(data).hashCode().toString() + ".hash"
        val file = File(basePath, name)
        val stream = FileOutputStream(file)
        try { stream.write(data) }
        finally { stream.close() }

        return file.path
    }

    override fun createEmptyDir(): ContentId? {
        return "/tmp" // This won't really matter
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        val source = File(cid)
        val destination = File(dirCid)

        if(source.exists()) {
            if(!destination.exists()) {
                destination.mkdirs()
            }
            // Copy so that we can find the data both by CID and by path+name
            // It's all a mock anyway, it's fine if it's storing things a little extra
            Files.copy(Path(cid), Path("$destination/$name"), StandardCopyOption.REPLACE_EXISTING)
        }

        // In IPFS, the dir CID changes after
        // making a change like this, but not
        // in our mock environment.
        return dirCid
    }

    override fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord? {
        return if(peerId == this.peerID) {
            IPNSRecord(basePath, sequence)
        } else {
            IPNSRecord("$filesDir/$peerId/bw/0.1/", sequence)
        }
    }

    override fun resolveNode(link: String, timeoutSeconds: Long): ContentId? {
        return link
    }

    override fun resolveNode(root: ContentId, path: MutableList<String>,timeoutSeconds: Long): ContentId? {
        return resolveNode(path.joinToString("/"), timeoutSeconds)
    }

    private var _root: ContentId = "/tmp"
    override fun publishName(root: ContentId, sequence: Int, timeoutSeconds: Long) {
        _root = root
    }

    val basePath: String
        get() {
            val path = filesDir + "/bw/0.1/"
            File(path).also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            return path
        }
}