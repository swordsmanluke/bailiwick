package com.perfectlunacy.bailiwick.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.R
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper for showing batched notifications for new posts.
 *
 * Posts are buffered for a short delay before showing a single summary notification.
 * This prevents notification spam when syncing multiple posts at once.
 */
object NotificationHelper {
    private const val TAG = "NotificationHelper"

    // Notification channel for new posts
    const val CHANNEL_ID_NEW_POSTS = "bailiwick_new_posts"
    private const val NOTIFICATION_ID_NEW_POSTS = 2001

    // Buffer delay before showing notification (ms)
    private const val BUFFER_DELAY_MS = 3000L

    // Track pending posts by author
    private val pendingPosts = ConcurrentHashMap<String, MutableList<PendingPost>>()
    private var bufferJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Track if app is in foreground
    @Volatile
    var isAppInForeground = false

    /**
     * Data class to hold pending post info.
     */
    data class PendingPost(
        val postHash: String,
        val authorName: String,
        val authorNodeId: String,
        val timestamp: Long
    )

    /**
     * Create notification channel for new posts.
     * Must be called during app initialization (Android 8.0+).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_new_posts)
            val descriptionText = context.getString(R.string.notification_channel_new_posts_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(CHANNEL_ID_NEW_POSTS, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "Created notification channel: $CHANNEL_ID_NEW_POSTS")
        }
    }

    /**
     * Called when a new post is downloaded.
     * Buffers the post and schedules a batched notification.
     */
    fun onNewPost(
        context: Context,
        postHash: String,
        authorName: String,
        authorNodeId: String
    ) {
        // Don't notify if app is in foreground
        if (isAppInForeground) {
            Log.d(TAG, "App in foreground, skipping notification for post $postHash")
            return
        }

        // Add to pending posts
        val post = PendingPost(
            postHash = postHash,
            authorName = authorName,
            authorNodeId = authorNodeId,
            timestamp = System.currentTimeMillis()
        )

        pendingPosts.getOrPut(authorNodeId) { mutableListOf() }.add(post)

        Log.d(TAG, "Buffered post $postHash from $authorName")

        // Schedule notification after buffer delay
        scheduleNotification(context)
    }

    /**
     * Schedule a notification to be shown after buffer delay.
     * Cancels any existing scheduled notification.
     */
    private fun scheduleNotification(context: Context) {
        bufferJob?.cancel()
        bufferJob = scope.launch {
            delay(BUFFER_DELAY_MS)
            showNotification(context)
        }
    }

    /**
     * Show batched notification for all pending posts.
     */
    private fun showNotification(context: Context) {
        if (pendingPosts.isEmpty()) return
        if (isAppInForeground) {
            pendingPosts.clear()
            return
        }

        // Calculate totals
        val totalPosts = pendingPosts.values.sumOf { it.size }
        val authorCount = pendingPosts.size
        val authorNames = pendingPosts.values
            .flatMap { posts -> posts.map { it.authorName } }
            .distinct()
            .take(3)

        // Build notification text
        val title = context.getString(R.string.notification_new_posts_title)
        val text = when {
            totalPosts == 1 && authorCount == 1 -> {
                context.getString(R.string.notification_new_post_single, authorNames.first())
            }
            authorCount == 1 -> {
                context.getString(
                    R.string.notification_new_posts_single_author,
                    totalPosts,
                    authorNames.first()
                )
            }
            else -> {
                context.getString(
                    R.string.notification_new_posts_multiple_authors,
                    totalPosts,
                    authorCount
                )
            }
        }

        // Create intent to open app
        val intent = Intent(context, BailiwickActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEW_POSTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("new_posts")
            .build()

        // Show notification
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NEW_POSTS, notification)
            Log.i(TAG, "Showed notification: $totalPosts posts from $authorCount authors")
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification permission", e)
        }

        // Clear pending posts
        pendingPosts.clear()
    }

    /**
     * Cancel any pending or shown notifications.
     */
    fun cancelAll(context: Context) {
        bufferJob?.cancel()
        pendingPosts.clear()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_NEW_POSTS)
    }

    /**
     * Check if POST_NOTIFICATIONS permission is granted (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true
        }
    }
}
