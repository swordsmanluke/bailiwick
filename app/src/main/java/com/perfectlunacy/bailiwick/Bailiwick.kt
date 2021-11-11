package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS

class Bailiwick(val bailiwick: BailiwickNetwork, val ipfs: IPFS, val db: BailiwickDatabase) {
    companion object {
        const val VERSION = "0.2"

        @JvmStatic
        private lateinit var bw: Bailiwick

        @JvmStatic
        fun getInstance(): Bailiwick {
            return bw
        }

        @JvmStatic
        fun init(bailiwick: BailiwickNetwork, ipfs: IPFS, db: BailiwickDatabase): Bailiwick {
            bw = Bailiwick(bailiwick, ipfs, db)
            return bw
        }
    }
}