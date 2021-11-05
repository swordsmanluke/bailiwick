package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.models.Link
import com.perfectlunacy.bailiwick.models.LinkType
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPNSRecord
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.Path

class MockIPFS(val filesDir: String) : IPFS {
    companion object {
        const val TAG = "MockIPFS"
    }

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
        val dir = cid.split("/").last()
        return when(dir) {
            "files","" -> { listOf(Link("bw", "bw", LinkType.Dir)) }
            "bw" -> { listOf(Link("0.2", "bw/0.2", LinkType.Dir)) }
            "0.2" -> { listOf(
                Link("manifest.json", "bw/0.2/manifest.json", LinkType.File),
                Link("identity.json", "bw/0.2/identity.json", LinkType.File),
                Link("circles.json", "bw/0.2/circles.json", LinkType.File),
                Link("subscriptions.json", "bw/0.2/subscriptions.json", LinkType.File),
            )}
            else -> { null }
        }?.toMutableList()
    }

    override fun storeData(data: ByteArray): ContentId {
        val name = String(data).hashCode().toString() + ".hash"
        val file = File(basePath, name)
        File(basePath).mkdirs() // Just in case
        Log.i(TAG, "Storing data at ${file.path}")

        val stream = FileOutputStream(file)
        try { stream.write(data) }
        finally { stream.close() }

        return file.path
    }

    override fun createEmptyDir(): ContentId? {
        return filesDir // This won't really matter
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        val source = File(cid)
        val destination = File(dirCid)

        if(source.exists() && source.isFile) {
            if(!destination.exists()) {
                destination.mkdirs()
            }
            // Copy so that we can find the data both by CID and by path+name
            // It's all a mock anyway, it's fine if it's storing things a little extra
            Log.i(TAG, "Copying $cid to $destination/$name")
            Files.copy(Path(cid), Path("$destination/$name"), StandardCopyOption.REPLACE_EXISTING)
        }

        // In IPFS, the dir CID changes after
        // making a change like this, but not
        // in our mock environment.
        return dirCid
    }

    override fun resolveName(peerId: PeerId, sequence: Long, timeoutSeconds: Long): IPNSRecord? {
        return if(peerId == this.peerID) {
            IPNSRecord(Calendar.getInstance().timeInMillis, filesDir, _seq.toLong())
        } else {
            IPNSRecord(Calendar.getInstance().timeInMillis, "$filesDir/$peerId", _seq.toLong())
        }
    }

    override fun resolveNode(link: String, timeoutSeconds: Long): ContentId? {
        val link = if(link.startsWith("/ipfs/")) {
            link.slice(6 until link.length)
        } else {
            link
        }

        return if(File(link).exists() || File(link).isDirectory) {
            link
        } else if(File(filesDir+"/"+Path(link).fileName).exists()) {
            filesDir + "/" + Path(link).fileName
        } else {
            Log.i(TAG, "No such file: $link")
            null
        }
    }

    override fun resolveNode(root: ContentId, path: String, timeoutSeconds: Long): ContentId? {
        return resolveNode(filesDir + "/" + path, timeoutSeconds)
    }

    private var _root: ContentId = filesDir
    private var _seq: Long = 0
    override fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long) {
        _root = root
        _seq = sequence
    }

    val basePath: String
        get() {
            val path = filesDir + "/bw/0.2/"
            File(path).also {
                if (!it.exists()) {
                    Log.i(TAG, "Creating base dirs: ${filesDir+"/bw/0.2"}")
                    it.mkdirs()
                }
            }
            return path
        }
}