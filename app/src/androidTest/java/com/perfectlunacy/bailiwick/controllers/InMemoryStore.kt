package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.ipfs.Identity
import java.io.File

class InMemoryStore(private val myPeerId: String): BailiwickNetwork {

    private val store = HashMap<String, String>()
    override fun myId(): String {
        return myPeerId
    }

    override fun store(data: String): String {
        val key = data.hashCode().toString()
        store[key] = data
        return key
    }

    override fun publish_posts(data: String): String {
        store[myPeerId] = data
        return myPeerId
    }

    override fun retrieve(key: String): String {
        return store[key] ?: ""
    }

    override fun retrieve_posts(key: String): String {
        // Normally there's some indirection here, but we can skip that
        // since I'm ignoring the IPNS stuff for now.
        return retrieve(key)
    }

    override fun retrieve_file(key: String): File? {
        return when(store[key]) {
            is String -> { File(store[key]) }
            else -> { null }
        }
    }

    override var identity: Identity
        get() = TODO("Not yet implemented")
        set(value) {}

}