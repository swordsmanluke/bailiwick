package com.perfectlunacy.bailiwick.storage

interface DistHashTable {
    fun store(data: String): String
    fun publish_posts(data: String): String
    fun retrieve(key: String): String
    fun retrieve_posts(key: String): String
}