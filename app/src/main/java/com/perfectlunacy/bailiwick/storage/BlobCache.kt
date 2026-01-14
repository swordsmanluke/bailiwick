package com.perfectlunacy.bailiwick.storage

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Local cache for blob storage.
 *
 * Provides a simple file-based cache for storing and retrieving blobs by their hash.
 * All blobs are stored as files named by their hash in the cache directory.
 */
class BlobCache(private val cacheDir: File) {
    companion object {
        private const val TAG = "BlobCache"
    }

    /**
     * Stores data in the cache with the given hash as the filename.
     * @return true if stored successfully, false otherwise.
     */
    fun store(hash: BlobHash, data: ByteArray): Boolean {
        val cacheFile = File(cacheDir, hash)
        return try {
            cacheFile.writeBytes(data)
            Log.d(TAG, "Stored blob $hash (${data.size} bytes)")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write blob $hash to cache: ${e.message}")
            false
        }
    }

    /**
     * Retrieves data from the cache by hash.
     * @return The blob data, or null if not found or read error.
     */
    fun get(hash: BlobHash): ByteArray? {
        val cacheFile = File(cacheDir, hash)
        if (!cacheFile.exists()) {
            return null
        }
        return try {
            cacheFile.readBytes()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read blob $hash from cache: ${e.message}")
            null
        }
    }

    /**
     * Checks if a blob exists in the cache with valid content.
     * Returns false for missing files or 0-byte files (which indicate failed downloads).
     */
    fun exists(hash: BlobHash): Boolean {
        val file = File(cacheDir, hash)
        return file.exists() && file.length() > 0
    }

    /**
     * Deletes a blob from the cache.
     * @return true if deleted or didn't exist, false on error.
     */
    fun delete(hash: BlobHash): Boolean {
        val cacheFile = File(cacheDir, hash)
        if (!cacheFile.exists()) {
            return true
        }
        return try {
            cacheFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete blob $hash from cache: ${e.message}")
            false
        }
    }
}
