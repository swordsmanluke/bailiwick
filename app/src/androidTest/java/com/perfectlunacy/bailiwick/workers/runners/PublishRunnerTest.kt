package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.iroh.IrohIdentity
import com.perfectlunacy.bailiwick.models.iroh.IrohPost
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.InMemoryIrohNode
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import io.bloco.faker.Faker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*


@RunWith(AndroidJUnit4::class)
class ContentPublisherTest {
    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var iroh: InMemoryIrohNode
    private lateinit var publisher: ContentPublisher
    private val gson = Gson()
    private val cipher = NoopEncryptor()
    private lateinit var nodeId: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        iroh = InMemoryIrohNode()
        publisher = ContentPublisher(iroh, db)
        nodeId = runBlocking { iroh.nodeId() }
    }

    @Test
    fun publishIdentityStoresBlob() = runBlocking {
        val identity = createIdentity()

        val hash = publisher.publishIdentity(identity)

        // Verify blob was stored
        assertNotNull(iroh.getBlob(hash))

        // Verify blob content is correct
        val storedJson = String(iroh.getBlob(hash)!!)
        val storedIdentity = gson.fromJson(storedJson, IrohIdentity::class.java)
        assertEquals(identity.name, storedIdentity.name)
    }

    @Test
    fun publishPostStoresBlobAndUpdatesDb() = runBlocking {
        val identity = createIdentity()
        val circle = createCircle(identity.id)
        val post = createPost(identity.id)
        db.circlePostDao().insert(CirclePost(circle.id, post.id))

        val hash = publisher.publishPost(post, circle.id, cipher)

        // Verify blob was stored
        assertNotNull(iroh.getBlob(hash))

        // Verify content can be decrypted and parsed
        val encrypted = iroh.getBlob(hash)!!
        val decrypted = cipher.decrypt(encrypted)
        val storedPost = gson.fromJson(String(decrypted), IrohPost::class.java)

        assertEquals(post.text, storedPost.text)
        assertEquals(post.timestamp, storedPost.timestamp)
    }

    @Test
    fun publishPendingPublishesUnpublishedPosts() = runBlocking {
        val identity = createIdentity()
        val circle = createCircle(identity.id)

        // Create posts without blob hashes (pending)
        val post1 = createPost(identity.id)
        val post2 = createPost(identity.id)

        // Add posts to circle
        db.circlePostDao().insert(CirclePost(circle.id, post1.id))
        db.circlePostDao().insert(CirclePost(circle.id, post2.id))

        // Publish all pending content
        publisher.publishPending(cipher)

        // Verify posts now have hashes in DB
        val updatedPost1 = db.postDao().find(post1.id)
        val updatedPost2 = db.postDao().find(post2.id)

        assertNotNull(updatedPost1.blobHash)
        assertNotNull(updatedPost2.blobHash)

        // Verify blobs exist
        assertTrue(iroh.hasBlob(updatedPost1.blobHash!!))
        assertTrue(iroh.hasBlob(updatedPost2.blobHash!!))
    }

    @Test
    fun publishFileStoresEncryptedBlob() = runBlocking {
        val fileData = "Test file content".toByteArray()

        val hash = publisher.publishFile(fileData, cipher)

        // Verify blob was stored
        assertTrue(iroh.hasBlob(hash))

        // Verify content can be decrypted
        val encrypted = iroh.getBlob(hash)!!
        val decrypted = cipher.decrypt(encrypted)
        assertArrayEquals(fileData, decrypted)
    }

    @Test
    fun publishIdentityUpdatesDbHash() = runBlocking {
        val identity = createIdentity()
        assertNull(identity.blobHash)

        val hash = publisher.publishIdentity(identity)

        // Verify DB was updated
        val updated = db.identityDao().find(identity.id)
        assertEquals(hash, updated.blobHash)
    }

    @Test
    fun publishPostUpdatesDbHash() = runBlocking {
        val identity = createIdentity()
        val circle = createCircle(identity.id)
        val post = createPost(identity.id)
        db.circlePostDao().insert(CirclePost(circle.id, post.id))
        assertNull(post.blobHash)

        val hash = publisher.publishPost(post, circle.id, cipher)

        // Verify DB was updated
        val updated = db.postDao().find(post.id)
        assertEquals(hash, updated.blobHash)
    }

    // ===== Helper Methods =====

    private fun createIdentity(): Identity {
        val identity = Identity(
            blobHash = null,
            owner = nodeId,
            name = Faker().name.name(),
            profilePicHash = null
        )
        identity.id = db.identityDao().insert(identity)
        return identity
    }

    private fun createCircle(identityId: Long): Circle {
        val circle = Circle(
            name = "Test Circle",
            identityId = identityId,
            blobHash = null
        )
        circle.id = db.circleDao().insert(circle)
        return circle
    }

    private fun createPost(authorId: Long): Post {
        val post = Post(
            authorId = authorId,
            blobHash = null,
            timestamp = Calendar.getInstance().timeInMillis,
            parentHash = null,
            text = Faker().lorem.sentence(),
            signature = ""
        )
        post.id = db.postDao().insert(post)
        return post
    }
}
