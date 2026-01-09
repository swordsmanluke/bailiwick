package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId

@Entity
data class Identity(
    var blobHash: BlobHash?,       // Iroh blob hash (was: cid)
    var owner: NodeId,             // Iroh node ID (was: PeerId)
    var name: String,
    var profilePicHash: BlobHash?  // Profile pic blob hash (was: profilePicCid)
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identity")
    fun all(): List<Identity>

    @Query("SELECT * FROM identity WHERE id = :id LIMIT 1")
    fun find(id: Long): Identity

    @Query("SELECT * FROM identity WHERE blobHash = :hash LIMIT 1")
    fun findByHash(hash: BlobHash): Identity?

    @Query("SELECT * FROM identity WHERE owner = :owner")
    fun identitiesFor(owner: NodeId): List<Identity>

    @Query("SELECT * FROM identity WHERE owner = :owner LIMIT 1")
    fun findByOwner(owner: NodeId): Identity?

    @Query("UPDATE identity SET blobHash = :hash WHERE id = :id")
    fun updateHash(id: Long, hash: BlobHash?)

    @Insert
    fun insert(identity: Identity): Long

    @Update
    fun update(identity: Identity)
}
