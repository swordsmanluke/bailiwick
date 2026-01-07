package com.perfectlunacy.bailiwick

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.storage.iroh.IrohWrapper
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.viewmodels.BailwickViewModelFactory
import kotlinx.coroutines.*

class BailiwickActivity : AppCompatActivity() {
    lateinit var bwModel: BailiwickViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePerms()
        runBlocking { initBailiwick() }
        showDisplay()
    }

    private suspend fun initBailiwick() {
        withContext(Dispatchers.Default) {
            // Initialize Iroh node
            val iroh = IrohWrapper.create(applicationContext)

            // Initialize device keyring (RSA keypair)
            val keyring = DeviceKeyring.create(applicationContext)

            // Initialize database with fallback to destructive migration (no users to migrate)
            val bwDb = getBailiwickDb(applicationContext)

            // Initialize network layer
            val bwNetwork = BailiwickNetworkImpl(
                bwDb,
                iroh.nodeId,
                applicationContext.filesDir.toPath()
            )

            // Initialize global singleton
            val cacheDir = applicationContext.filesDir.resolve("blobs").also { it.mkdirs() }
            Bailiwick.init(bwNetwork, iroh, bwDb, keyring, cacheDir)

            // Initialize ViewModel
            bwModel = (viewModels<BailiwickViewModel> {
                BailwickViewModelFactory(applicationContext, bwNetwork, iroh, keyring, bwDb)
            }).value
        }
    }

    private fun showDisplay() {
        // Start showing things!
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
    }

    private fun requirePerms() {
        ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), 1)
        ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), 1)
    }

    companion object {
        const val TAG = "BailiwickActivity"
    }
}
