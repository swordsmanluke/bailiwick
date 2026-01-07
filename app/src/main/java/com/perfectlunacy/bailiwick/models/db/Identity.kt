package com.perfectlunacy.bailiwick.models.db

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

@Entity
data class Identity(
    var blobHash: BlobHash?,       // Iroh blob hash (was: cid)
    var owner: NodeId,             // Iroh node ID (was: PeerId)
    var name: String,
    var profilePicHash: BlobHash?  // Profile pic blob hash (was: profilePicCid)
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    fun avatar(cacheFilesDir: Path): Bitmap? {
        if (profilePicHash == null) { return null }

        val avatarFile = Path(cacheFilesDir.pathString, "blobs", profilePicHash.toString()).toFile()
        if (!avatarFile.exists() || !avatarFile.isFile) { return null }

        BufferedInputStream(FileInputStream(avatarFile)).use { file ->
            val picData = file.readBytes()
            return BitmapFactory.decodeByteArray(picData, 0, picData.size)
        }
    }
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

    @Query("UPDATE identity SET blobHash = :hash WHERE id = :id")
    fun updateHash(id: Long, hash: BlobHash?)

    @Insert
    fun insert(identity: Identity): Long

    @Update
    fun update(identity: Identity)
}
