package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.io.BaseEncoding
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import threads.lite.IPFS
import threads.lite.cid.Cid
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class IPFSWrapperTest {
    private var context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun createEmptyDirCreatesNewDirectories() {
        val ipfs = IPFSWrapper(TestEnv.getTestInstance(context))
        var dir = ipfs.createEmptyDir()!!
        val dir2 = ipfs.createEmptyDir()!!
        dir = ipfs.addLinkToDir(dir, "subdir", dir2)!!
        assertNotEquals(dir, dir2)
    }

    @Test
    fun convertToAndFromCid() {
        val ipfs = IPFSWrapper(TestEnv.getTestInstance(context))
        val cid = ipfs.createEmptyDir()!!
        val convertedCid = Cid(BaseEncoding.base32().decode(cid)).key

        assertEquals(cid, convertedCid)
    }
}
internal object TestEnv {
    private val bootstrap = AtomicBoolean(false)
    fun isConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return (capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    }

    fun getTestInstance(context: Context): IPFS {
        val ipfs = IPFS.getInstance(context)
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val linkProperties = connectivityManager.getLinkProperties(network)
        if (linkProperties != null) {
            val interfaceName = linkProperties.interfaceName
            ipfs.updateNetwork(interfaceName!!)
        }
        ipfs.clearDatabase()
        ipfs.relays(10)
        if (!bootstrap.getAndSet(true)) {
            ipfs.bootstrap()
        }
        ipfs.reset()
        System.gc()
        return ipfs
    }
}
