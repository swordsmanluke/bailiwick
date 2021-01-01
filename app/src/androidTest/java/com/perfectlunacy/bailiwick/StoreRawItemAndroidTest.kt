package com.perfectlunacy.bailiwick

import androidx.test.platform.app.InstrumentationRegistry
import com.perfectlunacy.bailiwick.ipfs.IpfsClient
import com.perfectlunacy.bailiwick.ipfs.IpfsWrapper
import io.ipfs.kotlin.defaults.LocalIPFS
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Test writing a file to the IPFS store
 */
class StoreRawItemAndroidTest {
    var ipfs: IpfsClient? = null
    val dht = IpfsWrapper(LocalIPFS())

    @Before
    fun startDaemon() {
        val context = InstrumentationRegistry.getInstrumentation().context
        ipfs = IpfsClient(context)
        ipfs!!.startDaemon()
    }

    @After
    fun stopDaemon() {
        ipfs?.stopDaemon()
    }

    @Test
    fun writing_to_ipfs_store_works() {
        val data = "test data"
        val hashkey = dht.store(data)

        assertNotNull(hashkey)
    }

    @Test
    fun reading_from_ipfs_store_works() {
        val hashkey = "QmWmsL95CYvci8JiortAMhezezr8BhAwAVohVUSJBcZcBL"
        val data = dht.retrieve(hashkey)

        assertNotNull(data)
    }
}