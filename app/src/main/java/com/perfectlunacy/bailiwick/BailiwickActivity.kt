package com.perfectlunacy.bailiwick

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.viewmodels.BailwickViewModelFactory
import kotlinx.coroutines.*
import threads.lite.IPFS
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

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
            val ipfs = IPFSWrapper(IPFS.getInstance(applicationContext), keyPair)
            val bwDb = getBailiwickDb(applicationContext)
            val bwNetwork = BailiwickNetworkImpl(bwDb, ipfs.peerID, applicationContext.filesDir.toPath())
            Bailiwick.init(bwNetwork, ipfs, bwDb)
            bwModel = (viewModels<BailiwickViewModel> { BailwickViewModelFactory(applicationContext, bwNetwork, ipfs, bwDb.ipnsCacheDao()) }).value
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

    private val keyPair: KeyPair
        get() {
            val decoder = Base64.getDecoder()

            if(privateKeyString != null) {
                val sharedPref = applicationContext.getSharedPreferences("liteKey", Context.MODE_PRIVATE)
                val privateKeyData = decoder.decode(sharedPref.getString("privateKey", ""))
                val publicKeyData = decoder.decode(sharedPref.getString("publicKey", ""))

                val publicKey =
                    KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyData))

                val privateKey =
                    KeyFactory.getInstance("RSA")
                        .generatePrivate(PKCS8EncodedKeySpec(privateKeyData))

                return KeyPair(publicKey, privateKey)
            } else {
                val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
                val sharedPref = applicationContext.getSharedPreferences("liteKey", Context.MODE_PRIVATE).edit()
                sharedPref.putString("privateKey", Base64.getEncoder().encodeToString(keyPair.private.encoded))
                sharedPref.putString("publicKey", Base64.getEncoder().encodeToString(keyPair.public.encoded))
                sharedPref.apply()
                return keyPair
            }
        }

    private val privateKeyString: String?
        get() {
            // Use the IPFS private key
            val sharedPref = applicationContext.getSharedPreferences("liteKey", Context.MODE_PRIVATE)
            return sharedPref.getString("privateKey", null)
        }

    companion object {
        const val TAG = "BailiwickActivitiy"
    }
}