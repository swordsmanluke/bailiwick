package com.perfectlunacy.bailiwick.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import androidx.work.WorkManager
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentSplashBinding
import com.perfectlunacy.bailiwick.services.IpfsService
import com.perfectlunacy.bailiwick.services.TestService
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.workers.IpfsDownloadWorker
import com.perfectlunacy.bailiwick.workers.IpfsPublishWorker
import kotlinx.coroutines.*

/**
 * Shows the branded splash screen and determines if we already have an account loaded or not.
 */
class SplashFragment : BailiwickFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DataBindingUtil.inflate<FragmentSplashBinding>(
            inflater,
            R.layout.fragment_splash,
            container,
            false
        )

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                showSplashScreen()
                launchIpfsJobs(bwModel.ipfs)
            }

            IpfsService.start(requireContext())
        }
    }

    private suspend fun showSplashScreen() {
        Log.i(TAG, "Connected to IPFS network")

        Thread.sleep(100)
        val nav = requireView().findNavController()
        if (bwModel.network.accountExists()) {
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_splashFragment_to_contentFragment) }
        } else {
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_splashFragment_to_firstRunFragment) }
        }
    }

    private fun CoroutineScope.launchIpfsJobs(ipfs: IPFS) =
        launch {
            withContext(Dispatchers.Default) {
                ipfs.bootstrap(requireContext())
                startUploadJob()
                startDownloadJob()
            }
        }

    private fun startUploadJob() {
        IpfsPublishWorker.enqueuePeriodicRefresh(requireContext())
    }

    private fun startDownloadJob() {
        val refreshId = IpfsDownloadWorker.enqueuePeriodicRefresh(requireContext())

        WorkManager.getInstance(requireContext()).getWorkInfoById(refreshId)
            .addListener(
                { // Runnable
                    bwModel.viewModelScope.launch {
                        withContext(Dispatchers.Default) { bwModel.refreshContent() }
                    }
                },
                { it.run() }  // Executable
            )
    }


    companion object {
        private const val TAG = "SplashFragment"
    }
}