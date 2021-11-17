package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.IpnsCache
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import io.bloco.faker.Faker
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import threads.lite.TestEnv
import java.security.KeyPairGenerator
import java.util.*
import javax.crypto.KeyGenerator

@RunWith(AndroidJUnit4::class)
class DownloadRunnerTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java).build()
    private val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
    private val ipfs = IPFSWrapper(TestEnv.getTestInstance(context), keyPair)

    @Test
    fun runDownloadsIpfsData() {
        val now = Calendar.getInstance().timeInMillis
        val cipher = NoopEncryptor()

        val id = IpfsIdentity("General Adama", "")
        val idCid = id.toIpfs(cipher, ipfs)

        val post = IpfsPost(now,
            null,
            Faker().lorem.sentence(),
            emptyList(),
            "")

        val keyBytes = KeyGenerator.getInstance("AES").generateKey().encoded
        val action = IpfsAction("UpdateKey", mapOf(Pair("key", Base64.getEncoder().encodeToString(
            keyBytes
        ))))

        val postCid = post.toIpfs(cipher, ipfs)

        val actionCid = action.toIpfs(cipher, ipfs)

        val feed = IpfsFeed(now, listOf(postCid), emptyList(), emptyList(), idCid)
        val feedCid = feed.toIpfs(cipher, ipfs)

        val manifest = IpfsManifest(listOf(feedCid), listOf(actionCid))
        val manCid = manifest.toIpfs(cipher, ipfs)
        publishNewManifest(manCid)

        Assert.assertEquals(0, db.postDao().all().count())

        DownloadRunner(context, db, ipfs).run()

        val validator = ValidatorFactory.jsonValidator()

        // Should download the data we backdoor'd into IPFS
        Assert.assertEquals(1, db.postDao().all().count())
        Assert.assertNotNull(Keyring.encryptorForPeer(db.keyDao(), ipfs.peerID, validator))
    }

    private fun publishNewManifest(manCid: ContentId) {
        val seq = db.ipnsCacheDao().sequenceFor(ipfs.peerID) ?: 0

        var verCid = ipfs.createEmptyDir()!!
        var bwCid = ipfs.createEmptyDir()!!
        var rootCid = ipfs.createEmptyDir()!!

        verCid = ipfs.addLinkToDir(verCid, "manifest.json", manCid)!!
        bwCid = ipfs.addLinkToDir(bwCid, Bailiwick.VERSION, verCid)!!
        rootCid = ipfs.addLinkToDir(rootCid, "bw", bwCid)!!

        val peerId = ipfs.peerID
        db.ipnsCacheDao().insert(IpnsCache(peerId, "", rootCid, seq + 1))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw", bwCid, seq + 1))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw/${Bailiwick.VERSION}", verCid, seq + 1))

        ipfs.publishName(rootCid, seq + 1, 10)
        listOf(rootCid, bwCid, verCid, manCid).forEach { ipfs.provide(it, 10) }
    }
}