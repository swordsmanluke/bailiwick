package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.ipfs.IpfsWrapper
import io.ipfs.kotlin.IPFS
import io.ipfs.kotlin.IPFSConfiguration
import org.junit.Test

import org.junit.Assert.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/**
 * Test writing a file to the IPFS store
 */
class StoreRawItemTest {
    @Test
    fun writing_to_ipfs_store_does_not_raise_on_success() {
        val ipfsStore = IpfsWrapper(IPFS(IPFSConfiguration("http://localhost:5001/api/v0/")))
        val data = "test data"

        // We're just asserting that no errors are raised here.
        val uri = ipfsStore.store(data)

        assertNotNull(uri)
    }
}