package com.perfectlunacy.bailiwick.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.workers.runners.UploadRunner
import java.time.Duration

class IpfsUploadWorker(val context: Context, workerParameters: WorkerParameters): Worker(context, workerParameters) {
    companion object {
        const val TAG = "IpfsUploadWorker"

        @JvmStatic
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<IpfsUploadWorker>(Duration.ofMinutes(2)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ipfs-uploader", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override fun doWork(): Result {
        val ipfs = Bailiwick.getInstance().ipfs
        val db = Bailiwick.getInstance().db

        try {
            UploadRunner(context, db, ipfs).run()
        } catch(e: Exception) {
            Log.e(TAG, "Failed to publish updates", e)
            return Result.failure()
        }

        Log.i(TAG, "Succesfully uploaded updates!")
        return Result.success()
    }

}