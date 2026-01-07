package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Maps a peer's NodeId to their Iroh Doc namespace.
 * This replaces IPNS - we subscribe to their Doc to get updates.
 */
@Entity(indices = [Index(value = ["nodeId"], unique = true)])
data class PeerDoc(
    @PrimaryKey val nodeId: NodeId,
    val docNamespaceId: DocNamespaceId,
    val displayName: String?,
    val lastSyncedAt: Long = 0,
    val isSubscribed: Boolean = true
)

@Dao
interface PeerDocDao {
    @Query("SELECT * FROM peerdoc WHERE isSubscribed = 1")
    fun subscribedPeers(): List<PeerDoc>

    @Query("SELECT * FROM peerdoc WHERE nodeId = :nodeId")
    fun findByNodeId(nodeId: NodeId): PeerDoc?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(peerDoc: PeerDoc)

    @Query("UPDATE peerdoc SET lastSyncedAt = :timestamp WHERE nodeId = :nodeId")
    fun updateLastSynced(nodeId: NodeId, timestamp: Long)

    @Delete
    fun delete(peerDoc: PeerDoc)

    @Query("SELECT * FROM peerdoc")
    fun all(): List<PeerDoc>
}
