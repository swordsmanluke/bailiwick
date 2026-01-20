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
 * Integration tests for the data operations used by EditIdentityFragment.
 * Tests the database operations for:
 * - Loading identity data for editing
 * - Updating identity name
 * - Updating identity avatar (profile pic hash)
 * - Verifying changes persist correctly
 */
@RunWith(AndroidJUnit4::class)
class EditIdentityDataTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase

    private val ownerNodeId = "my-node-id"
    private var identityId: Long = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create the "me" identity (the app owner's identity)
        val myIdentity = Identity(
            blobHash = "my-identity-blob-hash",
            owner = ownerNodeId,
            name = "My Display Name",
            profilePicHash = "my-avatar-hash"
        )
        identityId = db.identityDao().insert(myIdentity)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =====================
    // Load Identity Tests
    // =====================

    @Test
    fun loadIdentity_returns_correct_data() {
        val identity = db.identityDao().find(identityId)

        assertNotNull(identity)
        assertEquals("My Display Name", identity.name)
        assertEquals("my-avatar-hash", identity.profilePicHash)
        assertEquals(ownerNodeId, identity.owner)
    }

    @Test
    fun loadIdentity_by_owner_returns_my_identity() {
        val identity = db.identityDao().findByOwner(ownerNodeId)

        assertNotNull(identity)
        assertEquals("My Display Name", identity!!.name)
        assertEquals(identityId, identity.id)
    }

    // =====================
    // Update Name Tests
    // =====================

    @Test
    fun updateIdentityName_persists_change() {
        val original = db.identityDao().find(identityId)
        assertEquals("My Display Name", original.name)

        // Update name
        val updated = Identity(
            blobHash = original.blobHash,
            owner = original.owner,
            name = "New Display Name",
            profilePicHash = original.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify
        val result = db.identityDao().find(identityId)
        assertEquals("New Display Name", result.name)
    }

    @Test
    fun updateIdentityName_preserves_other_fields() {
        val original = db.identityDao().find(identityId)

        // Update only name
        val updated = Identity(
            blobHash = original.blobHash,
            owner = original.owner,
            name = "Changed Name",
            profilePicHash = original.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify other fields unchanged
        val result = db.identityDao().find(identityId)
        assertEquals("my-identity-blob-hash", result.blobHash)
        assertEquals(ownerNodeId, result.owner)
        assertEquals("my-avatar-hash", result.profilePicHash)
    }

    @Test
    fun updateIdentityName_to_empty_string() {
        val original = db.identityDao().find(identityId)

        val updated = Identity(
            blobHash = original.blobHash,
            owner = original.owner,
            name = "",
            profilePicHash = original.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        val result = db.identityDao().find(identityId)
        assertEquals("", result.name)
    }

    // =====================
    // Update Avatar Tests
    // =====================

    @Test
    fun updateProfilePicHash_persists_change() {
        val original = db.identityDao().find(identityId)
        assertEquals("my-avatar-hash", original.profilePicHash)

        // Update avatar
        val updated = Identity(
            blobHash = original.blobHash,
            owner = original.owner,
            name = original.name,
            profilePicHash = "new-uploaded-avatar-hash"
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify
        val result = db.identityDao().find(identityId)
        assertEquals("new-uploaded-avatar-hash", result.profilePicHash)
    }

    @Test
    fun updateProfilePicHash_preserves_other_fields() {
        val original = db.identityDao().find(identityId)

        // Update only avatar
        val updated = Identity(
            blobHash = original.blobHash,
            owner = original.owner,
            name = original.name,
            profilePicHash = "robot-generated-avatar"
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify other fields unchanged
        val result = db.identityDao().find(identityId)
        assertEquals("My Display Name", result.name)
        assertEquals("my-identity-blob-hash", result.blobHash)
        assertEquals(ownerNodeId, result.owner)
    }

    @Test
    fun updateProfilePicHash_from_null() {
        // Create identity with no avatar
        val noAvatarIdentity = Identity(
            blobHash = "blob-hash",
            owner = "other-node",
            name = "No Avatar User",
            profilePicHash = null
        )
        val id = db.identityDao().insert(noAvatarIdentity)

        // Verify null initially
        val initial = db.identityDao().find(id)
        assertNull(initial.profilePicHash)

        // Add avatar
        val updated = Identity(
            blobHash = initial.blobHash,
            owner = initial.owner,
            name = initial.name,
            profilePicHash = "new-avatar-hash"
        )
        updated.id = id
        db.identityDao().update(updated)

        // Verify avatar added
        val result = db.identityDao().find(id)
        assertEquals("new-avatar-hash", result.profilePicHash)
    }

    @Test
    fun updateProfilePicHash_to_null() {
        val original = db.identityDao().find(identityId)
        assertNotNull(original.profilePicHash)

        // Remove avatar
        val updated = Identity(
            blobHash = original.blobHash,
            owner = original.owner,
            name = original.name,
            profilePicHash = null
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify avatar removed
        val result = db.identityDao().find(identityId)
        assertNull(result.profilePicHash)
    }

    // =====================
    // Full Edit Workflow Tests
    // =====================

    @Test
    fun editIdentity_full_workflow_name_only() {
        // Step 1: Load identity (simulates fragment load)
        val loaded = db.identityDao().find(identityId)
        val originalName = loaded.name
        assertEquals("My Display Name", originalName)

        // Step 2: User edits name (simulates user input)
        val newName = "Alice Wonderland"

        // Step 3: Save changes (simulates save button)
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = newName,
            profilePicHash = loaded.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Step 4: Verify changes persisted
        val result = db.identityDao().find(identityId)
        assertEquals("Alice Wonderland", result.name)
        assertEquals("my-avatar-hash", result.profilePicHash) // unchanged
    }

    @Test
    fun editIdentity_full_workflow_avatar_only() {
        // Step 1: Load identity
        val loaded = db.identityDao().find(identityId)
        val originalAvatar = loaded.profilePicHash
        assertEquals("my-avatar-hash", originalAvatar)

        // Step 2: User picks new avatar (simulated by new hash)
        val newAvatarHash = "camera-photo-blob-hash"

        // Step 3: Save changes
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = loaded.name,
            profilePicHash = newAvatarHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Step 4: Verify
        val result = db.identityDao().find(identityId)
        assertEquals("My Display Name", result.name) // unchanged
        assertEquals("camera-photo-blob-hash", result.profilePicHash)
    }

    @Test
    fun editIdentity_full_workflow_both_name_and_avatar() {
        // Load
        val loaded = db.identityDao().find(identityId)

        // User edits both
        val newName = "Bob Smith"
        val newAvatarHash = "gallery-photo-hash"

        // Save
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = newName,
            profilePicHash = newAvatarHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify
        val result = db.identityDao().find(identityId)
        assertEquals("Bob Smith", result.name)
        assertEquals("gallery-photo-hash", result.profilePicHash)
    }

    @Test
    fun editIdentity_workflow_robot_avatar() {
        // Simulate generating a robot avatar
        val loaded = db.identityDao().find(identityId)

        // Robot avatar generated (simulated)
        val robotAvatarHash = "robohash-generated-avatar"

        // Save with robot avatar
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = loaded.name,
            profilePicHash = robotAvatarHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify
        val result = db.identityDao().find(identityId)
        assertEquals("robohash-generated-avatar", result.profilePicHash)
    }

    // =====================
    // Cancel/Discard Tests
    // =====================

    @Test
    fun editIdentity_cancel_without_saving_preserves_original() {
        // Load original state
        val original = db.identityDao().find(identityId)
        assertEquals("My Display Name", original.name)
        assertEquals("my-avatar-hash", original.profilePicHash)

        // User makes changes but does NOT save (simulates cancel)
        // The database should still have original values

        // Verify unchanged
        val result = db.identityDao().find(identityId)
        assertEquals("My Display Name", result.name)
        assertEquals("my-avatar-hash", result.profilePicHash)
    }

    // =====================
    // Edge Cases
    // =====================

    @Test
    fun editIdentity_unicode_name() {
        val loaded = db.identityDao().find(identityId)

        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = "日本語名前 🎉",
            profilePicHash = loaded.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        val result = db.identityDao().find(identityId)
        assertEquals("日本語名前 🎉", result.name)
    }

    @Test
    fun editIdentity_very_long_name() {
        val loaded = db.identityDao().find(identityId)

        val longName = "A".repeat(500)
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = longName,
            profilePicHash = loaded.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        val result = db.identityDao().find(identityId)
        assertEquals(longName, result.name)
        assertEquals(500, result.name.length)
    }

    @Test
    fun editIdentity_special_characters_in_name() {
        val loaded = db.identityDao().find(identityId)

        val specialName = "Test <User> & \"Quotes\" 'Apostrophe'"
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = specialName,
            profilePicHash = loaded.profilePicHash
        )
        updated.id = identityId
        db.identityDao().update(updated)

        val result = db.identityDao().find(identityId)
        assertEquals(specialName, result.name)
    }

    @Test
    fun editIdentity_multiple_sequential_edits() {
        // First edit
        var loaded = db.identityDao().find(identityId)
        var updated = Identity(loaded.blobHash, loaded.owner, "Name v1", loaded.profilePicHash)
        updated.id = identityId
        db.identityDao().update(updated)

        // Second edit
        loaded = db.identityDao().find(identityId)
        updated = Identity(loaded.blobHash, loaded.owner, "Name v2", "avatar-v2")
        updated.id = identityId
        db.identityDao().update(updated)

        // Third edit
        loaded = db.identityDao().find(identityId)
        updated = Identity(loaded.blobHash, loaded.owner, "Name v3", "avatar-v3")
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify final state
        val result = db.identityDao().find(identityId)
        assertEquals("Name v3", result.name)
        assertEquals("avatar-v3", result.profilePicHash)
    }

    // =====================
    // Multi-Identity Scenarios
    // =====================

    @Test
    fun editIdentity_does_not_affect_other_identities() {
        // Create another identity (contact)
        val contactIdentity = Identity(
            blobHash = "contact-blob",
            owner = "contact-node-id",
            name = "Contact Name",
            profilePicHash = "contact-avatar"
        )
        val contactId = db.identityDao().insert(contactIdentity)

        // Edit my identity
        val loaded = db.identityDao().find(identityId)
        val updated = Identity(
            blobHash = loaded.blobHash,
            owner = loaded.owner,
            name = "Updated My Name",
            profilePicHash = "updated-my-avatar"
        )
        updated.id = identityId
        db.identityDao().update(updated)

        // Verify my identity updated
        val myResult = db.identityDao().find(identityId)
        assertEquals("Updated My Name", myResult.name)

        // Verify contact unchanged
        val contactResult = db.identityDao().find(contactId)
        assertEquals("Contact Name", contactResult.name)
        assertEquals("contact-avatar", contactResult.profilePicHash)
    }

    @Test
    fun identityDao_handles_multiple_identities_for_same_owner() {
        // Some protocols allow multiple identities per node
        val secondIdentity = Identity(
            blobHash = "second-blob",
            owner = ownerNodeId,
            name = "My Second Identity",
            profilePicHash = "second-avatar"
        )
        val secondId = db.identityDao().insert(secondIdentity)

        // Get all my identities
        val myIdentities = db.identityDao().identitiesFor(ownerNodeId)
        assertEquals(2, myIdentities.size)

        // Edit first identity
        val first = db.identityDao().find(identityId)
        val updatedFirst = Identity(first.blobHash, first.owner, "First Updated", first.profilePicHash)
        updatedFirst.id = identityId
        db.identityDao().update(updatedFirst)

        // Verify first updated, second unchanged
        val result1 = db.identityDao().find(identityId)
        val result2 = db.identityDao().find(secondId)
        assertEquals("First Updated", result1.name)
        assertEquals("My Second Identity", result2.name)
    }
}
