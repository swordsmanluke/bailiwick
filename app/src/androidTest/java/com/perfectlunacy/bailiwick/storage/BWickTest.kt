package com.perfectlunacy.bailiwick.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import io.bloco.faker.Faker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.util.*

@RunWith(AndroidJUnit4::class)
class BWickTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var signer: RsaSignature

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create RSA keypair for signing posts
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.genKeyPair()
        signer = RsaSignature(keyPair.public, keyPair.private)
    }

    @Test
    fun creatingAPostWorks() {
        val peerId = "myPeerId"

        val identity = Identity(null, peerId, "StarBuck", null)
        val identityId = db.identityDao().insert(identity)

        val allCircle = Circle("All", identityId, null)
        val circleId = db.circleDao().insert(allCircle)

        db.circleMemberDao().insert(CircleMember(circleId, identityId))

        // acct does not exist now, but will by the time we finish saving all the below.
        val bw = BailiwickNetworkImpl(db, peerId, context.filesDir.toPath())

        assertEquals(0, bw.posts.count())

        bw.storePost(circleId, buildPost(identity))

        assertEquals(1, bw.posts.count())
    }

    @Test
    fun allPostsAreFound() {
        val myPeerId = "myPeerId"
        val yourPeerId = "yourPeerId"

        val you = Identity(null, yourPeerId, "Hugh", null)
        val me = Identity(null, myPeerId, "Me, Myself and I", null)

        val myPost = buildPost(me)
        val yourPost = buildPost(you)

        db.postDao().insert(yourPost)
        db.postDao().insert(myPost)

        listOf(myPeerId, yourPeerId).forEach{
            db.subscriptionDao().insert(Subscription(it, 0))
        }

        // After all of that set up... connect to our account and validate stuff
        val bailiwick = BailiwickNetworkImpl(db, myPeerId, context.filesDir.toPath())
        assertTrue(bailiwick.posts.containsAll(listOf(myPost, yourPost)))
    }

    @Test
    fun storePost_insertsPostOnlyToSpecifiedCircle() {
        // Posts are only stored in the specified circle, not auto-added to "All"
        val myPeerId = "myPeerId"
        newAccount(myPeerId, "Test User", "")
        val me = db.identityDao().identitiesFor(myPeerId).first()

        val bw = BailiwickNetworkImpl(db, myPeerId, context.filesDir.toPath())
        val friendsCircle = bw.createCircle("friends", me)
        val allCircle = bw.circles.find { it.name == "All" }!!

        // Precondition: no posts exist
        assertEquals("Precondition failed: posts should be empty", 0, bw.posts.count())

        // Store a post to the "friends" circle
        val post = buildPost(me)
        bw.storePost(friendsCircle.id, post)

        // The post should exist exactly once in the database
        assertEquals("Post was inserted multiple times!", 1, bw.posts.count())

        // The post should only be in friends circle, not auto-added to All
        assertTrue("Post should be in friends circle",
            bw.circlePosts(friendsCircle.id).any { it.signature == post.signature })
        assertFalse("Post should NOT be auto-added to All circle",
            bw.circlePosts(allCircle.id).any { it.signature == post.signature })
    }

    @Test
    fun postsAreInTheirExpectedCircles() {
        val yourPeerId = "yourPeerId"
        val you = Identity(null, yourPeerId, "Hugh", null)

        val myPeerId = "myPeerId"
        newAccount(myPeerId, "Me, Myself and I", "")
        val me = db.identityDao().identitiesFor(myPeerId).first()

        val myPost = buildPost(me)
        val yourPost = buildPost(you)

        val bw = BailiwickNetworkImpl(db, myPeerId, context.filesDir.toPath())
        val friendCirc = bw.createCircle("BestFriends", me)
        val coworkersCirc = bw.createCircle("Cow Orkers", me) // IRL, this would be tied to my "professional" profile
        val allCirc = bw.circles.find { it.name == "All" }!!

        // I'm in all of my own circles, of course
        db.circleMemberDao().insert(CircleMember(friendCirc.id, me.id))
        db.circleMemberDao().insert(CircleMember(allCirc.id, me.id))
        db.circleMemberDao().insert(CircleMember(coworkersCirc.id, me.id))

        // Hugh is a coworker. Not in the 'best friends' circle
        db.circleMemberDao().insert(CircleMember(coworkersCirc.id, you.id))
        db.circleMemberDao().insert(CircleMember(allCirc.id, you.id))

        // Posts are tied only to the circle they're posted in (no longer auto-added to All)
        bw.storePost(friendCirc.id, myPost)

        // Your posts get inserted. I'll find them in circles I've added your Post's author to
        db.postDao().insert(yourPost)

        // Only Hugh's post is in the coworkers' circle (I haven't posted there!)
        assertTrue("Hugh's post is missing from Cow Orkers!", bw.circlePosts(coworkersCirc.id).contains(yourPost))
        assertFalse("My post is in Cow Orkers!", bw.circlePosts(coworkersCirc.id).contains(myPost))

        // Only my post is in the BestFriends circle
        assertTrue("My post is missing from BestFriends!", bw.circlePosts(friendCirc.id).contains(myPost))
        assertFalse("Hugh's post is in BestFriends!", bw.circlePosts(friendCirc.id).contains(yourPost))
    }

    private fun newAccount(nodeId: NodeId, name: String, avatarHash: String) {
        val identity = Identity(null, nodeId, name, avatarHash)
        val identityId = db.identityDao().insert(identity)

        val user = User(nodeId, "")
        db.userDao().insert(user)
        db.subscriptionDao().insert(Subscription(nodeId, 0)) // Always subscribed to ourselves

        val circle = Circle("All", identityId, null)
        val circleId = db.circleDao().insert(circle)

        db.circleMemberDao().insert(CircleMember(circleId, identityId))
    }

    private fun buildPost(author: Identity): Post {
        val now = Calendar.getInstance().timeInMillis

        val post = Post(
            author.id,
            null,
            now,
            null,
            Faker().lorem.sentence(),
            ""  // Will be signed below
        )
        // Sign the post with RSA - this creates a unique, verifiable signature
        post.sign(signer, emptyList())
        return post
    }
}
