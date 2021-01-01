package com.perfectlunacy.bailiwick

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.perfectlunacy.bailiwick.ipfs.IpfsClient
import com.perfectlunacy.bailiwick.ipfs.IpfsWrapper
import io.ipfs.kotlin.IPFS
import io.ipfs.kotlin.IPFSConfiguration
import io.ipfs.kotlin.defaults.LocalIPFS
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import java.io.File

/**
 * Test to ensure our IPFS "lib" file is loaded and available
 */
class FindingExecutablesAndroidTest {
    @Test
    fun nativeLibDirectoryIsPresentAndHasFiles() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val ipfs = IpfsClient(context)

        assertTrue("No such dir ${ipfs.libDir}", File(ipfs.libDir).exists() && File(ipfs.libDir).isDirectory)
        assertTrue("No files in ${ipfs.libDir}", File(ipfs.libDir).list()?.isNotEmpty() ?: false)
    }

    @Test
    fun ipfsExecutableIsAccessible() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val ipfs = IpfsClient(context)

        assertNotNull("Failed to create the IPFS path!", ipfs.IpfsExecutablePath)
        assertTrue("No such file ${ipfs.IpfsExecutablePath}", File(ipfs.IpfsExecutablePath).exists())
    }
}