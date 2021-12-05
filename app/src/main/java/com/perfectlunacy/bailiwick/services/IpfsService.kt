package com.perfectlunacy.bailiwick.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS

class IpfsService(val db: BailiwickDatabase, val ipfs: IPFS): Service() {
    companion object {
        const val TAG = "IpfsService"
    }
    private var networkCallback: NetworkCallback? = null

    // Not a bound service
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "IpfsService onStartCommand called")
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            applicationContext, TAG
        )

        builder.setSmallIcon(R.drawable.img_bailiwick_icon)
        builder.setUsesChronometer(true)
        builder.setOnlyAlertOnce(true)
        builder.setContentText("Bailiwick is running...")
        builder.setSubText(
            "...I think"
        )
        builder.color = ContextCompat.getColor(applicationContext, R.color.cardview_light_background)
        builder.setCategory(Notification.CATEGORY_SERVICE)

        val notification = builder.build()
        Log.i(TAG, "Built notification")

        startForeground(TAG.hashCode(), notification)
        Log.i(TAG, "Started in foreground")

        return START_NOT_STICKY
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
                    ipfs.bootstrap(applicationContext)
                }

                override fun onLost(network: Network) {}
            }
            networkCallback?.let { connectivityManager.registerDefaultNetworkCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG,"Failed to register network callback", e)
        }
    }
}