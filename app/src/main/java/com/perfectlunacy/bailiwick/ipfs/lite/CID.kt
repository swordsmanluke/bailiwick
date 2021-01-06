package com.perfectlunacy.bailiwick.ipfs.lite

import java.util.*


class CID(val cid: String) {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val pid1 = o as CID
        return cid == pid1.cid
    }

    override fun hashCode(): Int {
        return Objects.hash(cid)
    }

    override fun toString(): String {
        return cid
    }
}
