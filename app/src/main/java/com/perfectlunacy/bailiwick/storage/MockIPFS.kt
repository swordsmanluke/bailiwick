package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.util.Log
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
        TODO("No impl")
    }

    override fun storeData(data: ByteArray): ContentId {
        val name = String(data).hashCode().toString() + ".hash"
        val file = File(basePath, name)
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
            IPNSRecord(filesDir, _seq.toLong())
        } else {
            IPNSRecord("$filesDir/$peerId", _seq.toLong())
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

    override fun resolveNode(root: ContentId, path: MutableList<String>,timeoutSeconds: Long): ContentId? {
        return resolveNode(filesDir + "/" + path.joinToString("/"), timeoutSeconds)
    }

    private var _root: ContentId = filesDir
    private var _seq: Int = 0
    override fun publishName(root: ContentId, sequence: Int, timeoutSeconds: Long) {
        _root = root
        _seq = sequence
    }

    val basePath: String
        get() {
            val path = filesDir + "/bw/${BailiwickImpl.VERSION}/"
            File(path).also {
                if (!it.exists()) {
                    Log.i(TAG, "Creating base dirs: ${filesDir+"/bw/${BailiwickImpl.VERSION}"}")
                    it.mkdirs()
                }
            }
            return path
        }
}