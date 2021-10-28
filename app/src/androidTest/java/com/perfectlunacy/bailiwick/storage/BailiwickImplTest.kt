package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.*
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import io.bloco.faker.Faker
import junit.framework.Assert.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.KeyPairGenerator
import java.util.*
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class BailiwickImplTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java).build()
    private val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
    private val bw: BailiwickImpl = BailiwickImpl(MockIPFS(context.filesDir.path), keyPair, db, MockFileCache(), context.filesDir.path)

    fun deleteCachedFiles() {
        val bwDir = File(context.filesDir.path + "/bw")
        if(bwDir.exists()) {
            bwDir.deleteRecursively()
        }
    }

    @Before
    fun initializeBailiwick() {
        deleteCachedFiles()
        bw.newAccount("Lucas", "swordsmanluke", "notmypass", "")
    }

    @Test
    fun creatingAndRetrievingPostWorks() {
        val cipher = bw.encryptorForKey("${bw.peerId}:everyone")

        val finalPost = post(cipher)
        val cid = bw.store(finalPost, cipher)

        Assert.assertNotNull("Failed to create Post", cid)

        val post2 = bw.retrieve(cid, cipher, Post.PostRecord::class.java)!!

        Log.i("BailiwickImplTest", "Post: $post2")

        assertEquals(finalPost, post2)
    }

    @Test
    fun creatingAndRetrievingManifestWorks() {
        val cipher = bw.encryptorForKey("${bw.peerId}:everyone")

        val ogPost = postWithPicture(cipher)
        val postCid = bw.store(ogPost, cipher)
        val everyoneFeed = bw.manifest.feeds.first()
        everyoneFeed.addPost(postCid)
        bw.manifest.updateFeed(everyoneFeed, cipher)

        val manifest = bw.manifest
        val feed = manifest.feeds.first()
        val post = feed.posts.first()

        Log.i(javaClass.simpleName, "Post: $post vs $ogPost")

        assertNotNull(post)
    }

    @Test
    fun creatingIntroductionWorks() {
        /***
         * Create my account
         */

        /***
         * Create a subscribe request to send
         */
        val publicIdCid = bw.ipfs.resolveNode("/ipfs/${bw.peerId}/identity.json", 10)!!
        val password = "password"

        // Generated request is encrypted with the password
        val encRequest = bw.createIntroduction(publicIdCid, password)

        // Use the password to generate a key
        val aesKey = SecretKeySpec(Md5Signature().sign(password.toByteArray()), "AES")
        val aes = AESEncryptor(aesKey)

        // Decrypt the request
        val request = Gson().fromJson(String(aes.decrypt(encRequest)), Introduction::class.java)

        assertEquals(false, request.isResponse)
        assertEquals(request.peerId, bw.peerId)
        assertEquals(request.publicKey, Base64.getEncoder().encodeToString(bw.keyPair.public.encoded))
    }

    private fun post(cipher: Encryptor): Post {
        val text = Faker().lorem.sentence()
        val ipfs = MockIPFS(context.filesDir.path)
        val ipfsCache = IPFSCache(context.filesDir.path)

        val cid = Post.create(ipfs, ipfsCache, Sha1Signature(), cipher,null, text, emptyList())
        val author = UserIdentity(cipher, ipfsCache, cid)
        return Post(cipher, ipfsCache, author, cid)
    }

    private fun postWithPicture(cipher: Encryptor): Post {
        val text = Faker().lorem.sentence()
        val imgUrl = URL(Faker().avatar.image())
        val bm = BitmapFactory.decodeStream(imgUrl.openConnection().getInputStream())
        val f = File.createTempFile("av_", null)
        val out = FileOutputStream(f)
        bm.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
        val input = FileInputStream(f)

        val fileCid = bw.store(input.readBytes())
        val fileDef = FileDef("image/png", fileCid)
        input.close()

        val ipfs = MockIPFS(context.filesDir.path)
        val ipfsCache = IPFSCache(context.filesDir.path)

        val cid = Post.create(ipfs, ipfsCache, Sha1Signature(), cipher, null, text, listOf(fileDef))
        val id = UserIdentity.create(ipfs, ipfsCache, cipher, "My Name", "")
        val author = UserIdentity(cipher, ipfsCache, id)
        return Post(cipher, ipfsCache, author, cid)
    }

    private fun manifest(identity: ContentId, cipher: Encryptor): Manifest {
        val ipfs = MockIPFS(context.filesDir.path)
        val ipfsCache = IPFSCache(context.filesDir.path)
        val everyoneFeed = Feed.create(ipfsCache, ipfs, identity, listOf(), listOf(), cipher)
        val mCid = Manifest.create(ipfsCache, ipfs, everyoneFeed, cipher)

        return Manifest(cipher, ipfsCache, mCid)
    }
}