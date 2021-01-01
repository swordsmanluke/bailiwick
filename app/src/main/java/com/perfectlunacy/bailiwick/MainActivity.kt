package com.perfectlunacy.bailiwick

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.perfectlunacy.bailiwick.ipfs.IpfsClient
import com.perfectlunacy.bailiwick.ipfs.IpfsWrapper
import io.ipfs.kotlin.defaults.LocalIPFS

class MainActivity : AppCompatActivity() {
    lateinit var dht: DistHashStore
    lateinit var ipfs: IpfsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO: Extract this to a Service
        ipfs = IpfsClient(this.applicationContext)
        ipfs.startDaemon()

        dht = IpfsWrapper(LocalIPFS())
    }
}