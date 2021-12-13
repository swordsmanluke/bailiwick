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
            val request = OneTimeWorkRequestBuilder<IpfsPublishWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork("ipfsupload", ExistingWorkPolicy.APPEND, request)
            return request.id
        }

        @JvmStatic
        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<IpfsPublishWorker>(Duration.ofMinutes(20))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ipfsupload-periodic", ExistingPeriodicWorkPolicy.REPLACE, request)
        }
    }

    override fun doWork(): Result {
        val ipfs = Bailiwick.getInstance().ipfs
        val db = Bailiwick.getInstance().db

        val postsToPublish = db.postDao().inNeedOfSync().isNotEmpty()
        try {
            if(postsToPublish) {
                Log.i(TAG, "Publishing new posts")
                PublishRunner(context, db, ipfs).run()
            } else {
                Log.i(TAG, "Providing existing posts")
                PublishRunner(context, db, ipfs).refresh()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish updates", e)
            return Result.failure()
        }

        Log.i(TAG, "Successfully uploaded updates!")
        return Result.success()
    }
}