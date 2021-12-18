package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentSplashBinding
import com.perfectlunacy.bailiwick.services.IpfsService
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                bwModel.ipfs.bootstrap(requireContext())
                showSplashScreen()
            }

            IpfsService.start(requireContext())
        }
    }

    private suspend fun showSplashScreen() {
        Log.i(TAG, "Connected to IPFS network")

        val nav = requireView().findNavController()
        if (bwModel.network.accountExists()) {
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_splashFragment_to_contentFragment) }
        } else {
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_splashFragment_to_firstRunFragment) }
        }
    }

    companion object {
        private const val TAG = "SplashFragment"
    }
}