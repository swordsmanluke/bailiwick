package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Caches downloaded user manifests for offline access and quick lookup.
 */
@Entity(tableName = "manifest_cache")
data class ManifestCache(
    @PrimaryKey
    val nodeId: NodeId,

    /** Hash of the cached manifest blob */
    val manifestHash: BlobHash,

    /** Version number of the cached manifest */
    val version: Long,

    /** When this manifest was downloaded */
    val downloadedAt: Long,

    /** Cached decrypted UserManifest JSON (for quick access) */
    val userManifestJson: String
)

@Dao
interface ManifestCacheDao {
    @Query("SELECT * FROM manifest_cache WHERE nodeId = :nodeId")
    fun findByNodeId(nodeId: NodeId): ManifestCache?

    @Query("SELECT * FROM manifest_cache")
    fun all(): List<ManifestCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(cache: ManifestCache)

    @Query("DELETE FROM manifest_cache WHERE nodeId = :nodeId")
    fun deleteByNodeId(nodeId: NodeId)

    @Query("DELETE FROM manifest_cache WHERE downloadedAt < :olderThan")
    fun deleteOlderThan(olderThan: Long)

    @Query("SELECT version FROM manifest_cache WHERE nodeId = :nodeId")
    fun getVersion(nodeId: NodeId): Long?
}
