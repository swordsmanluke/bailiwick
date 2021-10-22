package com.perfectlunacy.bailiwick

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentSubscribeBinding
import com.perfectlunacy.bailiwick.fragments.BailiwickFragment
import com.perfectlunacy.bailiwick.models.SubscriptionRequest
import com.perfectlunacy.bailiwick.models.ipfs.Identity
import com.perfectlunacy.bailiwick.signatures.Md5Signature
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
            binding.spnIdentities.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Identity") + bwModel.network.manifest.feeds.map{it.identity.name}
            )

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

                    binding.imgQrCode.setImageBitmap(buildQrCode(ciphertext))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.txtName.text = ""
                binding.avatar.setImageBitmap(BitmapFactory.decodeStream(requireContext().assets.open("avatar.png")))
                binding.imgQrCode.setImageBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ALPHA_8))
            }

        }

        return binding.root
    }

    fun buildRequest(binding: FragmentSubscribeBinding): SubscriptionRequest {
        val map = mutableMapOf<String, String>()
        map["name"] = binding.txtName.text.toString()
        map["peer"] = bwModel.network.peerId
        val idCid = bwModel.network.manifest.feeds.getOrNull(binding.spnIdentities.selectedItemPosition - 1)?.identity?.cid!!
        return SubscriptionRequest(bwModel.network.peerId, idCid, Base64.getEncoder().encodeToString(bwModel.network.keyPair.public.encoded))
    }

    fun buildQrCode(data: ByteArray): Bitmap {
        val writer = QRCodeWriter()
        val content = Base64.getEncoder().encodeToString(data)
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.getWidth ()
        val height = bitMatrix.getHeight ();
        val bmp = Bitmap.createBitmap (width, height, Bitmap.Config.RGB_565);

        for (x in (0 until width)) {
            for (y in (0 until height)) {
                val color = if (bitMatrix.get(x, y)) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
                bmp.setPixel(x, y, color);
            }
        }

        return bmp
    }

    companion object {
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