package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode
import java.io.File

class Bailiwick(
    val network: BailiwickNetwork,
    val iroh: IrohNode,
    val db: BailiwickDatabase,
    val keyring: DeviceKeyring,
    val cacheDir: File
) {
    companion object {
        const val VERSION = "0.3"  // Bumped for Iroh migration

        @JvmStatic
        @Volatile
        private var bw: Bailiwick? = null
        private val lock = Any()

        /**
         * Check if the Bailiwick singleton has been initialized.
         * Call this before getInstance() if initialization state is uncertain.
         */
        @JvmStatic
        fun isInitialized(): Boolean = bw != null

        /**
         * Get the Bailiwick singleton instance.
         * @throws IllegalStateException if init() has not been called
         */
        @JvmStatic
        fun getInstance(): Bailiwick {
            return bw ?: throw IllegalStateException(
                "Bailiwick not initialized. Call Bailiwick.init() from BailiwickActivity.onCreate() first."
            )
        }

        /**
         * Initialize the Bailiwick singleton. Must be called once during app startup,
         * typically from BailiwickActivity.onCreate().
         *
         * Thread-safe: uses double-checked locking to ensure only one instance is created.
         */
        @JvmStatic
        fun init(
            network: BailiwickNetwork,
            iroh: IrohNode,
            db: BailiwickDatabase,
            keyring: DeviceKeyring,
            cacheDir: File
        ): Bailiwick {
            return bw ?: synchronized(lock) {
                bw ?: Bailiwick(network, iroh, db, keyring, cacheDir).also { bw = it }
            }
        }
    }
}
