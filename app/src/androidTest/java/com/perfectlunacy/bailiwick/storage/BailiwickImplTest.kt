package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.KeyFile
import com.perfectlunacy.bailiwick.storage.ipfs.Post
import junit.framework.Assert.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.SecureRandom
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
    private val bw: BailiwickImpl = BailiwickImpl(MockIPFS(context.filesDir.path), keyPair, db)

    init {
        if(db.accountDao().activeAccount() == null) {
            bw.newAccount("swordsmanluke", "notmypass")
        }
    }

    @Test
    fun creatingAndRetrievingPostWorks() {
        val p = Post(Calendar.getInstance().timeInMillis, null, "this is my post", emptyList(), "")
        val unsigned = Gson().toJson(p)
        Log.i("BailiwickImplTest", "Post: $unsigned")
        val signature = Base64.getEncoder()
            .encodeToString(RsaSignature(keyPair.public, keyPair.private).sign(unsigned.toByteArray()))
        val finalPost = Post(p.timestamp, p.parentCid, p.text, p.files, signature)
        val plaintext = Gson().toJson(finalPost)

        Log.i("BailiwickImplTest", "Post(signed): $plaintext")

        val key = Base64.getDecoder().decode(keyFile.keys["everyone"]?.lastOrNull()!!)
        val cipher = AESEncryptor(SecretKeySpec(key, "AES"))
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ciphertext = cipher.encrypt(plaintext.toByteArray(), iv)

        val cid = bw.store(ciphertext)

        Assert.assertNotNull("Failed to create Post", cid)

        val rawBytes = bw.download(cid)!!
        Assert.assertEquals("Got different data", rawBytes.contentHashCode(), ciphertext.contentHashCode())

        val plaintext2 = cipher.decrypt(rawBytes)
        val post2 = Gson().fromJson(String(plaintext2), Post::class.java)
        Log.i("BailiwickImplTest", "Post: $post2")

        assertEquals(finalPost, post2)
    }
}