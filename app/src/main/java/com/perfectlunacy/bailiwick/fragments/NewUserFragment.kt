package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentNewUserBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass.
 * Use the [NewUserFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class NewUserFragment : BailiwickFragment() {
    @DelicateCoroutinesApi
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val binding = DataBindingUtil.inflate<FragmentNewUserBinding>(inflater, R.layout.fragment_new_user, container, false)

        binding.newUserName.doOnTextChanged { text, start, before, count ->
            val goIsEnabled = binding.newUserName.text.toString().length > 3 &&
                    binding.confirmPassword.text.toString() == binding.newPassword.text.toString() &&
                    binding.newPassword.text.toString().length > 8

            Log.i(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")

            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        // Make sure new and confirmed password fields are the same
        binding.newPassword.doOnTextChanged { text, start, before, count ->
            val goIsEnabled = binding.newUserName.text.toString().length > 3 &&
                    binding.confirmPassword.text.toString() == binding.newPassword.text.toString() &&
                    binding.newPassword.text.toString().length > 8

            Log.i(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")
            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        binding.confirmPassword.doOnTextChanged { text, start, before, count ->
            val goIsEnabled = binding.newUserName.text.toString().length > 3 &&
                    binding.confirmPassword.text.toString() == binding.newPassword.text.toString() &&
                    binding.newPassword.text.toString().length > 8

            Log.i(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")
            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        binding.newUserName.text.insert(0, "swordsmanluke")
        binding.newPassword.text.insert(0, "100themon")
        binding.confirmPassword.text.insert(0, "100themon")

        binding.newUserBtnGo.setOnClickListener {
            binding.newUserBtnGo.isEnabled = false
            binding.newPassword.isEnabled = false
            binding.confirmPassword.isEnabled = false
            binding.newUserName.isEnabled = false

            Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()

            // TODO: Show Spinner until this completes
            GlobalScope.launch {
                bwModel.createAccount(binding.newUserName.text.toString(), binding.newPassword.text.toString())
                // TODO: After create account returns an AccountSuccess event (or something) transition
                val nav = requireView().findNavController()
                Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_newUserFragment_to_contentFragment) }
            }
        }

        // FIXME: Delete this permanently once account creation is deemed ready
        binding.btnSwdslk.setOnClickListener {
             Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()

            GlobalScope.launch {
                bwModel.createAccount("swordsmanluke", "fake1@3pass")
                // TODO: After create account returns an AccountSuccess event (or something) transition
                val nav = requireView().findNavController()
                Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_newUserFragment_to_contentFragment) }
            }
        }

        return binding.root
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment NewUserFragment.
         */
        @JvmStatic
        fun newInstance() = NewUserFragment()

        const val TAG = "NewUserFragment"
    }
}