package com.perfectlunacy.bailiwick.storage

import android.content.Context
import com.perfectlunacy.bailiwick.models.Identity
import threads.lite.cid.Cid
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MockBailiwickNetwork(val context: Context) : BailiwickNetwork {
    override fun myId() = "<peerid>"
    val basePath: String
        get() {
            val path = context.filesDir.toString() + "/bw/0.1/"
            File(path).also { if (!it.exists()) { it.mkdirs() } }
            return path
        }

    override fun store(data: String): Cid {
        val name = data.hashCode().toString() + ".hash"
        val file = File(basePath, name)
        val stream = FileOutputStream(file)
        try {
            stream.write(data.toByteArray())
        } finally {
            stream.close()
        }

        return Cid(file.path.toByteArray())
    }

    override fun publish_posts(data: String): Cid {
        // TODO: Append to the posts file
        return store(data)
    }

    override fun retrieve(key: Cid): String {
        val file = File(key.toString())
        if (file.exists() && file.isFile) {
            return FileInputStream(file).readBytes().toString()
        }
        return ""
    }

    override fun retrieve_posts(key: String): String {
        TODO("Not yet implemented")
    }

    override fun retrieve_file(key: Cid): File? {
        val f = File(key.toString())
        return when {
            f.exists() -> f
            else -> null
        }
    }

    private var _identity = Identity("swordsmanluke", myId(), null)
    override var identity: Identity
        get() = _identity
        set(value) {
            _identity = value
        }

}