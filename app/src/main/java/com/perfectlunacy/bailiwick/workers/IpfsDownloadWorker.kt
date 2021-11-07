package com.perfectlunacy.bailiwick.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel

class IpfsDownloadWorker(context: Context, workerParameters: WorkerParameters): Worker(context, workerParameters) {
    companion object {
        const val TAG = "IpfsDownloadWorker"

        @JvmStatic
        fun enqueue(context: Context, cid: ContentId) {
            val data = Data.Builder().putAll(mapOf(
                Pair("cid", cid)
            )).build()

            val request = OneTimeWorkRequestBuilder<IpfsDownloadWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override fun doWork(): Result {
        val cid = inputData.keyValueMap["cid"] as ContentId
        val ipfs = BailiwickViewModel.ipfs()

        try {
            // Downloading in the IPFS store _also_ stores it in the cache.
            ipfs.getData(cid, 600)
        } catch(e: Exception) {
            Log.e(TAG, "Failed to download cid $cid. ${e.message}\n${e.stackTraceToString()}")
            return Result.failure()
        }

        return Result.success()
    }

}