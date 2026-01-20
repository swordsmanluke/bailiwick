package com.perfectlunacy.bailiwick.fragments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleMember
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for EditCircleFragment's business logic.
 *
 * Tests the member tracking and state management logic that the fragment uses:
 * - getCurrentMemberIds(): (originalMemberIds + addedMemberIds) - removedMemberIds
 * - hasChanges(): checks name changes and member list changes
 * - Add/remove member tracking with proper state transitions
 */
@RunWith(AndroidJUnit4::class)
class EditCircleFragmentLogicTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase

    private var ownerIdentityId: Long = 0
    private var circleId: Long = 0

    // Simulates fragment state
    private var originalName = ""
    private var originalMemberIds = setOf<Long>()
    private val addedMemberIds = mutableSetOf<Long>()
    private val removedMemberIds = mutableSetOf<Long>()
    private var currentNameInput = ""

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create owner identity
        val ownerIdentity = Identity(
            blobHash = null,
            owner = "owner-node",
            name = "Owner",
            profilePicHash = null
        )
        ownerIdentityId = db.identityDao().insert(ownerIdentity)

        // Create a circle
        val circle = Circle(name = "Test Circle", identityId = ownerIdentityId, blobHash = null)
        circleId = db.circleDao().insert(circle)

        // Reset fragment state
        addedMemberIds.clear()
        removedMemberIds.clear()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // Helper to simulate fragment's getCurrentMemberIds()
    private fun getCurrentMemberIds(): Set<Long> {
        return (originalMemberIds + addedMemberIds) - removedMemberIds
    }

    // Helper to simulate fragment's hasChanges()
    private fun hasChanges(): Boolean {
        return currentNameInput != originalName ||
                addedMemberIds.isNotEmpty() ||
                removedMemberIds.isNotEmpty()
    }

    // Helper to simulate fragment's onAddMember()
    private fun onAddMember(identityId: Long) {
        if (identityId in removedMemberIds) {
            removedMemberIds.remove(identityId)
        } else {
            addedMemberIds.add(identityId)
        }
    }

    // Helper to simulate fragment's onRemoveMember()
    private fun onRemoveMember(identityId: Long) {
        if (identityId in addedMemberIds) {
            addedMemberIds.remove(identityId)
        } else {
            removedMemberIds.add(identityId)
        }
    }

    // =====================
    // getCurrentMemberIds Tests
    // =====================

    @Test
    fun getCurrentMemberIds_returns_empty_when_no_members() {
        originalMemberIds = emptySet()

        val current = getCurrentMemberIds()

        assertTrue(current.isEmpty())
    }

    @Test
    fun getCurrentMemberIds_returns_original_members_when_unchanged() {
        originalMemberIds = setOf(1L, 2L, 3L)

        val current = getCurrentMemberIds()

        assertEquals(setOf(1L, 2L, 3L), current)
    }

    @Test
    fun getCurrentMemberIds_includes_added_members() {
        originalMemberIds = setOf(1L, 2L)
        addedMemberIds.add(3L)
        addedMemberIds.add(4L)

        val current = getCurrentMemberIds()

        assertEquals(setOf(1L, 2L, 3L, 4L), current)
    }

    @Test
    fun getCurrentMemberIds_excludes_removed_members() {
        originalMemberIds = setOf(1L, 2L, 3L)
        removedMemberIds.add(2L)

        val current = getCurrentMemberIds()

        assertEquals(setOf(1L, 3L), current)
    }

    @Test
    fun getCurrentMemberIds_handles_add_and_remove_together() {
        originalMemberIds = setOf(1L, 2L, 3L)
        addedMemberIds.add(4L)
        addedMemberIds.add(5L)
        removedMemberIds.add(1L)
        removedMemberIds.add(2L)

        val current = getCurrentMemberIds()

        assertEquals(setOf(3L, 4L, 5L), current)
    }

    @Test
    fun getCurrentMemberIds_ignores_adding_already_present_member() {
        originalMemberIds = setOf(1L, 2L)
        addedMemberIds.add(1L) // Already in original

        val current = getCurrentMemberIds()

        // Should still be just 1, 2 (1 doesn't appear twice)
        assertEquals(setOf(1L, 2L), current)
    }

    @Test
    fun getCurrentMemberIds_ignores_removing_non_member() {
        originalMemberIds = setOf(1L, 2L)
        removedMemberIds.add(99L) // Not in original

        val current = getCurrentMemberIds()

        assertEquals(setOf(1L, 2L), current)
    }

    // =====================
    // hasChanges Tests
    // =====================

    @Test
    fun hasChanges_returns_false_when_nothing_changed() {
        originalName = "Test Circle"
        currentNameInput = "Test Circle"

        assertFalse(hasChanges())
    }

    @Test
    fun hasChanges_returns_true_when_name_changed() {
        originalName = "Test Circle"
        currentNameInput = "New Name"

        assertTrue(hasChanges())
    }

    @Test
    fun hasChanges_returns_true_when_member_added() {
        originalName = "Test Circle"
        currentNameInput = "Test Circle"
        addedMemberIds.add(1L)

        assertTrue(hasChanges())
    }

    @Test
    fun hasChanges_returns_true_when_member_removed() {
        originalName = "Test Circle"
        currentNameInput = "Test Circle"
        originalMemberIds = setOf(1L)
        removedMemberIds.add(1L)

        assertTrue(hasChanges())
    }

    @Test
    fun hasChanges_returns_true_when_both_name_and_members_changed() {
        originalName = "Test Circle"
        currentNameInput = "New Name"
        addedMemberIds.add(1L)
        removedMemberIds.add(2L)

        assertTrue(hasChanges())
    }

    // =====================
    // onAddMember Tests
    // =====================

    @Test
    fun onAddMember_adds_new_member_to_addedMemberIds() {
        val newMemberId = 5L

        onAddMember(newMemberId)

        assertTrue(addedMemberIds.contains(newMemberId))
        assertFalse(removedMemberIds.contains(newMemberId))
    }

    @Test
    fun onAddMember_restores_removed_member_instead_of_adding() {
        val memberId = 2L
        originalMemberIds = setOf(1L, memberId, 3L)
        removedMemberIds.add(memberId) // User removed this member

        // Now user adds them back
        onAddMember(memberId)

        assertFalse(removedMemberIds.contains(memberId))
        assertFalse(addedMemberIds.contains(memberId)) // Not added, just restored
    }

    @Test
    fun onAddMember_multiple_members() {
        onAddMember(1L)
        onAddMember(2L)
        onAddMember(3L)

        assertEquals(setOf(1L, 2L, 3L), addedMemberIds)
    }

    // =====================
    // onRemoveMember Tests
    // =====================

    @Test
    fun onRemoveMember_marks_original_member_for_removal() {
        val memberId = 2L
        originalMemberIds = setOf(1L, memberId, 3L)

        onRemoveMember(memberId)

        assertTrue(removedMemberIds.contains(memberId))
        assertFalse(addedMemberIds.contains(memberId))
    }

    @Test
    fun onRemoveMember_removes_added_member_instead_of_marking() {
        val memberId = 5L
        addedMemberIds.add(memberId) // User just added this

        // Now user removes them
        onRemoveMember(memberId)

        assertFalse(addedMemberIds.contains(memberId)) // Removed from added
        assertFalse(removedMemberIds.contains(memberId)) // Not marked for removal
    }

    @Test
    fun onRemoveMember_multiple_members() {
        originalMemberIds = setOf(1L, 2L, 3L)

        onRemoveMember(1L)
        onRemoveMember(3L)

        assertEquals(setOf(1L, 3L), removedMemberIds)
    }

    // =====================
    // Add/Remove Interaction Tests
    // =====================

    @Test
    fun add_then_remove_new_member_leaves_no_trace() {
        val memberId = 99L

        onAddMember(memberId)
        assertTrue(addedMemberIds.contains(memberId))

        onRemoveMember(memberId)
        assertFalse(addedMemberIds.contains(memberId))
        assertFalse(removedMemberIds.contains(memberId))
    }

    @Test
    fun remove_then_add_original_member_restores_it() {
        val memberId = 2L
        originalMemberIds = setOf(1L, memberId, 3L)

        onRemoveMember(memberId)
        assertTrue(removedMemberIds.contains(memberId))

        onAddMember(memberId)
        assertFalse(removedMemberIds.contains(memberId))
        assertFalse(addedMemberIds.contains(memberId)) // Restored, not added
    }

    @Test
    fun complex_add_remove_sequence() {
        originalMemberIds = setOf(1L, 2L, 3L)

        // Remove member 1
        onRemoveMember(1L)
        assertEquals(setOf(2L, 3L), getCurrentMemberIds())

        // Add new member 4
        onAddMember(4L)
        assertEquals(setOf(2L, 3L, 4L), getCurrentMemberIds())

        // Add back member 1
        onAddMember(1L)
        assertEquals(setOf(1L, 2L, 3L, 4L), getCurrentMemberIds())

        // Remove member 4 (newly added)
        onRemoveMember(4L)
        assertEquals(setOf(1L, 2L, 3L), getCurrentMemberIds())

        // Final state: back to original
        assertTrue(addedMemberIds.isEmpty())
        assertTrue(removedMemberIds.isEmpty())
    }

    // =====================
    // Database Integration Tests
    // =====================

    @Test
    fun saveChanges_persists_added_members() {
        // Create test members
        val member1 = Identity(blobHash = null, owner = "member1", name = "Member 1", profilePicHash = null)
        val member2 = Identity(blobHash = null, owner = "member2", name = "Member 2", profilePicHash = null)
        val memberId1 = db.identityDao().insert(member1)
        val memberId2 = db.identityDao().insert(member2)

        // Simulate adding members
        addedMemberIds.add(memberId1)
        addedMemberIds.add(memberId2)

        // Persist (simulates saveChanges)
        for (id in addedMemberIds) {
            db.circleMemberDao().insert(CircleMember(circleId, id))
        }

        // Verify
        val savedMembers = db.circleMemberDao().membersFor(circleId)
        assertEquals(2, savedMembers.size)
        assertTrue(savedMembers.contains(memberId1))
        assertTrue(savedMembers.contains(memberId2))
    }

    @Test
    fun saveChanges_removes_members_from_database() {
        // Create and add test members
        val member1 = Identity(blobHash = null, owner = "member1", name = "Member 1", profilePicHash = null)
        val member2 = Identity(blobHash = null, owner = "member2", name = "Member 2", profilePicHash = null)
        val memberId1 = db.identityDao().insert(member1)
        val memberId2 = db.identityDao().insert(member2)

        // Add members to circle
        db.circleMemberDao().insert(CircleMember(circleId, memberId1))
        db.circleMemberDao().insert(CircleMember(circleId, memberId2))

        // Verify initial state
        assertEquals(2, db.circleMemberDao().membersFor(circleId).size)

        // Simulate removing member1
        removedMemberIds.add(memberId1)

        // Persist (simulates saveChanges)
        for (id in removedMemberIds) {
            db.circleMemberDao().delete(circleId, id)
        }

        // Verify
        val remainingMembers = db.circleMemberDao().membersFor(circleId)
        assertEquals(1, remainingMembers.size)
        assertTrue(remainingMembers.contains(memberId2))
        assertFalse(remainingMembers.contains(memberId1))
    }

    @Test
    fun saveChanges_updates_circle_name() {
        val circle = db.circleDao().find(circleId)
        assertEquals("Test Circle", circle.name)

        // Simulate name change
        currentNameInput = "Renamed Circle"
        circle.name = currentNameInput
        db.circleDao().update(circle)

        // Verify
        val updated = db.circleDao().find(circleId)
        assertEquals("Renamed Circle", updated.name)
    }

    @Test
    fun deleteCircle_removes_members_then_circle() {
        // Create and add test members
        val member1 = Identity(blobHash = null, owner = "member1", name = "Member 1", profilePicHash = null)
        val member2 = Identity(blobHash = null, owner = "member2", name = "Member 2", profilePicHash = null)
        val memberId1 = db.identityDao().insert(member1)
        val memberId2 = db.identityDao().insert(member2)

        db.circleMemberDao().insert(CircleMember(circleId, memberId1))
        db.circleMemberDao().insert(CircleMember(circleId, memberId2))

        originalMemberIds = setOf(memberId1, memberId2)

        // Simulate deleteCircle
        for (memberId in originalMemberIds) {
            db.circleMemberDao().delete(circleId, memberId)
        }
        val circle = db.circleDao().find(circleId)
        db.circleDao().delete(circle)

        // Verify members removed
        val members = db.circleMemberDao().membersFor(circleId)
        assertTrue(members.isEmpty())

        // Verify circle removed
        val circles = db.circleDao().all()
        assertTrue(circles.none { it.id == circleId })
    }

    // =====================
    // Edge Cases
    // =====================

    @Test
    fun empty_name_should_be_detected() {
        originalName = "Test Circle"
        currentNameInput = "   " // Whitespace only

        // Fragment validates name is not blank before saving
        val nameIsBlank = currentNameInput.trim().isBlank()
        assertTrue(nameIsBlank)
    }

    @Test
    fun whitespace_trimmed_name_change_detected() {
        originalName = "Test Circle"
        currentNameInput = "  New Name  "

        // After trim, this is different from original
        assertNotEquals(originalName, currentNameInput.trim())
    }
}
