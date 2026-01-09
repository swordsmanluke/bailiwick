package com.perfectlunacy.bailiwick.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.BlobHash
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Utility for loading avatar images from the local blob cache.
 */
object AvatarLoader {
    /**
     * Load an avatar bitmap for an identity.
     * @param identity The identity whose avatar to load
     * @param cacheFilesDir The directory containing the blob cache
     * @return The avatar bitmap, or null if not available
     */
    @JvmStatic
    fun loadAvatar(identity: Identity, cacheFilesDir: Path): Bitmap? {
        return identity.profilePicHash?.let { hash ->
            loadBlob(hash, cacheFilesDir)
        }
    }

    /**
     * Load a blob as a bitmap.
     * @param hash The blob hash to load
     * @param cacheFilesDir The directory containing the blob cache
     * @return The decoded bitmap, or null if file doesn't exist or can't be decoded
     */
    @JvmStatic
    fun loadBlob(hash: BlobHash, cacheFilesDir: Path): Bitmap? {
        val avatarFile = Path(cacheFilesDir.pathString, "blobs", hash).toFile()
        if (!avatarFile.exists() || !avatarFile.isFile) {
            return null
        }

        return try {
            BufferedInputStream(FileInputStream(avatarFile)).use { file ->
                val picData = file.readBytes()
                BitmapFactory.decodeByteArray(picData, 0, picData.size)
            }
        } catch (e: Exception) {
            null
        }
    }
}
