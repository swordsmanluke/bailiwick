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
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.QRCode
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentSubscribeBinding
import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.crypto.spec.SecretKeySpec


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SubscribeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SubscribeFragment : BailiwickFragment() {

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
        GlobalScope.launch {
            Handler(requireContext().mainLooper).post {
                binding.spnIdentities.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("Identity") + bwModel.network.manifest.feeds.map { it.identity.name }
                )
            }
        }

        binding.spnIdentities.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {

                val identity = bwModel.network.manifest.feeds.getOrNull(position - 1)?.identity
                binding.txtName.text = identity?.name ?: ""
                binding.avatar.setImageBitmap(identity?.avatar ?:
                    BitmapFactory.decodeStream(requireContext().assets.open("avatar.png")))

                if(identity != null) {
                    val key = Md5Signature().sign(binding.txtPassword.text.toString().toByteArray())
                    val cipher = AESEncryptor(SecretKeySpec(key, "AES"))
                    val ciphertext = cipher.encrypt(Gson().toJson(buildRequest(binding)).toByteArray())

                    binding.imgQrCode.setImageBitmap(QRCode.create(ciphertext))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.txtName.text = ""
                binding.avatar.setImageBitmap(BitmapFactory.decodeStream(requireContext().assets.open("avatar.png")))
                binding.imgQrCode.setImageBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ALPHA_8))
            }
        }

        binding.btnRequest.setOnClickListener {
            val imagefolder: File = File(requireContext().cacheDir, "images")
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

    fun buildRequest(binding: FragmentSubscribeBinding): Introduction {
        val map = mutableMapOf<String, String>()
        map["name"] = binding.txtName.text.toString()
        map["peer"] = bwModel.network.peerId
        val idCid = bwModel.network.manifest.feeds.getOrNull(binding.spnIdentities.selectedItemPosition - 1)?.identity?.cid!!
        return Introduction(UUID.randomUUID(), bwModel.network.peerId, idCid, Base64.getEncoder().encodeToString(bwModel.network.keyPair.public.encoded))
    }

    companion object {
        const val TAG = "SubscribeFragment"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SubscribeFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            SubscribeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}