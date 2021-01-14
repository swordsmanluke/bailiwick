package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson
import java.lang.RuntimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

data class Interaction(
    val type: String,
    val version: String,
    val id: String,
    private val timestampMillis: Long,
    val post: String,
    val parent: String?,
    val files: List<PostFile>,
    var signature: String? = null,
    ) {
    companion object {
        fun fromJson(json: String): Interaction {
            val parser = Gson()
            val interaction = parser.fromJson(json, Interaction::class.java)
            interaction.validateSignature()
            return interaction
        }
    }

    val timestamp: LocalDateTime
        get() = LocalDateTime.ofEpochSecond(this.timestampMillis/1000L, 0, ZoneOffset.UTC)

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

    private fun sigStr() = "$type$version$id$timestampMillis$post$parent" +
            files.map { f -> "${f.mimeType}${f.cid}${f.signature}" }.sorted().joinToString("")
}