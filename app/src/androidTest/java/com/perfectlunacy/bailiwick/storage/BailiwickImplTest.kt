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
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
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
    private val bw: BailiwickImpl = BailiwickImpl(MockIPFS(context.filesDir.path), keyPair, db, MockFileCache())

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

        val finalPost = post()
        val cid = bw.store(finalPost, cipher)

        Assert.assertNotNull("Failed to create Post", cid)

        val post2 = bw.retrieve(cid, cipher, Post::class.java)!!

        Log.i("BailiwickImplTest", "Post: $post2")

        assertEquals(finalPost, post2)
    }

    @Test
    fun creatingAndRetrievingManifestWorks() {
        val cipher = bw.encryptorForKey("${bw.peerId}:everyone")

        val ogPost = postWithPicture()
        val postCid = bw.store(ogPost, cipher)
        val idCid = bw.store(Identity("my identity", ""), bw.encryptorForKey("public"))

        bw.ipfsManifest = manifest(idCid, listOf(postCid), cipher)

        val manifest = bw.ipfsManifest
        val feed = bw.retrieve(manifest.feeds[0], cipher, Feed::class.java)!!
        val post = bw.retrieve(feed.posts[0], cipher, Post::class.java)!!

        Log.i(javaClass.simpleName, "Post: $post vs $ogPost")

        assertNotNull(post)
    }

    @Test
    fun creatingIntroductionWorks() {
        /***
         * Create my account
         */
        val myCipher = bw.encryptorForKey("${bw.peerId}:everyone")
        val idCid = bw.store(Identity("another identity", ""), myCipher)
        bw.ipfsManifest = manifest(idCid, listOf(), myCipher)

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

    private fun post(): Post {
        val text = Faker().lorem.sentence()

        val p = Post(Calendar.getInstance().timeInMillis, null, text, emptyList(), "")
        val unsigned = Gson().toJson(p)
        Log.i("BailiwickImplTest", "Post: $unsigned")
        val signature = Base64.getEncoder()
            .encodeToString(
                RsaSignature(
                    keyPair.public,
                    keyPair.private
                ).sign(unsigned.toByteArray())
            )
        val finalPost = Post(p.timestamp, p.parentCid, p.text, p.files, signature)
        return finalPost
    }

    private fun postWithPicture(): Post {
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

        val p = Post(Calendar.getInstance().timeInMillis, null, text, listOf(fileDef), "")
        val unsigned = Gson().toJson(p)
        Log.i("BailiwickImplTest", "Post: $unsigned")
        val signature = Base64.getEncoder()
            .encodeToString(
                RsaSignature(
                    keyPair.public,
                    keyPair.private
                ).sign(unsigned.toByteArray())
            )
        return Post(p.timestamp, p.parentCid, p.text, p.files, signature)
    }

    private fun manifest(identity: ContentId, posts: List<ContentId>, cipher: Encryptor): Manifest {
        val feedCid = bw.store(
            Feed(
                Calendar.getInstance().timeInMillis,
                posts,
                emptyList(),
                emptyList(),
                identity
            ), cipher
        )
        return Manifest(listOf(feedCid))
    }
}