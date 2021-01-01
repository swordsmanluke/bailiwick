package com.perfectlunacy.bailiwick

interface DistHashStore {
    fun store(data: String): String
    fun retrieve(uri: String): String
}
