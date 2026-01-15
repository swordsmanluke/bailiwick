package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Maps a peer's NodeId to their Gossip topic for receiving manifest announcements.
 * Replaces PeerDoc - we subscribe to their Gossip topic instead of their Doc.
 */
@Entity(tableName = "peer_topic")
@TypeConverters(CommonConverters::class)
data class PeerTopic(
    @PrimaryKey
    val nodeId: NodeId,

    /** Peer's Ed25519 public key for signature verification (32 bytes) */
    val ed25519PublicKey: ByteArray,

    /** Topic key for subscribing to peer's Gossip announcements (32 bytes) */
    val topicKey: ByteArray,

    /** Known addresses for bootstrapping Gossip connections */
    val addresses: List<String>,

    /** Last known manifest version from this peer */
    val lastKnownVersion: Long = 0,

    /** Hash of the last downloaded manifest (for change detection) */
    val lastManifestHash: BlobHash? = null,

    /** Display name for UI */
    val displayName: String? = null,

    /** Whether we're actively subscribed to this peer's topic */
    val isSubscribed: Boolean = true,

    /** When we last successfully synced with this peer */
    val lastSyncedAt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PeerTopic

        if (nodeId != other.nodeId) return false
        if (!ed25519PublicKey.contentEquals(other.ed25519PublicKey)) return false
        if (!topicKey.contentEquals(other.topicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + ed25519PublicKey.contentHashCode()
        result = 31 * result + topicKey.contentHashCode()
        return result
    }
}

@Dao
interface PeerTopicDao {
    @Query("SELECT * FROM peer_topic WHERE isSubscribed = 1")
    fun subscribedPeers(): List<PeerTopic>

    @Query("SELECT * FROM peer_topic WHERE nodeId = :nodeId")
    fun findByNodeId(nodeId: NodeId): PeerTopic?

    @Query("SELECT * FROM peer_topic")
    fun all(): List<PeerTopic>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(peerTopic: PeerTopic)

    @Query("UPDATE peer_topic SET lastKnownVersion = :version, lastManifestHash = :hash, lastSyncedAt = :timestamp WHERE nodeId = :nodeId")
    fun updateManifestInfo(nodeId: NodeId, version: Long, hash: BlobHash?, timestamp: Long)

    @Query("UPDATE peer_topic SET lastSyncedAt = :timestamp WHERE nodeId = :nodeId")
    fun updateLastSynced(nodeId: NodeId, timestamp: Long)

    @Query("UPDATE peer_topic SET isSubscribed = :subscribed WHERE nodeId = :nodeId")
    fun updateSubscription(nodeId: NodeId, subscribed: Boolean)

    @Query("UPDATE peer_topic SET addresses = :addresses WHERE nodeId = :nodeId")
    fun updateAddresses(nodeId: NodeId, addresses: List<String>)

    @Delete
    fun delete(peerTopic: PeerTopic)

    @Query("DELETE FROM peer_topic WHERE nodeId = :nodeId")
    fun deleteByNodeId(nodeId: NodeId)
}
