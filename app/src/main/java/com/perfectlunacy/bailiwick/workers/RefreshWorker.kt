package com.perfectlunacy.bailiwick.workers

import android.content.Context
import androidx.work.*
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.workers.runners.RefreshRunner
import java.time.Duration
import java.util.*

class RefreshWorker(context: Context, workerParameters: WorkerParameters): Worker(context, workerParameters) {
    companion object {
        @JvmStatic
        fun enqueue(context: Context): UUID {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(Duration.ofMinutes(15))
                .build()

            WorkManager.getInstance(context).enqueue(request)

            return request.id
        }
    }

    override fun doWork(): Result {
        return try {
            val bailiwick = BailiwickViewModel.bailiwick()
            RefreshRunner(
                applicationContext,
                bailiwick.peers,
                bailiwick.keyring,
                bailiwick.cache,
                bailiwick.ipnsDao,
                bailiwick.ipfs
            ).run()
            Result.success()
        }catch (e: Exception){
            Result.failure()
        }
    }
}