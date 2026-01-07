package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
import java.io.File

class Bailiwick(
    val bailiwick: BailiwickNetwork,
    val iroh: IrohNode,
    val db: BailiwickDatabase,
    val keyring: DeviceKeyring,
    val cacheDir: File
) {
    companion object {
        const val VERSION = "0.3"  // Bumped for Iroh migration

        @JvmStatic
        private lateinit var bw: Bailiwick

        @JvmStatic
        fun getInstance(): Bailiwick {
            return bw
        }

        @JvmStatic
        fun init(
            bailiwick: BailiwickNetwork,
            iroh: IrohNode,
            db: BailiwickDatabase,
            keyring: DeviceKeyring,
            cacheDir: File
        ): Bailiwick {
            bw = Bailiwick(bailiwick, iroh, db, keyring, cacheDir)
            return bw
        }
    }
}
