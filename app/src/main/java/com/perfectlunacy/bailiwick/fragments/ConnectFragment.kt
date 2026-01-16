package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentConnectBinding

/**
 * Connection options fragment - entry point for connecting with other users.
 * Provides options to invite a friend (show QR) or scan an invitation.
 */
class ConnectFragment : BailiwickFragment() {

    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_connect,
            container,
            false
        )

        setupButtons()

        return binding.root
    }

    private fun setupButtons() {
        binding.btnConnRequest.setOnClickListener {
            it.findNavController().navigate(R.id.action_connectFragment_to_subscribeFragment)
        }

        binding.btnConnAccept.setOnClickListener {
            it.findNavController().navigate(R.id.action_connectFragment_to_acceptSubscriptionFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
