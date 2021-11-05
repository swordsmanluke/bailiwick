package com.perfectlunacy.bailiwick.fragments

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentSplashBinding
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import threads.lite.IPFS
import java.util.*

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

        showSplashScreen()

        return binding.root
    }

    @DelicateCoroutinesApi
    private fun showSplashScreen() {
        GlobalScope.launch {
//            bwModel.bootstrap(requireContext())
//            Log.i(TAG, "Connected to IPFS network")
            // Now that we're connected to IPFS, check for an account and go!
            Thread.sleep(100)
            val nav = requireView().findNavController()
            if (bwModel.network.accountExists()) {
                Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_splashFragment_to_contentFragment) }
            } else {
                Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_splashFragment_to_firstRunFragment) }
            }
        }
    }

    companion object {
        private const val TAG = "SplashFragment"
    }
}