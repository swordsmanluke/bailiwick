package com.perfectlunacy.bailiwick.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner
import java.time.Duration
import java.util.*

class IpfsPublishWorker(val context: Context, workerParameters: WorkerParameters): Worker(context, workerParameters) {
    companion object {
        const val TAG = "IpfsPublishWorker"

        @JvmStatic
        fun enqueue(context: Context): UUID {
            val data = workDataOf("refresh" to false)
            val request = OneTimeWorkRequestBuilder<IpfsPublishWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork("ipfs-upload", ExistingWorkPolicy.APPEND, request)
            return request.id
        }

        @JvmStatic
        fun enqueuePeriodicRefresh(context: Context) {
            val data = workDataOf("refresh" to true)
            val request = PeriodicWorkRequestBuilder<IpfsPublishWorker>(Duration.ofMinutes(20))
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ipfs-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override fun doWork(): Result {
        val ipfs = Bailiwick.getInstance().ipfs
        val db = Bailiwick.getInstance().db
        val refresh = inputData.getBoolean("refresh", false)
        try {
            if(refresh) {
                PublishRunner(context, db, ipfs).refresh()
            } else {
                PublishRunner(context, db, ipfs).run()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish updates", e)
        }

        Log.i(TAG, "Successfully uploaded updates!")
        Thread.sleep(1000 * 60L) // 1 minute
        return Result.success()
    }
}