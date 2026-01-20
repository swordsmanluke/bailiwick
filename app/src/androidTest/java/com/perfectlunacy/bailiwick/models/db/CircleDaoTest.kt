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
 * Tests for CircleDao, covering the operations used by EditCircleFragment
 * for managing circles (create, rename, delete).
 */
@RunWith(AndroidJUnit4::class)
class CircleDaoTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var circleDao: CircleDao
    private lateinit var identityDao: IdentityDao

    private var ownerIdentityId: Long = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        circleDao = db.circleDao()
        identityDao = db.identityDao()

        // Create owner identity for circles
        val ownerIdentity = Identity(
            blobHash = null,
            owner = "owner-node",
            name = "Owner",
            profilePicHash = null
        )
        ownerIdentityId = identityDao.insert(ownerIdentity)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =====================
    // Insert Tests
    // =====================

    @Test
    fun insert_creates_circle_with_generated_id() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)

        val id = circleDao.insert(circle)

        assertTrue(id > 0)
    }

    @Test
    fun insert_multiple_circles_creates_unique_ids() {
        val circle1 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)

        val id1 = circleDao.insert(circle1)
        val id2 = circleDao.insert(circle2)

        assertNotEquals(id1, id2)
    }

    // =====================
    // Find Tests
    // =====================

    @Test
    fun find_returns_circle_by_id() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        val found = circleDao.find(id)

        assertEquals(id, found.id)
        assertEquals("Friends", found.name)
        assertEquals(ownerIdentityId, found.identityId)
    }

    @Test
    fun find_returns_correct_circle_among_multiple() {
        val circle1 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)
        val circle3 = Circle(name = "Work", identityId = ownerIdentityId, blobHash = null)

        circleDao.insert(circle1)
        val id2 = circleDao.insert(circle2)
        circleDao.insert(circle3)

        val found = circleDao.find(id2)

        assertEquals("Family", found.name)
    }

    // =====================
    // Update Tests (used by EditCircleFragment.saveChanges)
    // =====================

    @Test
    fun update_changes_circle_name() {
        val circle = Circle(name = "Old Name", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        val toUpdate = circleDao.find(id)
        toUpdate.name = "New Name"
        circleDao.update(toUpdate)

        val updated = circleDao.find(id)
        assertEquals("New Name", updated.name)
    }

    @Test
    fun update_changes_blob_hash() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        val toUpdate = circleDao.find(id)
        toUpdate.blobHash = "new-hash-123"
        circleDao.update(toUpdate)

        val updated = circleDao.find(id)
        assertEquals("new-hash-123", updated.blobHash)
    }

    @Test
    fun update_preserves_other_fields() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = "original-hash")
        val id = circleDao.insert(circle)

        val toUpdate = circleDao.find(id)
        toUpdate.name = "New Name"
        circleDao.update(toUpdate)

        val updated = circleDao.find(id)
        assertEquals("New Name", updated.name)
        assertEquals(ownerIdentityId, updated.identityId)
        assertEquals("original-hash", updated.blobHash)
    }

    @Test
    fun update_does_not_affect_other_circles() {
        val circle1 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)
        val id1 = circleDao.insert(circle1)
        val id2 = circleDao.insert(circle2)

        val toUpdate = circleDao.find(id1)
        toUpdate.name = "Updated Friends"
        circleDao.update(toUpdate)

        val other = circleDao.find(id2)
        assertEquals("Family", other.name)
    }

    // =====================
    // Delete Tests (used by EditCircleFragment.deleteCircle)
    // =====================

    @Test
    fun delete_removes_circle() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        val toDelete = circleDao.find(id)
        circleDao.delete(toDelete)

        val all = circleDao.all()
        assertTrue(all.none { it.id == id })
    }

    @Test
    fun delete_does_not_affect_other_circles() {
        val circle1 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)
        val id1 = circleDao.insert(circle1)
        val id2 = circleDao.insert(circle2)

        val toDelete = circleDao.find(id1)
        circleDao.delete(toDelete)

        val remaining = circleDao.all()
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining[0].id)
    }

    // =====================
    // All Tests
    // =====================

    @Test
    fun all_returns_empty_when_no_circles() {
        val all = circleDao.all()
        assertTrue(all.isEmpty())
    }

    @Test
    fun all_returns_all_circles() {
        val circle1 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)
        val circle3 = Circle(name = "Work", identityId = ownerIdentityId, blobHash = null)

        circleDao.insert(circle1)
        circleDao.insert(circle2)
        circleDao.insert(circle3)

        val all = circleDao.all()
        assertEquals(3, all.size)
    }

    // =====================
    // CirclesFor Tests
    // =====================

    @Test
    fun circlesFor_returns_circles_owned_by_identity() {
        val circle1 = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val circle2 = Circle(name = "Family", identityId = ownerIdentityId, blobHash = null)

        circleDao.insert(circle1)
        circleDao.insert(circle2)

        val circles = circleDao.circlesFor(ownerIdentityId)
        assertEquals(2, circles.size)
    }

    @Test
    fun circlesFor_returns_empty_for_nonexistent_identity() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        circleDao.insert(circle)

        val circles = circleDao.circlesFor(999L)
        assertTrue(circles.isEmpty())
    }

    // =====================
    // Hash Management Tests
    // =====================

    @Test
    fun storeHash_updates_blob_hash() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        circleDao.storeHash(id, "new-blob-hash")

        val updated = circleDao.find(id)
        assertEquals("new-blob-hash", updated.blobHash)
    }

    @Test
    fun clearHash_sets_blob_hash_to_null() {
        val circle = Circle(name = "Friends", identityId = ownerIdentityId, blobHash = "existing-hash")
        val id = circleDao.insert(circle)

        circleDao.clearHash(id)

        val updated = circleDao.find(id)
        assertNull(updated.blobHash)
    }

    // =====================
    // EditCircleFragment Integration Scenarios
    // =====================

    @Test
    fun editCircle_rename_workflow() {
        // Simulate EditCircleFragment rename workflow
        val circle = Circle(name = "Original Name", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        // Load circle (as EditCircleFragment.loadCircleData does)
        val loaded = circleDao.find(id)
        assertEquals("Original Name", loaded.name)

        // Update name (as EditCircleFragment.saveChanges does)
        loaded.name = "Renamed Circle"
        circleDao.update(loaded)

        // Verify change persisted
        val verified = circleDao.find(id)
        assertEquals("Renamed Circle", verified.name)
    }

    @Test
    fun editCircle_delete_workflow() {
        // Simulate EditCircleFragment delete workflow
        val circle = Circle(name = "To Delete", identityId = ownerIdentityId, blobHash = null)
        val id = circleDao.insert(circle)

        // Verify circle exists
        var all = circleDao.all()
        assertEquals(1, all.size)

        // Delete circle (as EditCircleFragment.deleteCircle does)
        val toDelete = circleDao.find(id)
        circleDao.delete(toDelete)

        // Verify circle is gone
        all = circleDao.all()
        assertTrue(all.isEmpty())
    }
}
