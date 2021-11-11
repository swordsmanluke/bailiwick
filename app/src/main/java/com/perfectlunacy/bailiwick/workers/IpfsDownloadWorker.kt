package com.perfectlunacy.bailiwick.workers

import android.content.Context
import androidx.work.*
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import java.time.Duration
import java.util.*

class IpfsDownloadWorker(context: Context, workerParameters: WorkerParameters): Worker(context, workerParameters) {
    companion object {
        @JvmStatic
        fun enqueue(context: Context): UUID {
            val request = PeriodicWorkRequestBuilder<IpfsDownloadWorker>(Duration.ofMinutes(5))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ipfsdownload", ExistingPeriodicWorkPolicy.KEEP, request)

            return request.id
        }
    }

    override fun doWork(): Result {
        return try {
            val bw = Bailiwick.getInstance()
            val ipfs = bw.ipfs
            DownloadRunner(
                applicationContext,
                getBailiwickDb(applicationContext),
                ipfs
            ).run()
            Result.success()
        }catch (e: Exception){
            Result.failure()
        }
    }
}