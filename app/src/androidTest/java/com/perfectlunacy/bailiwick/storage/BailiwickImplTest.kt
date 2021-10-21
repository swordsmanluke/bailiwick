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
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import io.bloco.faker.Faker
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.KeyPairGenerator
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class BailiwickImplTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java).build()
    private val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
    private val key = KeyGenerator.getInstance("AES").generateKey()
    private val keyFile = KeyFile(mapOf(Pair("everyone", listOf(Base64.getEncoder().encodeToString(key.encoded)))))
    private val bw: BailiwickImpl = BailiwickImpl(MockIPFS(context.filesDir.path), keyPair, db, MockFileCache())

    init {
        if(db.accountDao().activeAccount() == null) {
            bw.newAccount("swordsmanluke", "notmypass")
        }
    }

    @Test
    fun creatingAndRetrievingPostWorks() {
        val key = Base64.getDecoder().decode(keyFile.keys["everyone"]?.lastOrNull()!!)
        val cipher = AESEncryptor(SecretKeySpec(key, "AES"))

        val finalPost = post()
        val cid = bw.store(finalPost, cipher)

        Assert.assertNotNull("Failed to create Post", cid)

        val post2 = bw.retrieve(cid, cipher, Post::class.java)!!

        Log.i("BailiwickImplTest", "Post: $post2")

        assertEquals(finalPost, post2)
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

    @Test
    fun creatingAndRetrievingManifestWorks() {
        val key = Base64.getDecoder().decode(keyFile.keys["everyone"]?.lastOrNull()!!)
        val cipher = AESEncryptor(SecretKeySpec(key, "AES"))

        val ogPost = postWithPicture()
        val postCid = bw.store(ogPost, cipher)

        val idCid = bw.store(Identity("another identity", ""), cipher)
        val feedCid = bw.store(Feed(Calendar.getInstance().timeInMillis, listOf(postCid), emptyList(), emptyList(), idCid), cipher)
        bw.manifest = Manifest(listOf(feedCid))

        val manifest = bw.manifest
        val feed = bw.retrieve(manifest.feeds[0], cipher, Feed::class.java)!!
        val post = bw.retrieve(feed.posts[0], cipher, Post::class.java)!!

        Log.i(javaClass.simpleName, "Post: $post vs $ogPost")

        assertNotNull(post)
    }
}