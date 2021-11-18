package com.perfectlunacy.bailiwick.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import com.perfectlunacy.bailiwick.workers.runners.downloaders.FeedDownloader
import com.perfectlunacy.bailiwick.workers.runners.downloaders.FileDownloader
import com.perfectlunacy.bailiwick.workers.runners.downloaders.IdentityDownloader
import com.perfectlunacy.bailiwick.workers.runners.downloaders.PostDownloader
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
                val db = bw.db
                val ipfs = bw.ipfs

                // Downloader classes which retrieve and store specific IPFS objects
                val fileDlr = FileDownloader(applicationContext.filesDir.toPath(), db.postFileDao(), ipfs)
                val postDlr = PostDownloader(db.postDao(), ipfs, fileDlr)
                val identDlr = IdentityDownloader(db.identityDao(), ipfs)
                val feedDlr = FeedDownloader(db.keyDao(), identDlr, postDlr, ipfs)

                DownloadRunner(
                    applicationContext.filesDir.toPath(),
                    db,
                    ipfs,
                    feedDlr
                ).run()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to Download from IPFS", e)
            }

            Log.i(TAG, "Downloaded successfully from IPFS")
            val minutesBetweenRefresh = 15
            Thread.sleep(1000L * 60 * minutesBetweenRefresh)
        }

        return Result.success()
    }
}