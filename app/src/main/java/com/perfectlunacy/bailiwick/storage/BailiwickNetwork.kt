package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.models.Identity
import threads.lite.cid.Cid
import java.io.File

interface BailiwickNetwork {
    fun myId(): String
    fun store(data: String): Cid
    fun publish_posts(data: String): Cid
    fun retrieve(key: Cid): String
    fun retrieve_posts(key: String): String
    fun retrieve_file(key: Cid): File?

    var identity: Identity
}