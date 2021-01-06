package com.perfectlunacy.bailiwick.ipfs.lite

import java.util.*


class LinkInfo private constructor(
    private val hash: String,
    val name: String,
    val size: Long,
    private val type: Int
) {
    val cid: CID
        get() = CID(hash)

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val linkInfo = o as LinkInfo
        return size == linkInfo.size && type == linkInfo.type && hash == linkInfo.hash && name == linkInfo.name
    }

    override fun hashCode(): Int {
        return Objects.hash(hash, name, size, type)
    }

    // --Commented out by Inspection START (4/28/2020 9:50 PM):
    //    public int getType() {
    //        return type;
    //    }
    // --Commented out by Inspection STOP (4/28/2020 9:50 PM)
    val isDirectory: Boolean
        get() = type == 1

    override fun toString(): String {
        return "LinkInfo{" +
                "hash='" + hash + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", type=" + type +
                '}'
    }

    // --Commented out by Inspection START (4/28/2020 9:50 PM):
    val isFile: Boolean
        get() = type == 2

    //    public boolean isRaw() {
    //        return type == 0;
    //    }
    // --Commented out by Inspection STOP (4/28/2020 9:50 PM)
    companion object {
        fun create(name: String, hash: String, size: Long, type: Int): LinkInfo {
            Objects.requireNonNull(hash)
            Objects.requireNonNull(name)
            return LinkInfo(hash, name, size, type)
        }
    }
}

