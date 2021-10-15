package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.Identity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import threads.lite.IpfsTest
import threads.lite.TestEnv


@RunWith(AndroidJUnit4::class)
class BWFileManagerTest {
    private val TAG = BWFileManagerTest::class.java.simpleName
    private var context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun identityLoop() {
        val ipfs = TestEnv.getTestInstance(context)
        val bwfm = BWFileManager(ipfs)
        bwfm.initBase(ipfs.peerID)

        val ident = Identity("My Name", "")
        bwfm.storeIdentity(ipfs.peerID, ident)

        val ident2 = bwfm.identityFor(ipfs.peerID)

        assertEquals(ident, ident2)
    }

}