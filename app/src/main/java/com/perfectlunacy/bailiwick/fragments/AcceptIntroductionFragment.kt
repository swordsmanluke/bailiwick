package com.perfectlunacy.bailiwick.fragments

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import com.google.gson.Gson
import com.google.zxing.integration.android.IntentIntegrator
import com.perfectlunacy.bailiwick.QRCode
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentAcceptSubscriptionBinding
import com.perfectlunacy.bailiwick.models.Action
import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.spec.SecretKeySpec

class AcceptIntroductionFragment : BailiwickFragment() {

    enum class AcceptMode {
        CaptureUser,
        SendResponse,
        NoResponseReqd
    }

    data class AcceptViewModel(
        val mode: MutableLiveData<AcceptMode>,
        var request: Introduction?,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = DataBindingUtil.inflate<FragmentAcceptSubscriptionBinding>(
            inflater,
            R.layout.fragment_accept_subscription,
            container,
            false
        )

        // Inflate the layout for this fragment
        binding.btnScan.setOnClickListener {
            val integrator = IntentIntegrator.forSupportFragment(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt("Scan the invitation!")
            integrator.setCameraId(0) // Use a specific camera of the device

            integrator.setBeepEnabled(true)
            integrator.setBarcodeImageEnabled(true)
            integrator.initiateScan()
        }

        binding.btnImages.setOnClickListener {
            // TODO pick image
            // TODO and then use zxing to decode the barcode
        }

        binding.btnSend.setOnClickListener {
            val imagefolder = File(requireContext().cacheDir, "images")
            imagefolder.mkdirs()
            val f = File(imagefolder, "connect_response.png")
            val out = FileOutputStream(f)
            binding.imgResponseQr.drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "com.perfectlunacy.shareimage.fileprovider",
                f
            )

            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(
                Intent.EXTRA_TEXT,
                "Thanks for reaching out! Here's my connect code."
            )
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sendIntent.type = "image/png"
            startActivity(sendIntent)
        }

        bwModel.acceptViewModel.mode.observe(viewLifecycleOwner) { newMode ->
            when (newMode!!) {
                AcceptMode.CaptureUser -> {
                    binding.layoutButtons.visibility = View.VISIBLE
                    binding.layoutSendResponse.visibility = View.GONE
                }
                AcceptMode.SendResponse -> {
                    binding.layoutButtons.visibility = View.GONE
                    binding.layoutSendResponse.visibility = View.VISIBLE

                    val password = ""

                    // Populate QR Response
                    bwModel.viewModelScope.launch {
                        withContext(Dispatchers.Default) {
                            val req = bwModel.network.createIntroductionResponse(
                                bwModel.network.cidForPath("bw/${BailiwickImpl.VERSION}/identity.json")!!,
                                password
                            )
                            Handler(requireContext().mainLooper).post {
                                binding.imgResponseQr.setImageBitmap(
                                    QRCode.create(req)
                                )
                            }
                        }
                    }
                }
                AcceptMode.NoResponseReqd -> {
                    binding.layoutButtons.visibility = View.GONE
                    binding.layoutSendResponse.visibility = View.GONE
                    val builder = AlertDialog.Builder(requireContext())
                    // TODO: String resource-ify these
                    builder.setMessage("Your introduction to ${bwModel.acceptViewModel.request?.name ?: "Unknown"} is complete!")
                    builder.setTitle("Introduction Made")

                    builder.apply {
                        setPositiveButton(R.string.ok) { dialog, id ->
                            val nav = requireView().findNavController()
                            Handler(requireContext().mainLooper).post {
                                nav.navigate(R.id.action_acceptSubscriptionFragment_to_contentFragment)
                            }
                        }
                    }.create().show()
                }
            }
        }

        return binding.root
    }

    // TODO: Replace this with registerForActivityResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(requireContext(), "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                // TODO: Capture password
                val key = Md5Signature().sign(byteArrayOf())
                val aes = AESEncryptor(SecretKeySpec(key, "AES"))
                val json = String(aes.decrypt(Base64.getDecoder().decode(result.contents)))
                val subReq = Gson().fromJson(json, Introduction::class.java)

                // TODO: Display identity and ask for confirmation
                // TODO: And which Circles to add them to. If any.
                val pubkey = KeyFactory.getInstance("RSA").generatePublic(
                    X509EncodedKeySpec(
                        Base64.getDecoder().decode(subReq.publicKey)
                    )
                )

                bwModel.viewModelScope.launch {
                    withContext(Dispatchers.Default) {

                        // Remember their key
                        bwModel.network.keyring.addPublicKey(subReq.peerId, subReq.publicKey)
                        // Send them our "everyone" key
                        val rsa = RsaWithAesEncryptor(null, pubkey)
                        val everyone =
                            bwModel.network.encryptorForKey("${bwModel.network.peerId}:everyone")
                        val action = Action.updateKeyAction(
                            bwModel.network,
                            rsa,
                            bwModel.network.keyring.secretKeys("${bwModel.network.peerId}:everyone")!!
                                .last()
                        )
                        val feedEveryone = bwModel.network.manifest.feeds.first()
                        feedEveryone.addAction(action)

                        val manCid = bwModel.network.manifest.updateFeed(feedEveryone, everyone)
                        bwModel.network.addBailiwickFile("manifest.json", manCid)

                        bwModel.network.users.add(subReq.peerId, pubkey)
                        bwModel.network.circles.all().first().add(subReq.peerId)

                        bwModel.acceptViewModel.request = subReq
                        if (subReq.isResponse) {
                            bwModel.acceptViewModel.mode.postValue(AcceptMode.NoResponseReqd)
                        } else {
                            bwModel.acceptViewModel.mode.postValue(AcceptMode.SendResponse)
                        }
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}