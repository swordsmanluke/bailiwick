package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.IpfsFeed
import com.perfectlunacy.bailiwick.models.ipfs.Manifest
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import io.bloco.faker.Faker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import threads.lite.TestEnv
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.*
import javax.crypto.KeyGenerator


@RunWith(AndroidJUnit4::class)
class UploadRunnerTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java).build()
    private val testIpfs = TestEnv.getTestInstance(context)
    private val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
    private val ipfs = IPFSWrapper(testIpfs, keyPair) // key doesn't really matter here, but this is the wrong key.

    @Test
    fun postsAndCirclesMakeIPFSManifest() {
        createAccountWithPost()

        UploadRunner(context, db, ipfs).run()

        val manifest = IpfsDeserializer.fromBailiwickFile(NoopEncryptor(),
                                                          ipfs,
                                                          ipfs.peerID,
                                                          "manifest.json",
                                                          Manifest::class.java)!!

        assertNotNull("Failed to locate manifest", manifest)
    }

    @Test
    fun IPFSManifestContainsExpectedPost() {
        createAccountWithPost()

        UploadRunner(context, db, ipfs).run()

        val manifest = IpfsDeserializer.fromBailiwickFile(NoopEncryptor(), ipfs, ipfs.peerID, "manifest.json", Manifest::class.java)!!

        assertEquals(1, manifest.feeds.size)

        val cipher = Keyring.encryptorForCircle(db.keyDao(), db.circleDao().all().first().id)
        manifest.feeds.forEach { feedCid ->
            val feed = IpfsDeserializer.fromCid(cipher, ipfs, feedCid, IpfsFeed::class.java)!!
            assertEquals(1, feed.posts.size)
            feed.posts.forEach { postCid ->
                IpfsDeserializer.fromCid(cipher, ipfs, postCid, Post::class.java)!!
            }
        }
    }

    private fun createAccountWithPost() {
        val peerId = ipfs.peerID
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        val identity = Identity(null, peerId, "StarBuck", null)
        val identityId = db.identityDao().insert(identity)

        // Create the "everyone" Circle
        val everyone = Circle("everyone", identityId, null)
        val circleId = db.circleDao().insert(everyone)
        db.circleMemberDao().insert(CircleMember(circleId, identityId))

        // Create a new encryption key for "everyone"
        val alias = UUID.randomUUID().toString()

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .build()

        keyGen.init(keyGenParameterSpec)
        keyGen.generateKey()
        db.keyDao().insert(Key("circle:$circleId", alias, "AES", KeyType.Secret))

        val bw = BailiwickNetworkImpl(db, peerId, context.filesDir.toPath())
        bw.storePost(circleId, buildPost(identity))
    }

    private fun buildPost(author: Identity): Post {
        val now = Calendar.getInstance().timeInMillis

        val myPost = Post(
            author.id,
            null,
            now,
            null,
            Faker().lorem.sentence(),
            ""
        )
        return myPost
    }
}