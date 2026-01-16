package com.perfectlunacy.bailiwick.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Generates robot avatars using robohash.org API.
 * Creates unique, fun robot face avatars as an alternative to photo selection.
 */
object RobotAvatarGenerator {
    private const val ROBOHASH_BASE_URL = "https://robohash.org"
    private const val AVATAR_SIZE = 512

    /**
     * Generate a random robot avatar.
     * @return Bitmap of the generated avatar
     * @throws IOException if network request fails
     */
    suspend fun generate(): Bitmap {
        val seed = UUID.randomUUID().toString()
        return generateWithSeed(seed)
    }

    /**
     * Generate a robot avatar with a specific seed.
     * Same seed will always produce the same robot.
     * @param seed The seed string for avatar generation
     * @return Bitmap of the generated avatar
     * @throws IOException if network request fails
     */
    suspend fun generateWithSeed(seed: String): Bitmap {
        val url = "$ROBOHASH_BASE_URL/$seed.png?set=set1&size=${AVATAR_SIZE}x$AVATAR_SIZE"

        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Failed to fetch avatar: HTTP ${connection.responseCode}")
                }

                connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input)
                        ?: throw IOException("Failed to decode avatar image")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
