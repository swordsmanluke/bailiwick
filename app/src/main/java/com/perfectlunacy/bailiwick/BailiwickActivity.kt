package com.perfectlunacy.bailiwick

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.perfectlunacy.bailiwick.storage.BWick
import com.perfectlunacy.bailiwick.storage.MockIPFS
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import com.perfectlunacy.bailiwick.storage.ipfs.IpnsImpl
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.viewmodels.BailwickViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
            val useMocks = false

            val ipfs = if(useMocks) {
                MockIPFS(applicationContext.filesDir.path)
            } else {
                IPFSWrapper(IPFS.getInstance(applicationContext))
            }

            val cache = IPFSCache(applicationContext.filesDir.path + "/bwcache")

            val bwDb = getBailiwickDb(applicationContext)

            val ipns = IpnsImpl(ipfs, bwDb.accountDao(), bwDb.ipnsCacheDao())

//            val bwNetwork = BailiwickImpl(IpfsStore(cache, ipfs, ipns), keyPair, bwDb)
            val bwNetwork = BWick(bwDb, ipfs.peerID, applicationContext.filesDir.toPath())
            bwModel = (viewModels<BailiwickViewModel> { BailwickViewModelFactory(applicationContext, bwNetwork) }).value
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
                val privateKeyData = decoder.decode(privateKeyString)
                val publicKeyData = decoder.decode(publicKeyString)

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

    private val publicKeyString: String?
        get() {
            // Use the IPFS private key
            val sharedPref = applicationContext.getSharedPreferences("liteKey", Context.MODE_PRIVATE)
            return sharedPref.getString("publicKey", null)
        }
}