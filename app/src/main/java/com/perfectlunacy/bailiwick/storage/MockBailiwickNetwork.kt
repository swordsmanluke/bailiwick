package com.perfectlunacy.bailiwick.storage

import android.content.Context
import com.perfectlunacy.bailiwick.models.Identity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MockBailiwickNetwork(val context: Context) : BailiwickNetwork {
    override fun myId() = "<peerid>"
    private val basePath = context.filesDir.toString() + "/bw/0.1/"

    override fun store(data: String): String {
        val name = data.hashCode().toString() + ".hash"
        val file = File(basePath, name)
        val stream = FileOutputStream(file)
        try {
            stream.write(data.toByteArray())
        } finally {
            stream.close()
        }

        return file.path
    }

    override fun publish_posts(data: String): String {
        // TODO: Append to the posts file
        return store(data)
    }

    override fun retrieve(key: String): String {
        val file = File(key)
        if (file.exists() && file.isFile) {
            return FileInputStream(file).readBytes().toString()
        }
        return ""
    }

    override fun retrieve_posts(key: String): String {
        TODO("Not yet implemented")
    }

    private var _identity = Identity("swordsmanluke")
    override var identity: Identity
        get() = _identity
        set(value) {
            _identity = value
        }

}