package com.perfectlunacy.bailiwick.models.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * A reaction to a post (emoji reaction).
 *
 * Reactions are identified by the combination of postHash, authorNodeId, and emoji.
 * A user can have at most one reaction of each emoji type per post.
 */
@Entity(
    indices = [
        Index(value = ["postHash"]),
        Index(value = ["authorNodeId", "postHash", "emoji"], unique = true)
    ]
)
data class Reaction(
    val postHash: BlobHash,           // Hash of the post being reacted to
    val authorNodeId: NodeId,         // Node ID of the user who reacted
    val emoji: String,                // The emoji reaction (e.g., "👍", "❤️", "😂")
    val timestamp: Long,              // When the reaction was created
    val signature: String,            // Signature proving authenticity
    val blobHash: BlobHash?           // Iroh blob hash for sync
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

/**
 * Aggregated reaction count for display.
 */
data class ReactionCount(
    val emoji: String,
    val count: Int,
    val userReacted: Boolean          // Whether the current user has this reaction
)

@Dao
interface ReactionDao {
    @Query("SELECT * FROM reaction WHERE postHash = :postHash ORDER BY timestamp ASC")
    fun reactionsForPost(postHash: BlobHash): List<Reaction>

    @Query("SELECT * FROM reaction WHERE postHash = :postHash ORDER BY timestamp ASC")
    fun reactionsForPostLive(postHash: BlobHash): LiveData<List<Reaction>>

    @Query("SELECT * FROM reaction WHERE postHash = :postHash AND authorNodeId = :nodeId")
    fun myReactionsForPost(postHash: BlobHash, nodeId: NodeId): List<Reaction>

    @Query("SELECT * FROM reaction WHERE postHash = :postHash AND authorNodeId = :nodeId AND emoji = :emoji LIMIT 1")
    fun findReaction(postHash: BlobHash, nodeId: NodeId, emoji: String): Reaction?

    @Query("SELECT * FROM reaction WHERE blobHash IS NULL")
    fun inNeedOfSync(): List<Reaction>

    @Query("SELECT * FROM reaction WHERE authorNodeId = :nodeId AND blobHash IS NOT NULL")
    fun myPublishedReactions(nodeId: NodeId): List<Reaction>

    @Query("SELECT EXISTS(SELECT 1 FROM reaction WHERE blobHash = :hash)")
    fun reactionExists(hash: BlobHash): Boolean

    @Query("SELECT * FROM reaction WHERE blobHash = :hash")
    fun findByHash(hash: BlobHash): Reaction?

    @Query("UPDATE reaction SET blobHash = :hash WHERE id = :id")
    fun updateHash(id: Long, hash: BlobHash)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(reaction: Reaction): Long

    @Delete
    fun delete(reaction: Reaction)

    @Query("DELETE FROM reaction WHERE postHash = :postHash AND authorNodeId = :nodeId AND emoji = :emoji")
    fun deleteReaction(postHash: BlobHash, nodeId: NodeId, emoji: String)

    @Query("SELECT emoji, COUNT(*) as count FROM reaction WHERE postHash = :postHash GROUP BY emoji ORDER BY count DESC")
    fun reactionCountsForPost(postHash: BlobHash): List<EmojiCount>
}

/**
 * Helper class for aggregated emoji counts.
 */
data class EmojiCount(
    val emoji: String,
    val count: Int
)
