package com.perfectlunacy.bailiwick.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.workers.ContentDownloader
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import kotlinx.coroutines.*

/**
 * Background service for syncing with Iroh network.
 * Replaces the old IpfsService.
 */
class IrohService : Service() {
    companion object {
        private const val TAG = "IrohService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bailiwick_sync"
        private val SYNC_INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes

        fun start(context: Context) {
            val intent = Intent(context, IrohService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, IrohService::class.java)
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting IrohService")
        startForeground(NOTIFICATION_ID, createNotification("Syncing..."))
        startSyncLoop()
        return START_STICKY
    }

    private fun startSyncLoop() {
        syncJob = scope.launch {
            while (isActive) {
                try {
                    sync()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun sync() {
        val bw = try {
            Bailiwick.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Bailiwick not initialized, skipping sync")
            return
        }

        val iroh = bw.iroh
        val db = bw.db

        if (!iroh.isConnected()) {
            Log.i(TAG, "Not connected, skipping sync")
            updateNotification("Waiting for connection...")
            return
        }

        updateNotification("Syncing...")

        // Publish local changes
        val publisher = ContentPublisher(iroh, db)
        // TODO: Get cipher from keyring
        // publisher.publishPending(cipher)

        // Download from peers
        val downloader = ContentDownloader(iroh, db, bw.cacheDir)
        downloader.syncAll()

        val peerCount = db.peerDocDao().subscribedPeers().size
        updateNotification("Connected to $peerCount peers")

        Log.i(TAG, "Sync completed")
    }

    override fun onDestroy() {
        Log.i(TAG, "Stopping IrohService")
        syncJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bailiwick Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization with Bailiwick network"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BailiwickActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bailiwick")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
