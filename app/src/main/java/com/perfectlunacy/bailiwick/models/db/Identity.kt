package com.perfectlunacy.bailiwick.models.db

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

@Entity
data class Identity(
    var cid: ContentId?, var owner: PeerId, var name: String, var profilePicCid: ContentId?
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    fun avatar(cacheFilesDir: Path): Bitmap? {
        if(profilePicCid == null) { return null }

        val avatarFile = Path(cacheFilesDir.pathString, "bwcache", profilePicCid.toString()).toFile()
        if(!avatarFile.exists()) { return null }

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

    @Query("SELECT * FROM identity WHERE cid = :cid LIMIT 1")
    fun findByCid(cid: ContentId): Identity

    @Query("SELECT * FROM identity WHERE owner = :owner")
    fun identitiesFor(owner: PeerId): List<Identity>

    @Query("UPDATE identity SET cid = :cid WHERE id = :id")
    fun updateCid(id: Long, cid: ContentId?)

    @Insert
    fun insert(identity: Identity): Long
}