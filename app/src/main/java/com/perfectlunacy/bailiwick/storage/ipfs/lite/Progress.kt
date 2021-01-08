package com.perfectlunacy.bailiwick.storage.ipfs.lite

interface Progress : Closeable {
    fun setProgress(progress: Int)
    fun doProgress(): Boolean
}
