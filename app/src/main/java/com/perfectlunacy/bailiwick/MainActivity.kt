package com.perfectlunacy.bailiwick

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.perfectlunacy.bailiwick.ipfs.IpfsLiteStore
import com.perfectlunacy.bailiwick.ipfs.lite.IPFS
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.RuntimeException

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), 1)
        ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), 1)

        val context = applicationContext

        // TODO: Extract this to a Service
        val ipfs = IPFS.getInstance(context)
        val dht = IpfsLiteStore(ipfs)
        ipfs.startDaemon(false)

        GlobalScope.launch {
            val data = "test data"
            val key = dht.store(data)
            val ret_data = dht.retrieve(key)
            if (ret_data != data) {
                throw RuntimeException("Well, hell. '$ret_data' != '$data'")
            }
        }
    }
}