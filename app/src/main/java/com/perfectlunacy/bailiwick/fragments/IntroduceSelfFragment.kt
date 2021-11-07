package com.perfectlunacy.bailiwick.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.QRCode
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentSubscribeBinding
import com.perfectlunacy.bailiwick.models.ipfs.Introduction
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import com.perfectlunacy.bailiwick.storage.PeerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.crypto.spec.SecretKeySpec


class IntroduceSelfFragment : BailiwickFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val binding = DataBindingUtil.inflate<FragmentSubscribeBinding>(
            inflater,
            R.layout.fragment_subscribe,
            container,
            false
        )

        // TODO: Manage multiple feeds with names etc. Manifest needs a facade
        //  Also, these files need their CIDs readily available.
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val identities = bwModel.network.users.map { it.name }

                Handler(requireContext().mainLooper).post {
                    binding.spnIdentities.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("Identity") + identities
                    )
                }
            }
        }

        binding.spnIdentities.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                bwModel.viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        val identity = bwModel.network.users.getOrNull(position - 1)
                        binding.txtName.text = identity?.name ?: ""
                        binding.avatar.setImageBitmap(identity?.avatar(requireContext().filesDir.toPath()))

                        if (identity != null) {
                            val key =
                                Md5Signature().sign(
                                    binding.txtPassword.text.toString().toByteArray()
                                )
                            val cipher = AESEncryptor(SecretKeySpec(key, "AES"))
                            val request =
                                buildRequest(
                                    binding.txtName.text.toString(),
                                    bwModel.network.peerId
                                )
                            val ciphertext = cipher.encrypt(Gson().toJson(request).toByteArray())

                            binding.imgQrCode.setImageBitmap(QRCode.create(ciphertext))
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.txtName.text = ""
                binding.avatar.setImageBitmap(BitmapFactory.decodeStream(requireContext().assets.open("avatar.png")))
                binding.imgQrCode.setImageBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ALPHA_8))
            }
        }

        binding.btnRequest.setOnClickListener {
            val imagefolder = File(requireContext().cacheDir, "images")
            imagefolder.mkdirs()
            val f = File(imagefolder, "connect_request.png")
            val out = FileOutputStream(f)
            binding.imgQrCode.drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100,out)
            out.flush()
            out.close()
            val uri = FileProvider.getUriForFile(requireContext(), "com.perfectlunacy.shareimage.fileprovider", f)

            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT,
                "Hi! I'd like to connect on Bailiwick: the pro-social network! You can find out more here: https://bailiwick.space")
            Log.i(TAG, "Attaching ${f.path}")
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sendIntent.type = "image/png"
            startActivity(sendIntent)
        }

        return binding.root
    }

    fun buildRequest(name: String, peerId: PeerId): Introduction {
        TODO("Access public key")
        return Introduction(false,
            peerId,
            name,
            "")
    }

    companion object {
        const val TAG = "SubscribeFragment"
    }
}