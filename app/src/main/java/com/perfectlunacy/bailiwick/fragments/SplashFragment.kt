package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentSplashBinding
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import java.util.*

/**
 * Shows the branded splash screen and determines if we already have an account loaded or not.
 */
class SplashFragment : BailiwickFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DataBindingUtil.inflate<FragmentSplashBinding>(inflater, R.layout.fragment_splash, container, false)

        showSplashScreen()

        return binding.root
    }

    private fun showSplashScreen() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val nav = requireView().findNavController()
                if (bwModel.name.isBlank()) {
                    nav.navigate(R.id.action_splashFragment_to_firstRunFragment)
                } else {
                    nav.navigate(R.id.action_splashFragment_to_contentFragment)
                }
            }
        }, SPLASH_DURATION_MS)
    }

    companion object {
        private const val SPLASH_DURATION_MS: Long = 1000
    }
}