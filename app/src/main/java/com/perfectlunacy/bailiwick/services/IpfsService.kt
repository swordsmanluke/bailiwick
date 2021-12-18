package com.perfectlunacy.bailiwick.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner
import com.perfectlunacy.bailiwick.workers.runners.downloaders.FeedDownloader
import com.perfectlunacy.bailiwick.workers.runners.downloaders.FileDownloader
import com.perfectlunacy.bailiwick.workers.runners.downloaders.IdentityDownloader
import com.perfectlunacy.bailiwick.workers.runners.downloaders.PostDownloader
import java.time.Duration

class IpfsService: Service() {
    companion object {
        const val TAG = "IpfsService"
        const val CHANNEL = "ipfs-service"

        @JvmStatic
        fun start(context: Context) {
            Log.i(TAG, "Starting up IpfsService")
            val intent = Intent(context, IpfsService::class.java)
            intent.putExtra("run", true)
            ContextCompat.startForegroundService(context, intent)
            Log.i(TAG, "Started(?) IpfsService")
        }
    }
    private var networkCallback: NetworkCallback? = null
    private var db: BailiwickDatabase? = null
    private var ipfs: IPFS? = null
    private var thread: Thread? = null

    // Not a bound service
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.i(TAG, "IpfsService onStartCommand called")

            db = Bailiwick.getInstance().db
            ipfs = Bailiwick.getInstance().ipfs

            if (intent!!.getBooleanExtra("run", false)) {
                startup()
            } else {
                shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start IpfsService", e)
        }

        return START_NOT_STICKY
    }

    private fun shutdown() {
        try {
            stopForeground(true)
        } finally {
            stopSelf()
        }
    }

    private fun startup() {
        // Launch a thread that runs the download/upload runners on a schedule
        if(thread == null) {
            thread = Thread {
                Log.i(TAG, "Starting thread: ${Thread.currentThread().id}")
                val napDuration = Duration.ofMinutes(5).toMillis()

                // Assert that our var's are initialized - then use them.
                val db = db!!
                val ipfs = ipfs!!

                var timeOfLastDownload = 0L

                while (true) {
                    if (needToPublish(db)) {
                        Log.i(TAG, "${Thread.currentThread().id} Starting publish")
                        PublishRunner(applicationContext, db, ipfs).run()
                    }
                    if (needToDownload(timeOfLastDownload)) {
                        Log.i(TAG, "${Thread.currentThread().id} Starting download")

                        DownloadRunner(applicationContext.filesDir.toPath(), db, ipfs, feedDownloader(db, ipfs)).run()
                        timeOfLastDownload = System.currentTimeMillis()
                    }
                    Thread.sleep(napDuration)
                }

            }
            thread?.start()
        }

        val notification = buildNotification()
        startForeground(TAG.hashCode(), notification)
        Log.i(TAG, "Started in foreground")
    }

    private fun feedDownloader(
        db: BailiwickDatabase,
        ipfs: IPFS
    ): FeedDownloader {
        // Downloader classes which retrieve and store specific IPFS objects
        val fileDlr = FileDownloader(
            applicationContext.filesDir.toPath(),
            db.postFileDao(),
            ipfs
        )
        val postDlr = PostDownloader(db.postDao(), ipfs, fileDlr)
        val identDlr = IdentityDownloader(db.identityDao(), ipfs)
        val feedDlr = FeedDownloader(db.keyDao(), identDlr, postDlr, ipfs)
        return feedDlr
    }

    private fun needToDownload(timeOfLastDownload: Long): Boolean {
        val elapsed = System.currentTimeMillis() - timeOfLastDownload
        return elapsed > Duration.ofMinutes(20).toMillis()
    }

    private fun needToPublish(db: BailiwickDatabase): Boolean {
        return db.postDao().inNeedOfSync().isNotEmpty() ||
               db.actionDao().inNeedOfSync().isNotEmpty()
    }

    private fun buildNotification(): Notification {
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(applicationContext, CHANNEL)
        val stopIntent = Intent(applicationContext, IpfsService::class.java)
        stopIntent.putExtra("run", false)
        val cancelIntent: PendingIntent = PendingIntent.getService(
            applicationContext,
            System.currentTimeMillis().toInt(), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            "cancel",
            cancelIntent
        ).build()

        builder.setSmallIcon(R.drawable.img_bailiwick_icon)
        builder.setUsesChronometer(true)
        builder.setOnlyAlertOnce(true)

        builder.addAction(cancelAction)
        builder.setContentText("Bailiwick is running...")
        builder.setSubText(
            "...I think"
        )
        builder.color =
            ContextCompat.getColor(applicationContext, R.color.cardview_light_background)
        builder.setCategory(Notification.CATEGORY_SERVICE)

        val chan = NotificationChannel(
            CHANNEL,
            "ipfs channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(true)
            lightColor = Color.YELLOW
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
        }
        chan.setDescription("Dunno")

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            chan
        )

        val notification = builder.build()
        Log.i(TAG, "Built notification")
        return notification
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unRegisterNetworkCallback()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to deregister network callback", throwable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            registerNetworkCallback()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to register network callback", throwable)
        }
    }

    private fun unRegisterNetworkCallback() {
        try {
            val connectivityManager =
                applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }

        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to deregister network callback", throwable)
        }
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager =
                applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Connecting to IPFS")
                    ipfs?.bootstrap(applicationContext)
                }

                override fun onLost(network: Network) {}
            }
            networkCallback?.let { connectivityManager.registerDefaultNetworkCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG,"Failed to register network callback", e)
        }
    }
}