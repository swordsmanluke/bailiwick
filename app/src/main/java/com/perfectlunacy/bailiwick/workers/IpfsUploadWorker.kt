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
        fun enqueue(context: Context, refresh: Boolean) {
            val data = workDataOf("refresh" to refresh)
            val request = OneTimeWorkRequestBuilder<IpfsUploadWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork("ipfs-uploader", ExistingWorkPolicy.APPEND, request)
        }
    }

    override fun doWork(): Result {
        val ipfs = Bailiwick.getInstance().ipfs
        val db = Bailiwick.getInstance().db
        val refresh = inputData.getBoolean("refresh", false)
        try {
            if(refresh) {
                UploadRunner(context, db, ipfs).refresh()
            } else {
                UploadRunner(context, db, ipfs).run()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish updates", e)
        }

        Log.i(TAG, "Successfully uploaded updates!")
        Thread.sleep(1000 * 60L) // 1 minute
        return Result.success()
    }
}