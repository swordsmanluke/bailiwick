package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.InitState
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentSplashBinding
import com.perfectlunacy.bailiwick.services.IrohService
import kotlinx.coroutines.launch

/**
 * Shows the branded splash screen while initialization completes.
 * Observes initialization state and navigates when ready.
 */
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_splash,
            container,
            false
        )
        return binding.root
    }

    private var hasNavigated = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as BailiwickActivity

        Log.d(TAG, "Starting initState collection")
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Coroutine started, entering repeatOnLifecycle")
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d(TAG, "repeatOnLifecycle block started (STARTED state reached)")
                activity.initState.collect { state ->
                    Log.d(TAG, "Received state: $state")
                    handleState(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called, hasNavigated=$hasNavigated")

        // Fallback: check current state in case we missed it during lifecycle transitions
        if (!hasNavigated) {
            val activity = requireActivity() as BailiwickActivity
            val currentState = activity.initState.value
            Log.d(TAG, "onResume checking current state: $currentState")
            if (currentState is InitState.Ready) {
                Log.i(TAG, "onResume found Ready state, handling it")
                handleState(currentState)
            }
        }
    }

    private fun handleState(state: InitState) {
        if (hasNavigated) {
            Log.d(TAG, "Already navigated, ignoring state: $state")
            return
        }

        when (state) {
            is InitState.Loading -> {
                Log.d(TAG, "Waiting for initialization...")
                binding.txtStatus.text = getString(R.string.loading)
                binding.layoutError.visibility = View.GONE
            }

            is InitState.Ready -> {
                Log.i(TAG, "Initialization complete, navigating...")
                hasNavigated = true
                onInitializationComplete(state.accountExists)
            }

            is InitState.Error -> {
                Log.e(TAG, "Initialization failed: ${state.message}", state.cause)
                showError(state.message)
            }
        }
    }

    private fun onInitializationComplete(accountExists: Boolean) {
        // Start background sync service
        IrohService.start(requireContext())

        // Navigate based on whether account exists
        val navController = findNavController()
        if (accountExists) {
            navController.navigate(R.id.action_splashFragment_to_contentFragment)
        } else {
            navController.navigate(R.id.action_splashFragment_to_firstRunFragment)
        }
    }

    private fun showError(message: String) {
        binding.txtStatus.text = getString(R.string.error)
        binding.layoutError.visibility = View.VISIBLE
        binding.txtError.text = message
        binding.btnRetry.setOnClickListener {
            // Restart the activity to retry initialization
            requireActivity().recreate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SplashFragment"
    }
}
