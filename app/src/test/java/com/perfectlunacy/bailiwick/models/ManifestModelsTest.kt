package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.perfectlunacy.bailiwick.models.iroh.*
import org.junit.Assert.*
import org.junit.Test

class ManifestModelsTest {

    private val gson: Gson = GsonBuilder().create()

    // ===== PostEntry Tests =====

    @Test
    fun `PostEntry serializes and deserializes correctly`() {
        val entry = PostEntry(
            hash = "abc123hash",
            timestamp = 1700000000000L,
            authorNodeId = "nodeId123"
        )

        val json = gson.toJson(entry)
        val restored = gson.fromJson(json, PostEntry::class.java)

        assertEquals(entry.hash, restored.hash)
        assertEquals(entry.timestamp, restored.timestamp)
        assertEquals(entry.authorNodeId, restored.authorNodeId)
    }

    // ===== CircleManifest Tests =====

    @Test
    fun `CircleManifest serializes and deserializes correctly`() {
        val posts = listOf(
            PostEntry("hash1", 1700000001000L, "author1"),
            PostEntry("hash2", 1700000002000L, "author2")
        )
        val members = listOf("member1", "member2", "member3")

        val manifest = CircleManifest(
            circleId = 42,
            name = "Test Circle",
            posts = posts,
            members = members,
            updatedAt = 1700000003000L
        )

        val json = gson.toJson(manifest)
        val restored = gson.fromJson(json, CircleManifest::class.java)

        assertEquals(manifest.circleId, restored.circleId)
        assertEquals(manifest.name, restored.name)
        assertEquals(manifest.posts.size, restored.posts.size)
        assertEquals(manifest.members.size, restored.members.size)
        assertEquals(manifest.updatedAt, restored.updatedAt)
    }

    // ===== UserManifest Tests =====

    @Test
    fun `UserManifest serializes and deserializes correctly`() {
        val circleManifests = mapOf(
            1 to "circleHash1",
            2 to "circleHash2"
        )
        val actions = mapOf(
            "peer1" to listOf("action1", "action2"),
            "peer2" to listOf("action3")
        )

        val manifest = UserManifest(
            version = 42L,
            identityHash = "identityHash123",
            circleManifests = circleManifests,
            actions = actions,
            updatedAt = 1700000000000L
        )

        val json = gson.toJson(manifest)
        val restored = gson.fromJson(json, UserManifest::class.java)

        assertEquals(manifest.version, restored.version)
        assertEquals(manifest.identityHash, restored.identityHash)
        assertEquals(manifest.circleManifests.size, restored.circleManifests.size)
        assertEquals(manifest.actions.size, restored.actions.size)
        assertEquals(manifest.updatedAt, restored.updatedAt)
    }

    // ===== ManifestAnnouncement Tests =====

    @Test
    fun `ManifestAnnouncement serializes and deserializes correctly`() {
        val announcement = ManifestAnnouncement(
            manifestHash = "manifestHash123",
            version = 100L,
            timestamp = 1700000000000L,
            signature = "signatureBase64"
        )

        val json = gson.toJson(announcement)
        val restored = gson.fromJson(json, ManifestAnnouncement::class.java)

        assertEquals(announcement.manifestHash, restored.manifestHash)
        assertEquals(announcement.version, restored.version)
        assertEquals(announcement.timestamp, restored.timestamp)
        assertEquals(announcement.signature, restored.signature)
    }

    // ===== Retention Filter Tests =====

    @Test
    fun `filterByRetention removes old posts`() {
        val now = System.currentTimeMillis()
        val thirtyOneDaysAgo = now - (31L * 24 * 60 * 60 * 1000)
        val twentyNineDaysAgo = now - (29L * 24 * 60 * 60 * 1000)
        val yesterday = now - (24 * 60 * 60 * 1000)

        val posts = listOf(
            PostEntry("old", thirtyOneDaysAgo, "author1"),       // Should be filtered
            PostEntry("borderline", twentyNineDaysAgo, "author2"), // Should be kept
            PostEntry("recent", yesterday, "author3")            // Should be kept
        )

        val filtered = ManifestUtils.filterByRetention(posts, now = now)

        assertEquals(2, filtered.size)
        assertFalse(filtered.any { it.hash == "old" })
        assertTrue(filtered.any { it.hash == "borderline" })
        assertTrue(filtered.any { it.hash == "recent" })
    }

    @Test
    fun `filterByRetention keeps all posts when all are recent`() {
        val now = System.currentTimeMillis()
        val posts = listOf(
            PostEntry("post1", now - 1000, "author1"),
            PostEntry("post2", now - 2000, "author2"),
            PostEntry("post3", now - 3000, "author3")
        )

        val filtered = ManifestUtils.filterByRetention(posts, now = now)

        assertEquals(3, filtered.size)
    }

    @Test
    fun `filterByRetention returns empty list when all posts are old`() {
        val now = System.currentTimeMillis()
        val fiftyDaysAgo = now - (50L * 24 * 60 * 60 * 1000)

        val posts = listOf(
            PostEntry("old1", fiftyDaysAgo, "author1"),
            PostEntry("old2", fiftyDaysAgo - 1000, "author2")
        )

        val filtered = ManifestUtils.filterByRetention(posts, now = now)

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `buildCircleManifest applies retention and sorts newest first`() {
        val now = System.currentTimeMillis()
        val thirtyOneDaysAgo = now - (31L * 24 * 60 * 60 * 1000)
        val yesterday = now - (24 * 60 * 60 * 1000)
        val twoDaysAgo = now - (2L * 24 * 60 * 60 * 1000)

        val posts = listOf(
            PostEntry("old", thirtyOneDaysAgo, "author1"),
            PostEntry("oldest_kept", twoDaysAgo, "author2"),
            PostEntry("newest", yesterday, "author3")
        )

        val manifest = ManifestUtils.buildCircleManifest(
            circleId = 1,
            name = "Test",
            posts = posts,
            members = listOf("member1")
        )

        assertEquals(2, manifest.posts.size)
        assertEquals("newest", manifest.posts[0].hash)
        assertEquals("oldest_kept", manifest.posts[1].hash)
    }

    // ===== Retention Period Constant Test =====

    @Test
    fun `retention period is 30 days`() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        assertEquals(thirtyDaysMs, UserManifest.RETENTION_PERIOD_MS)
    }
}
