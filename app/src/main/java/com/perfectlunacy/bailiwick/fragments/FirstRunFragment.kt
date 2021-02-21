package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentFirstRunBinding

/**
 * A simple [Fragment] subclass.
 * Use the [FirstRunFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FirstRunFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val binding = DataBindingUtil.inflate<FragmentFirstRunBinding>(inflater, R.layout.fragment_first_run, container, false)

        // Bind button handlers
        binding.btnSignUp.setOnClickListener { view ->
            view.findNavController().navigate(R.id.action_firstRunFragment_to_newUserFragment)
        }

        binding.btnUseKey.setOnClickListener {
            Toast.makeText(this.context, "Not yet built!", Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment FirstRunFragment.
         */
        @JvmStatic
        fun newInstance() = FirstRunFragment()
    }
}