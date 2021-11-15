package com.perfectlunacy.bailiwick.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import java.time.Duration
import java.util.*

class IpfsDownloadWorker(context: Context, workerParameters: WorkerParameters): Worker(context, workerParameters) {
    companion object {
        const val TAG = "IpfsDownloadWorker"
        @JvmStatic
        fun enqueue(context: Context): UUID {
            val request = OneTimeWorkRequestBuilder<IpfsDownloadWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork("ipfsdownload", ExistingWorkPolicy.REPLACE, request)

            return request.id
        }
    }

    override fun doWork(): Result {
        while(true) {

            try {
                val bw = Bailiwick.getInstance()
                val ipfs = bw.ipfs
                DownloadRunner(
                    applicationContext,
                    getBailiwickDb(applicationContext),
                    ipfs
                ).run()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to Download from IPFS", e)
            }

            Log.i(TAG, "Downloaded successfully from IPFS")
            Thread.sleep(1000 * 60L * 5) // 5 minutes
        }

        return Result.success()
    }
}