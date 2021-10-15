package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.storage.ipfs.Identity
import java.io.File

interface BailiwickNetwork {
    fun myId(): String
    fun store(data: String): String
    fun publish_posts(data: String): String
    fun retrieve(key: String): String
    fun retrieve_posts(key: String): String
    fun retrieve_file(key: String): File?

    var identity: Identity
}