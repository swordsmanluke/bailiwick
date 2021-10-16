package com.perfectlunacy.bailiwick.storage.ipfs

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import threads.lite.TestEnv
import threads.lite.cid.Cid
import threads.lite.core.TimeoutCloseable


@RunWith(AndroidJUnit4::class)
class BWFileManagerTest {
    private val TAG = BWFileManagerTest::class.java.simpleName
    private var context: Context = ApplicationProvider.getApplicationContext()

//    @Test
    fun identityLoop() {
        val ipfs = TestEnv.getTestInstance(context)
        val bwfm = BWFileManager(ipfs, context)
        bwfm.initBase(ipfs.peerID)

        val ident = Identity("My Name", "")
        bwfm.storeIdentity(ipfs.peerID, ident)

        val ident2 = bwfm.identityFor(ipfs.peerID, bwfm.sequence)

        assertEquals(ident, ident2)
    }

    @Test
    fun blocksTest() {
        val ipfs = TestEnv.getTestInstance(context)
        val bwfm = BWFileManager(ipfs, context)
        val ipnsRecord = ipfs.resolveName(ipfs.peerID.toBase32(), 0, TimeoutCloseable(10))!!
        val blocks = ipfs.getBlocks(Cid(ipnsRecord.hash.toByteArray()))
        Log.i(TAG, "Blocks: $blocks")
        assertNotNull(blocks)
    }

}