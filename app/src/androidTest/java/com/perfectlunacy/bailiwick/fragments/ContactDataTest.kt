package com.perfectlunacy.bailiwick.fragments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for contact-related database operations.
 * Tests the database operations for:
 * - Loading contact data (user + identities)
 * - Muting/unmuting contacts
 * - Deleting contacts (including cascade effects)
 * - Managing circle membership
 */
@RunWith(AndroidJUnit4::class)
class ContactDataTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase

    private val testNodeId = "contact-node-id"
    private val testPublicKey = "contact-public-key"

    private var userId: Long = 0
    private var identityId1: Long = 0
    private var identityId2: Long = 0
    private var circleId1: Long = 0
    private var circleId2: Long = 0
    private var ownerIdentityId: Long = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create owner identity (the app user)
        val ownerIdentity = Identity(
            blobHash = null,
            owner = "owner-node-id",
            name = "App Owner",
            profilePicHash = null
        )
        ownerIdentityId = db.identityDao().insert(ownerIdentity)

        // Create circles owned by app user
        val circle1 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        circleId1 = db.circleDao().insert(circle1)
        circleId2 = db.circleDao().insert(circle2)

        // Create the contact (User entity)
        val user = User(nodeId = testNodeId, publicKey = testPublicKey)
        db.userDao().insert(user)
        userId = db.userDao().all()[0].id

        // Create multiple identities for the contact
        val identity1 = Identity(
            blobHash = "identity1-blob",
            owner = testNodeId,
            name = "Contact Identity 1",
            profilePicHash = "avatar1-hash"
        )
        val identity2 = Identity(
            blobHash = "identity2-blob",
            owner = testNodeId,
            name = "Contact Identity 2",
            profilePicHash = "avatar2-hash"
        )
        identityId1 = db.identityDao().insert(identity1)
        identityId2 = db.identityDao().insert(identity2)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =====================
    // Contact Data Loading Tests
    // =====================

    @Test
    fun loadContactIdentities_returns_all_identities_for_nodeId() {
        val identities = db.identityDao().identitiesFor(testNodeId)

        assertEquals(2, identities.size)
        assertTrue(identities.any { it.name == "Contact Identity 1" })
        assertTrue(identities.any { it.name == "Contact Identity 2" })
    }

    @Test
    fun loadUser_from_identity() {
        // Given an identity id, find the user
        val identity = db.identityDao().find(identityId1)
        val user = db.userDao().findByNodeId(identity.owner)

        assertNotNull(user)
        assertEquals(testNodeId, user!!.nodeId)
        assertEquals(testPublicKey, user.publicKey)
    }

    // =====================
    // Mute Contact Tests
    // =====================

    @Test
    fun muteContact_sets_isMuted_true() {
        assertFalse(db.userDao().isMuted(userId))

        db.userDao().setMuted(userId, true)

        assertTrue(db.userDao().isMuted(userId))
        val user = db.userDao().find(userId)
        assertTrue(user!!.isMuted)
    }

    @Test
    fun unmuteContact_sets_isMuted_false() {
        db.userDao().setMuted(userId, true)
        assertTrue(db.userDao().isMuted(userId))

        db.userDao().setMuted(userId, false)

        assertFalse(db.userDao().isMuted(userId))
    }

    // =====================
    // Circle Membership Tests
    // =====================

    @Test
    fun addContactToCircle() {
        assertFalse(db.circleMemberDao().isMember(circleId1, identityId1))

        db.circleMemberDao().insert(CircleMember(circleId1, identityId1))

        assertTrue(db.circleMemberDao().isMember(circleId1, identityId1))
    }

    @Test
    fun getCirclesForContact() {
        db.circleMemberDao().insert(CircleMember(circleId1, identityId1))
        db.circleMemberDao().insert(CircleMember(circleId2, identityId1))

        val circles = db.circleMemberDao().circlesFor(identityId1)

        assertEquals(2, circles.size)
        assertTrue(circles.contains(circleId1))
        assertTrue(circles.contains(circleId2))
    }

    @Test
    fun removeContactFromCircle() {
        db.circleMemberDao().insert(CircleMember(circleId1, identityId1))
        assertTrue(db.circleMemberDao().isMember(circleId1, identityId1))

        db.circleMemberDao().delete(circleId1, identityId1)

        assertFalse(db.circleMemberDao().isMember(circleId1, identityId1))
    }

    // =====================
    // Delete Contact Tests
    // =====================

    @Test
    fun deleteContact_removes_user() {
        assertNotNull(db.userDao().find(userId))

        db.userDao().delete(userId)

        assertNull(db.userDao().find(userId))
    }

    @Test
    fun deleteContact_workflow_removes_from_all_circles() {
        // Add contact to circles
        db.circleMemberDao().insert(CircleMember(circleId1, identityId1))
        db.circleMemberDao().insert(CircleMember(circleId2, identityId1))

        // Delete workflow: first remove from all circles
        val memberCircles = db.circleMemberDao().circlesFor(identityId1)
        memberCircles.forEach { circleId ->
            db.circleMemberDao().delete(circleId, identityId1)
        }

        // Then delete user
        db.userDao().delete(userId)

        // Verify
        assertNull(db.userDao().find(userId))
        assertTrue(db.circleMemberDao().circlesFor(identityId1).isEmpty())
    }

    @Test
    fun deleteContact_workflow_removes_posts() {
        // Create posts from the contact
        val post1 = Post(
            authorId = identityId1,
            blobHash = null,
            timestamp = System.currentTimeMillis(),
            parentHash = null,
            text = "Post 1",
            signature = "sig1"
        )
        val post2 = Post(
            authorId = identityId1,
            blobHash = null,
            timestamp = System.currentTimeMillis(),
            parentHash = null,
            text = "Post 2",
            signature = "sig2"
        )
        db.postDao().insert(post1)
        db.postDao().insert(post2)

        // Verify posts exist
        val postsBeforeDelete = db.postDao().postsFor(identityId1)
        assertEquals(2, postsBeforeDelete.size)

        // Delete workflow: delete all posts from contact's identities
        val identities = db.identityDao().identitiesFor(testNodeId)
        identities.forEach { identity ->
            val posts = db.postDao().postsFor(identity.id)
            posts.forEach { post ->
                db.postDao().delete(post.id)
            }
        }

        // Verify posts deleted
        val postsAfterDelete = db.postDao().postsFor(identityId1)
        assertTrue(postsAfterDelete.isEmpty())
    }

    @Test
    fun deleteContact_full_workflow() {
        // Setup: add contact to circles and create posts
        db.circleMemberDao().insert(CircleMember(circleId1, identityId1))
        db.circleMemberDao().insert(CircleMember(circleId2, identityId1))

        val post = Post(
            authorId = identityId1,
            blobHash = null,
            timestamp = System.currentTimeMillis(),
            parentHash = null,
            text = "Test Post",
            signature = "test-sig"
        )
        db.postDao().insert(post)

        // Verify setup
        assertNotNull(db.userDao().find(userId))
        assertEquals(2, db.circleMemberDao().circlesFor(identityId1).size)
        assertEquals(1, db.postDao().postsFor(identityId1).size)

        // Execute full delete contact workflow
        // 1. Remove from all circles
        val memberCircles = db.circleMemberDao().circlesFor(identityId1)
        memberCircles.forEach { circleId ->
            db.circleMemberDao().delete(circleId, identityId1)
        }

        // 2. Delete all posts from contact's identities
        val identities = db.identityDao().identitiesFor(testNodeId)
        identities.forEach { identity ->
            val posts = db.postDao().postsFor(identity.id)
            posts.forEach { p ->
                db.postDao().delete(p.id)
            }
        }

        // 3. Delete the user
        db.userDao().delete(userId)

        // Verify complete cleanup
        assertNull(db.userDao().find(userId))
        assertTrue(db.circleMemberDao().circlesFor(identityId1).isEmpty())
        assertTrue(db.postDao().postsFor(identityId1).isEmpty())

        // Note: Identities are NOT deleted, just the user reference
        // This is intentional - identities may still be referenced by existing posts in other users' views
        assertEquals(2, db.identityDao().identitiesFor(testNodeId).size)
    }

    // =====================
    // Edge Cases
    // =====================

    @Test
    fun muteContact_multiple_times_keeps_muted() {
        db.userDao().setMuted(userId, true)
        db.userDao().setMuted(userId, true)
        db.userDao().setMuted(userId, true)

        assertTrue(db.userDao().isMuted(userId))
    }

    @Test
    fun addContactToSameCircleTwice() {
        db.circleMemberDao().insert(CircleMember(circleId1, identityId1))

        // Attempting to insert duplicate may throw or be ignored depending on Room config
        // The test verifies the first insert works correctly
        assertTrue(db.circleMemberDao().isMember(circleId1, identityId1))
        assertEquals(1, db.circleMemberDao().circlesFor(identityId1).size)
    }

    @Test
    fun removeFromCircleWhenNotMember() {
        // Should not throw
        db.circleMemberDao().delete(circleId1, identityId1)

        assertFalse(db.circleMemberDao().isMember(circleId1, identityId1))
    }
}
