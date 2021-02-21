package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson
import java.lang.RuntimeException

data class PostFile(
    val mimeType: String,
    val cid: String,
    val signature: String
) {
    fun toJson() = "[$mimeType, $cid, $signature]"

    companion object {
        fun fromDbPostFile(f: com.perfectlunacy.bailiwick.models.db.PostFile): PostFile {
            return PostFile(f.mimeType, f.cid, f.signature)
        }
    }
}

data class Post(
    val version: String,
    val timestamp: Long,
    val text: String,
    val files: List<PostFile>,
    var signature: String?=null
) {
    companion object {
        fun fromJson(json: String): Post {
            val parser = Gson()
            val post = parser.fromJson(json, Post::class.java)
            post.validateSignature()
            return post
        }

        fun fromDbPost(dbPost: com.perfectlunacy.bailiwick.models.db.Post, files: List<PostFile>): Post {
            return Post("0.1", dbPost.timestamp, dbPost.text, files, dbPost.signature)
        }
    }

    init {
        if(signature != null) { validateSignature() }
    }

    private fun validateSignature() {
        val expectedSig = calcSignature()
        if(expectedSig != signature) { throw RuntimeException("Signature Violation") }
    }

    private fun calcSignature(): String {
        // TODO: Use our PublicKey to calculate the signature
        return sigStr().hashCode().toString()
    }

    fun toJson(): String {
        if (signature == null) { signature = calcSignature() }

        return Gson().toJson(this)
    }

    private fun sigStr() = "$version$timestamp$text" +
            files.map { f -> "${f.mimeType}${f.cid}${f.signature}" }.sorted().joinToString("")
}
