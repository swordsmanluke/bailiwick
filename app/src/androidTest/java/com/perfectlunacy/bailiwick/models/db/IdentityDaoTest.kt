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
 * Tests for IdentityDao, especially the functionality used by the Identity Editor
 * for loading and updating user identity information.
 */
@RunWith(AndroidJUnit4::class)
class IdentityDaoTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var identityDao: IdentityDao

    private val testNodeId1 = "node-id-1"
    private val testNodeId2 = "node-id-2"
    private val testBlobHash1 = "blob-hash-1"
    private val testBlobHash2 = "blob-hash-2"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        identityDao = db.identityDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =====================
    // Insert and Find Tests
    // =====================

    @Test
    fun insert_and_find_by_id() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = "avatar-hash"
        )

        val id = identityDao.insert(identity)

        val found = identityDao.find(id)
        assertNotNull(found)
        assertEquals(testNodeId1, found.owner)
        assertEquals("Test User", found.name)
        assertEquals(testBlobHash1, found.blobHash)
        assertEquals("avatar-hash", found.profilePicHash)
    }

    @Test
    fun all_returns_all_identities() {
        val identity1 = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "User One",
            profilePicHash = null
        )
        val identity2 = Identity(
            blobHash = testBlobHash2,
            owner = testNodeId2,
            name = "User Two",
            profilePicHash = null
        )
        identityDao.insert(identity1)
        identityDao.insert(identity2)

        val all = identityDao.all()
        assertEquals(2, all.size)
    }

    @Test
    fun all_returns_empty_when_no_identities() {
        val all = identityDao.all()
        assertTrue(all.isEmpty())
    }

    // =====================
    // findByHash Tests
    // =====================

    @Test
    fun findByHash_returns_correct_identity() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        identityDao.insert(identity)

        val found = identityDao.findByHash(testBlobHash1)
        assertNotNull(found)
        assertEquals("Test User", found!!.name)
    }

    @Test
    fun findByHash_returns_null_for_unknown_hash() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        identityDao.insert(identity)

        val found = identityDao.findByHash("unknown-hash")
        assertNull(found)
    }

    @Test
    fun findByHash_with_null_blobHash() {
        val identity = Identity(
            blobHash = null,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        identityDao.insert(identity)

        // Looking for null should not match
        val found = identityDao.findByHash("some-hash")
        assertNull(found)
    }

    // =====================
    // identitiesFor Tests
    // =====================

    @Test
    fun identitiesFor_returns_all_identities_for_owner() {
        val identity1 = Identity(
            blobHash = "hash-1",
            owner = testNodeId1,
            name = "Identity 1",
            profilePicHash = null
        )
        val identity2 = Identity(
            blobHash = "hash-2",
            owner = testNodeId1,
            name = "Identity 2",
            profilePicHash = null
        )
        val identity3 = Identity(
            blobHash = "hash-3",
            owner = testNodeId2,
            name = "Other User",
            profilePicHash = null
        )
        identityDao.insert(identity1)
        identityDao.insert(identity2)
        identityDao.insert(identity3)

        val identities = identityDao.identitiesFor(testNodeId1)
        assertEquals(2, identities.size)
        assertTrue(identities.any { it.name == "Identity 1" })
        assertTrue(identities.any { it.name == "Identity 2" })
    }

    @Test
    fun identitiesFor_returns_empty_for_unknown_owner() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        identityDao.insert(identity)

        val identities = identityDao.identitiesFor("unknown-node")
        assertTrue(identities.isEmpty())
    }

    // =====================
    // findByOwner Tests
    // =====================

    @Test
    fun findByOwner_returns_first_identity_for_owner() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        identityDao.insert(identity)

        val found = identityDao.findByOwner(testNodeId1)
        assertNotNull(found)
        assertEquals("Test User", found!!.name)
    }

    @Test
    fun findByOwner_returns_null_for_unknown_owner() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        identityDao.insert(identity)

        val found = identityDao.findByOwner("unknown-node")
        assertNull(found)
    }

    // =====================
    // Update Tests
    // =====================

    @Test
    fun update_changes_identity_name() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Original Name",
            profilePicHash = null
        )
        val id = identityDao.insert(identity)

        // Retrieve and update
        val toUpdate = identityDao.find(id)
        val updated = Identity(
            blobHash = toUpdate.blobHash,
            owner = toUpdate.owner,
            name = "Updated Name",
            profilePicHash = toUpdate.profilePicHash
        )
        updated.id = id
        identityDao.update(updated)

        // Verify
        val found = identityDao.find(id)
        assertEquals("Updated Name", found.name)
    }

    @Test
    fun update_changes_profile_pic_hash() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = "old-avatar"
        )
        val id = identityDao.insert(identity)

        // Update profile pic hash
        val toUpdate = identityDao.find(id)
        val updated = Identity(
            blobHash = toUpdate.blobHash,
            owner = toUpdate.owner,
            name = toUpdate.name,
            profilePicHash = "new-avatar"
        )
        updated.id = id
        identityDao.update(updated)

        // Verify
        val found = identityDao.find(id)
        assertEquals("new-avatar", found.profilePicHash)
    }

    @Test
    fun update_name_and_avatar_together() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Original Name",
            profilePicHash = "old-avatar"
        )
        val id = identityDao.insert(identity)

        // Update both name and avatar
        val updated = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "New Name",
            profilePicHash = "new-avatar"
        )
        updated.id = id
        identityDao.update(updated)

        // Verify
        val found = identityDao.find(id)
        assertEquals("New Name", found.name)
        assertEquals("new-avatar", found.profilePicHash)
    }

    @Test
    fun update_only_affects_target_identity() {
        val identity1 = Identity(
            blobHash = "hash-1",
            owner = testNodeId1,
            name = "User One",
            profilePicHash = null
        )
        val identity2 = Identity(
            blobHash = "hash-2",
            owner = testNodeId2,
            name = "User Two",
            profilePicHash = null
        )
        val id1 = identityDao.insert(identity1)
        val id2 = identityDao.insert(identity2)

        // Update identity1
        val updated = Identity(
            blobHash = "hash-1",
            owner = testNodeId1,
            name = "Updated One",
            profilePicHash = "new-avatar"
        )
        updated.id = id1
        identityDao.update(updated)

        // Verify identity1 changed
        val found1 = identityDao.find(id1)
        assertEquals("Updated One", found1.name)
        assertEquals("new-avatar", found1.profilePicHash)

        // Verify identity2 unchanged
        val found2 = identityDao.find(id2)
        assertEquals("User Two", found2.name)
        assertNull(found2.profilePicHash)
    }

    // =====================
    // updateHash Tests
    // =====================

    @Test
    fun updateHash_changes_blob_hash() {
        val identity = Identity(
            blobHash = "old-hash",
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        val id = identityDao.insert(identity)

        identityDao.updateHash(id, "new-hash")

        val found = identityDao.find(id)
        assertEquals("new-hash", found.blobHash)
        // Other fields unchanged
        assertEquals("Test User", found.name)
        assertEquals(testNodeId1, found.owner)
    }

    @Test
    fun updateHash_can_set_to_null() {
        val identity = Identity(
            blobHash = "some-hash",
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        val id = identityDao.insert(identity)

        identityDao.updateHash(id, null)

        val found = identityDao.find(id)
        assertNull(found.blobHash)
    }

    // =====================
    // Identity Editor Integration Tests
    // =====================

    @Test
    fun edit_identity_workflow_update_name() {
        // Create initial identity (simulates app setup)
        val identity = Identity(
            blobHash = "identity-hash",
            owner = testNodeId1,
            name = "Original Name",
            profilePicHash = "avatar-hash"
        )
        val id = identityDao.insert(identity)

        // Simulate loading identity in editor
        val loaded = identityDao.find(id)
        assertEquals("Original Name", loaded.name)

        // Simulate user editing name and saving
        val edited = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = "New Display Name",
            profilePicHash = loaded.profilePicHash
        )
        edited.id = id
        identityDao.update(edited)

        // Verify changes persisted
        val result = identityDao.find(id)
        assertEquals("New Display Name", result.name)
        assertEquals("avatar-hash", result.profilePicHash) // Avatar unchanged
    }

    @Test
    fun edit_identity_workflow_update_avatar() {
        // Create initial identity
        val identity = Identity(
            blobHash = "identity-hash",
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = "old-avatar-hash"
        )
        val id = identityDao.insert(identity)

        // Simulate loading and updating avatar
        val loaded = identityDao.find(id)
        val edited = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = loaded.name,
            profilePicHash = "new-uploaded-avatar-hash"
        )
        edited.id = id
        identityDao.update(edited)

        // Verify
        val result = identityDao.find(id)
        assertEquals("Test User", result.name) // Name unchanged
        assertEquals("new-uploaded-avatar-hash", result.profilePicHash)
    }

    @Test
    fun edit_identity_workflow_update_both() {
        // Create initial identity
        val identity = Identity(
            blobHash = "identity-hash",
            owner = testNodeId1,
            name = "Old Name",
            profilePicHash = "old-avatar"
        )
        val id = identityDao.insert(identity)

        // Simulate full profile update
        val loaded = identityDao.find(id)
        val edited = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = "New Name",
            profilePicHash = "new-avatar"
        )
        edited.id = id
        identityDao.update(edited)

        // Verify
        val result = identityDao.find(id)
        assertEquals("New Name", result.name)
        assertEquals("new-avatar", result.profilePicHash)
    }

    // =====================
    // Edge Cases
    // =====================

    @Test
    fun identity_with_null_profile_pic_hash() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "No Avatar User",
            profilePicHash = null
        )
        val id = identityDao.insert(identity)

        val found = identityDao.find(id)
        assertNull(found.profilePicHash)
    }

    @Test
    fun identity_with_null_blob_hash() {
        val identity = Identity(
            blobHash = null,
            owner = testNodeId1,
            name = "Unpublished Identity",
            profilePicHash = null
        )
        val id = identityDao.insert(identity)

        val found = identityDao.find(id)
        assertNull(found.blobHash)
    }

    @Test
    fun update_preserves_id() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Test User",
            profilePicHash = null
        )
        val originalId = identityDao.insert(identity)

        val updated = Identity(
            blobHash = "new-hash",
            owner = testNodeId1,
            name = "Updated Name",
            profilePicHash = "avatar"
        )
        updated.id = originalId
        identityDao.update(updated)

        // ID should be the same
        val found = identityDao.find(originalId)
        assertEquals(originalId, found.id)
    }

    @Test
    fun multiple_updates_to_same_identity() {
        val identity = Identity(
            blobHash = testBlobHash1,
            owner = testNodeId1,
            name = "Version 1",
            profilePicHash = null
        )
        val id = identityDao.insert(identity)

        // First update
        val v2 = Identity(testBlobHash1, testNodeId1, "Version 2", null)
        v2.id = id
        identityDao.update(v2)

        // Second update
        val v3 = Identity(testBlobHash1, testNodeId1, "Version 3", "avatar")
        v3.id = id
        identityDao.update(v3)

        // Third update
        val v4 = Identity("new-blob", testNodeId1, "Version 4", "new-avatar")
        v4.id = id
        identityDao.update(v4)

        // Verify final state
        val found = identityDao.find(id)
        assertEquals("Version 4", found.name)
        assertEquals("new-avatar", found.profilePicHash)
        assertEquals("new-blob", found.blobHash)
    }
}
