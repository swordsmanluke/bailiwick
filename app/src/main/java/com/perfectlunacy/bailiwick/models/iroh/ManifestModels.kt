package com.perfectlunacy.bailiwick.models.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Manifest data models for Gossip-based synchronization.
 *
 * The manifest system uses a hierarchical structure:
 * - UserManifest: Top-level manifest containing identity and circle references
 * - CircleManifest: Per-circle manifest listing posts and members
 *
 * These are serialized to JSON and stored as Iroh blobs.
 * Announcements are broadcast via Gossip to notify peers of updates.
 */

/**
 * Top-level user manifest, encrypted with a key derived from the user's Ed25519 key.
 *
 * Contains references to:
 * - User's identity blob
 * - Per-circle manifest blobs (each encrypted with circle key)
 * - Pending actions per peer
 */
data class UserManifest(
    /** Monotonically increasing version number */
    val version: Long,

    /** Hash of the user's IrohIdentity blob */
    val identityHash: BlobHash,

    /** Map of CircleId to encrypted CircleManifest blob hash */
    val circleManifests: Map<Int, BlobHash>,

    /** Pending actions per peer (NodeId -> list of action blob hashes) */
    val actions: Map<NodeId, List<BlobHash>>,

    /** When this manifest was last updated */
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Content retention period in milliseconds (30 days) */
        const val RETENTION_PERIOD_MS = 30L * 24 * 60 * 60 * 1000
    }
}

data class CircleManifest(
    /** Circle ID (local database ID) */
    val circleId: Int,

    /** Circle name for display */
    val name: String,

    /** List of posts in this circle, ordered by timestamp (newest first) */
    val posts: List<PostEntry>,

    /** List of reactions to posts in this circle */
    val reactions: List<ReactionEntry> = emptyList(),

    /** List of current member NodeIds */
    val members: List<NodeId>,

    /** When this manifest was last updated */
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Entry for a post in a circle manifest.
 *
 * Contains the minimal information needed to identify and fetch a post.
 */
data class PostEntry(
    /** Hash of the encrypted post blob */
    val hash: BlobHash,

    /** Post timestamp (for ordering and retention filtering) */
    val timestamp: Long,

    /** NodeId of the post author (for multi-author circles) */
    val authorNodeId: NodeId
)


/**
 * Entry for a reaction in a circle manifest.
 *
 * Contains the minimal information needed to identify and fetch a reaction.
 */
data class ReactionEntry(
    /** Hash of the encrypted reaction blob */
    val hash: BlobHash,

    /** Hash of the post being reacted to */
    val postHash: BlobHash,

    /** NodeId of the reaction author */
    val authorNodeId: NodeId,

    /** Reaction timestamp (for ordering and retention filtering) */
    val timestamp: Long
)

/**
 * Announcement message broadcast via Gossip to notify peers of manifest updates.
 *
 * The announcement is signed but NOT encrypted - it only contains the manifest hash,
 * not the manifest content. Peers need the manifest hash to fetch and decrypt it.
 */
data class ManifestAnnouncement(
    /** Hash of the encrypted UserManifest blob */
    val manifestHash: BlobHash,

    /** Current manifest version (monotonically increasing) */
    val version: Long,

    /** When this announcement was created */
    val timestamp: Long = System.currentTimeMillis(),

    /** Ed25519 signature over (manifestHash + version) for authenticity */
    val signature: String
)

/**
 * Utility functions for manifest operations.
 */
object ManifestUtils {
    /**
     * Filter posts to only include those within the retention period.
     *
     * @param posts List of post entries to filter
     * @param retentionPeriodMs Retention period in milliseconds (default: 30 days)
     * @param now Current timestamp (default: System.currentTimeMillis())
     * @return Filtered list of posts within the retention period
     */
    fun filterByRetention(
        posts: List<PostEntry>,
        retentionPeriodMs: Long = UserManifest.RETENTION_PERIOD_MS,
        now: Long = System.currentTimeMillis()
    ): List<PostEntry> {
        val cutoff = now - retentionPeriodMs
        return posts.filter { it.timestamp >= cutoff }
    }

    /**
     * Filter reactions to only include those within the retention period.
     *
     * @param reactions List of reaction entries to filter
     * @param retentionPeriodMs Retention period in milliseconds (default: 30 days)
     * @param now Current timestamp (default: System.currentTimeMillis())
     * @return Filtered list of reactions within the retention period
     */
    fun filterReactionsByRetention(
        reactions: List<ReactionEntry>,
        retentionPeriodMs: Long = UserManifest.RETENTION_PERIOD_MS,
        now: Long = System.currentTimeMillis()
    ): List<ReactionEntry> {
        val cutoff = now - retentionPeriodMs
        return reactions.filter { it.timestamp >= cutoff }
    }

    /**
     * Build a CircleManifest with retention filtering applied.
     *
     * @param circleId The circle ID
     * @param name The circle name
     * @param posts List of all posts (will be filtered)
     * @param reactions List of reactions (will be filtered)
     * @param members List of member NodeIds
     * @return CircleManifest with only posts/reactions within retention period
     */
    fun buildCircleManifest(
        circleId: Int,
        name: String,
        posts: List<PostEntry>,
        reactions: List<ReactionEntry> = emptyList(),
        members: List<NodeId>
    ): CircleManifest {
        val filteredPosts = filterByRetention(posts)
            .sortedByDescending { it.timestamp }  // Newest first
        val filteredReactions = filterReactionsByRetention(reactions)
            .sortedByDescending { it.timestamp }
        return CircleManifest(
            circleId = circleId,
            name = name,
            posts = filteredPosts,
            reactions = filteredReactions,
            members = members
        )
    }
}
