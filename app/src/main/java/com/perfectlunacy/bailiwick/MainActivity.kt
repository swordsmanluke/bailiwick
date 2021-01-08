package com.perfectlunacy.bailiwick

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsLiteStore
import com.perfectlunacy.bailiwick.storage.ipfs.lite.IPFS

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), 1)
        ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), 1)

        val context = applicationContext

        // TODO: Extract this to a Service
        val ipfs = IPFS.getInstance(context)
        ipfs.startDaemon(false)

        val dht = IpfsLiteStore(ipfs, IPFS.getPeerID(context)!!)
    }
}