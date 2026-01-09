package com.perfectlunacy.bailiwick.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.perfectlunacy.bailiwick.QRCode
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentSubscribeBinding
import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.util.AvatarLoader
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*


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
        viewLifecycleOwner.lifecycleScope.launch {
            val identities = withContext(Dispatchers.Default) {
                bwModel.network.myIdentities.map { it.name }
            }
            val ctx = context ?: return@launch
            binding.spnIdentities.adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Identity") + identities
            )
        }

        binding.spnIdentities.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val identity = withContext(Dispatchers.IO) {
                        bwModel.network.myIdentities.getOrNull(position - 1)
                    }
                    val ctx = context ?: return@launch
                    binding.txtName.text = identity?.name ?: ""
                    binding.avatar.setImageBitmap(
                        identity?.let { AvatarLoader.loadAvatar(it, ctx.filesDir.toPath()) }
                    )

                    if (identity != null) {
                        val ciphertext = withContext(Dispatchers.IO) {
                            val cipher = AESEncryptor.fromPassword(binding.txtPassword.text.toString())
                            val request = buildRequest(
                                binding.txtName.text.toString(),
                                bwModel.network.nodeId
                            )
                            cipher.encrypt(GsonProvider.gson.toJson(request).toByteArray())
                        }
                        binding.imgQrCode.setImageBitmap(QRCode.create(ciphertext))
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
                "Hi! Let's connect on Bailiwick: the pro-user social network!\n" +
                        "You can find out more here: https://bailiwick.space")
            Log.d(TAG, "Attaching ${f.path}")
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sendIntent.type = "image/png"
            startActivity(sendIntent)
        }

        return binding.root
    }

    suspend fun buildRequest(name: String, nodeId: NodeId): Introduction {
        val docTicket = bwModel.iroh.myDocTicket()
        return Introduction(
            isResponse = false,
            peerId = nodeId,
            name = name,
            publicKey = Base64.getEncoder().encodeToString(bwModel.keyring.publicKey.encoded),
            docTicket = docTicket
        )
    }

    companion object {
        const val TAG = "SubscribeFragment"
    }
}