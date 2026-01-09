package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R

/**
 * A simple [Fragment] subclass.
 * Use the [ConnectFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConnectFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_connect, container, false)

        view.findViewById<Button>(R.id.btn_conn_request).setOnClickListener {
            requireView().findNavController().navigate(R.id.action_connectFragment_to_subscribeFragment)
        }

        view.findViewById<Button>(R.id.btn_conn_accept).setOnClickListener {
            requireView().findNavController().navigate(R.id.action_connectFragment_to_acceptSubscriptionFragment)
        }

        return view
    }

}