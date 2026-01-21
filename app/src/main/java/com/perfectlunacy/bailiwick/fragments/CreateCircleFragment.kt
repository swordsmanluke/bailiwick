package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentCreateCircleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateCircleFragment : BailiwickFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DataBindingUtil.inflate<FragmentCreateCircleBinding>(
            inflater,
            R.layout.fragment_create_circle,
            container,
            false
        )

        binding.btnCreateCircle.setOnClickListener {
            val name = binding.txtCircleName.text?.toString()?.trim() ?: ""

            if (name.isEmpty()) {
                binding.layoutCircleName.error = getString(R.string.circle_name)
                return@setOnClickListener
            }

            binding.layoutCircleName.error = null
            binding.btnCreateCircle.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val circle = withContext(Dispatchers.IO) {
                    val identity = bwModel.network.myIdentities.firstOrNull()
                        ?: return@withContext null
                    val newCircle = bwModel.network.createCircle(name, identity)

                    // Generate encryption key for this circle - CRITICAL for security
                    val ctx = context ?: return@withContext newCircle
                    val filesDir = ctx.filesDir.toPath()
                    val rsaCipher = RsaWithAesEncryptor(bwModel.keyring.privateKey, bwModel.keyring.publicKey)
                    Keyring.generateAesKey(bwModel.db.keyDao(), filesDir, newCircle.id, rsaCipher)

                    newCircle
                }

                if (circle != null) {
                    Toast.makeText(context, R.string.circle_created, Toast.LENGTH_SHORT).show()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_CIRCLE_ID, circle.id)
                    findNavController().popBackStack()
                } else {
                    binding.btnCreateCircle.isEnabled = true
                }
            }
        }

        return binding.root
    }

    companion object {
        const val RESULT_CIRCLE_ID = "circle_id"
    }
}
