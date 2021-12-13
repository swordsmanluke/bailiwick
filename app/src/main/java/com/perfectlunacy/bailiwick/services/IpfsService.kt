package com.perfectlunacy.bailiwick.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS

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
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, CHANNEL)
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

        val chan = NotificationChannel(CHANNEL, "ipfs channel", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableLights(true)
            lightColor = Color.YELLOW
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
        }
        chan.setDescription("Dunno")

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)

        val notification = builder.build()
        Log.i(TAG, "Built notification")

        startForeground(TAG.hashCode(), notification)
        Log.i(TAG, "Started in foreground")
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