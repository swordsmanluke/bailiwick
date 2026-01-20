package com.perfectlunacy.bailiwick.models.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for CircleMemberDao, especially the functionality used by the Contact screen
 * for managing circle membership.
 */
@RunWith(AndroidJUnit4::class)
class CircleMemberDaoTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var circleMemberDao: CircleMemberDao
    private lateinit var circleDao: CircleDao
    private lateinit var identityDao: IdentityDao

    private var circleId1: Long = 0
    private var circleId2: Long = 0
    private var circleId3: Long = 0
    private var identityId1: Long = 0
    private var identityId2: Long = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        circleMemberDao = db.circleMemberDao()
        circleDao = db.circleDao()
        identityDao = db.identityDao()

        // Create test identities
        val identity1 = Identity(
            blobHash = null,
            owner = "node-1",
            name = "User One",
            profilePicHash = null
        )
        val identity2 = Identity(
            blobHash = null,
            owner = "node-2",
            name = "User Two",
            profilePicHash = null
        )
        identityId1 = identityDao.insert(identity1)
        identityId2 = identityDao.insert(identity2)

        // Create test circles
        val circle1 = Circle(name = "Family", identityId = identityId1, blobHash = null)
        val circle2 = Circle(name = "Friends", identityId = identityId1, blobHash = null)
        val circle3 = Circle(name = "Work", identityId = identityId1, blobHash = null)
        circleId1 = circleDao.insert(circle1)
        circleId2 = circleDao.insert(circle2)
        circleId3 = circleDao.insert(circle3)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =====================
    // Insert and Query Tests
    // =====================

    @Test
    fun insert_adds_member_to_circle() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))

        val members = circleMemberDao.membersFor(circleId1)
        assertEquals(1, members.size)
        assertEquals(identityId2, members[0])
    }

    @Test
    fun circlesFor_returns_circles_user_is_member_of() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        circleMemberDao.insert(CircleMember(circleId2, identityId2))

        val circles = circleMemberDao.circlesFor(identityId2)
        assertEquals(2, circles.size)
        assertTrue(circles.contains(circleId1))
        assertTrue(circles.contains(circleId2))
        assertFalse(circles.contains(circleId3))
    }

    @Test
    fun membersFor_returns_all_members_of_circle() {
        circleMemberDao.insert(CircleMember(circleId1, identityId1))
        circleMemberDao.insert(CircleMember(circleId1, identityId2))

        val members = circleMemberDao.membersFor(circleId1)
        assertEquals(2, members.size)
        assertTrue(members.contains(identityId1))
        assertTrue(members.contains(identityId2))
    }

    @Test
    fun circlesFor_returns_empty_when_user_not_member_of_any() {
        val circles = circleMemberDao.circlesFor(identityId2)
        assertTrue(circles.isEmpty())
    }

    @Test
    fun membersFor_returns_empty_when_circle_has_no_members() {
        val members = circleMemberDao.membersFor(circleId1)
        assertTrue(members.isEmpty())
    }

    // =====================
    // isMember Tests
    // =====================

    @Test
    fun isMember_returns_true_when_user_is_member() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))

        assertTrue(circleMemberDao.isMember(circleId1, identityId2))
    }

    @Test
    fun isMember_returns_false_when_user_is_not_member() {
        assertFalse(circleMemberDao.isMember(circleId1, identityId2))
    }

    @Test
    fun isMember_returns_false_after_removal() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        assertTrue(circleMemberDao.isMember(circleId1, identityId2))

        circleMemberDao.delete(circleId1, identityId2)
        assertFalse(circleMemberDao.isMember(circleId1, identityId2))
    }

    // =====================
    // Delete Tests
    // =====================

    @Test
    fun delete_removes_membership() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        assertTrue(circleMemberDao.isMember(circleId1, identityId2))

        circleMemberDao.delete(circleId1, identityId2)

        assertFalse(circleMemberDao.isMember(circleId1, identityId2))
        assertTrue(circleMemberDao.membersFor(circleId1).isEmpty())
    }

    @Test
    fun delete_only_affects_specific_membership() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        circleMemberDao.insert(CircleMember(circleId2, identityId2))

        circleMemberDao.delete(circleId1, identityId2)

        assertFalse(circleMemberDao.isMember(circleId1, identityId2))
        assertTrue(circleMemberDao.isMember(circleId2, identityId2))
    }

    @Test
    fun delete_nonexistent_membership_does_nothing() {
        circleMemberDao.insert(CircleMember(circleId1, identityId2))

        // Try to delete a membership that doesn't exist
        circleMemberDao.delete(circleId2, identityId2)

        // Original membership should still exist
        assertTrue(circleMemberDao.isMember(circleId1, identityId2))
    }

    // =====================
    // Contact Screen Integration Tests
    // =====================

    @Test
    fun adding_contact_to_multiple_circles() {
        // Simulate adding a contact to multiple circles from the Contact screen
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        circleMemberDao.insert(CircleMember(circleId2, identityId2))
        circleMemberDao.insert(CircleMember(circleId3, identityId2))

        val circles = circleMemberDao.circlesFor(identityId2)
        assertEquals(3, circles.size)
    }

    @Test
    fun removing_contact_from_all_circles() {
        // Setup: contact is in all circles
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        circleMemberDao.insert(CircleMember(circleId2, identityId2))
        circleMemberDao.insert(CircleMember(circleId3, identityId2))

        // Simulate deleting contact - remove from all circles
        val memberCircles = circleMemberDao.circlesFor(identityId2)
        memberCircles.forEach { circleId ->
            circleMemberDao.delete(circleId, identityId2)
        }

        // Verify contact is removed from all circles
        assertTrue(circleMemberDao.circlesFor(identityId2).isEmpty())
        assertFalse(circleMemberDao.isMember(circleId1, identityId2))
        assertFalse(circleMemberDao.isMember(circleId2, identityId2))
        assertFalse(circleMemberDao.isMember(circleId3, identityId2))
    }

    @Test
    fun removing_from_one_circle_keeps_other_memberships() {
        // Setup: contact is in multiple circles
        circleMemberDao.insert(CircleMember(circleId1, identityId2))
        circleMemberDao.insert(CircleMember(circleId2, identityId2))

        // Remove from one circle
        circleMemberDao.delete(circleId1, identityId2)

        // Verify only the specified membership is removed
        val circles = circleMemberDao.circlesFor(identityId2)
        assertEquals(1, circles.size)
        assertEquals(circleId2, circles[0])
    }
}
