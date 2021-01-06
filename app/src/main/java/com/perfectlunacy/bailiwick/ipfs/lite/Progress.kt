package com.perfectlunacy.bailiwick.ipfs.lite

interface Progress : Closeable {
    fun setProgress(progress: Int)
    fun doProgress(): Boolean
}
