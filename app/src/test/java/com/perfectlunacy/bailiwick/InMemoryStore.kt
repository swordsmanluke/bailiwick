package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.storage.DistHashTable

class InMemoryStore(private val myPeerId: String): DistHashTable {

    private val store = HashMap<String, String>()

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

}