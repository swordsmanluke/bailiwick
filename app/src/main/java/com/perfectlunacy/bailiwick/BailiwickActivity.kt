package com.perfectlunacy.bailiwick

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.perfectlunacy.bailiwick.models.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsLiteStore
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.viewmodels.BailwickViewModelFactory
import threads.lite.IPFS

class BailiwickActivity : AppCompatActivity() {
    lateinit var bwModel: BailiwickViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePerms()
        initBailiwick()
        showDisplay()
    }

    private fun initBailiwick() {
        val bwNetwork = IpfsLiteStore(IPFS.getInstance(applicationContext), IPFS.getInstance(applicationContext).getPeerId("me").toString())
        val db = BailiwickDatabase.getInstance(applicationContext)
        bwModel = (viewModels<BailiwickViewModel>{ BailwickViewModelFactory(bwNetwork, db) }).value
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
}