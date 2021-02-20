package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.models.Identity

interface DistHashTable {
    fun myId(): String
    fun store(data: String): String
    fun updateIdentity(name: String)
    fun publish_posts(data: String): String
    fun retrieve(key: String): String
    fun retrieve_posts(key: String): String

    val identity: Identity
}