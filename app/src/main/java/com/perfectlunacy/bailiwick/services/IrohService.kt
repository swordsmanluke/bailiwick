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
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl
import com.perfectlunacy.bailiwick.workers.ContentDownloader
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import kotlinx.coroutines.*
import javax.crypto.spec.SecretKeySpec

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
        // Cancel existing job if running to prevent duplicate sync loops
        syncJob?.let { existingJob ->
            if (existingJob.isActive) {
                Log.w(TAG, "Sync loop already running, cancelling and restarting")
                existingJob.cancel()
            }
        }

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

        // Publish local changes including actions
        val publisher = ContentPublisher(iroh, db)
        try {
            // Publish identity so others can see who we are
            val myIdentity = db.identityDao().findByOwner(iroh.nodeId())
            if (myIdentity != null) {
                try {
                    publisher.publishIdentity(myIdentity)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not publish identity: ${e.message}")
                }
            }

            // Publish actions first (keys for new peers)
            publisher.publishActions()

            // Get cipher for the "everyone" circle to publish posts/feeds
            val everyoneCircle = db.circleDao().all().find { it.name == BailiwickNetworkImpl.EVERYONE_CIRCLE }
            if (everyoneCircle != null) {
                try {
                    val filesDir = applicationContext.filesDir.toPath()
                    val rsaCipher = RsaWithAesEncryptor(bw.keyring.privateKey, bw.keyring.publicKey)
                    val keyBytes = Keyring.keyForCircle(db.keyDao(), filesDir, everyoneCircle.id.toInt(), rsaCipher)
                    val secretKey = SecretKeySpec(keyBytes, "AES")
                    val cipher = AESEncryptor(secretKey)

                    publisher.publishPending(cipher)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get circle cipher for publishing: ${e.message}")
                }
            } else {
                Log.w(TAG, "No 'everyone' circle found, skipping post publishing")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish: ${e.message}")
        }

        // Log my doc keys for debugging
        try {
            val myDocKeys = iroh.getMyDoc().keys()
            Log.d(TAG, "My doc has ${myDocKeys.size} keys: ${myDocKeys.take(10)}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get my doc keys: ${e.message}")
        }

        // Download from peers
        val downloader = ContentDownloader(iroh, db, bw.cacheDir)
        downloader.syncAll()

        // Process any downloaded actions (stores keys, etc.)
        try {
            downloader.processActions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process actions: ${e.message}")
        }

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
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BailiwickActivity::class.java),
            flags
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
