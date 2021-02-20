package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentSplashBinding
import com.perfectlunacy.bailiwick.storage.ipfs.lite.CID
import com.perfectlunacy.bailiwick.storage.ipfs.lite.IPFS
import java.util.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SplashFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SplashFragment : Fragment() {
    private var myIdentity: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Move this behind a "Bailiwick data store"
        val peerId = IPFS.getPeerID(requireContext()) ?: ""
        myIdentity = IPFS.getInstance(requireContext()).getText(CID("$peerId/identity")) ?: ""
    }

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
                if (myIdentity.isBlank()) {
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