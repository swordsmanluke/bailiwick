package com.perfectlunacy.bailiwick

interface IpfsStore {
    fun store(data: String): String
    fun retrieve(uri: String): String
}
