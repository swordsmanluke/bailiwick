package com.perfectlunacy.bailiwick.fragments

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.google.zxing.FormatException
import com.google.zxing.integration.android.IntentIntegrator
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.InitState
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.QRCode
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentAcceptSubscriptionBinding
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.PeerDoc
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.qr.QREncoder
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

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

    private val getScannableImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val ctx = context ?: return@registerForActivityResult
        val stream = ctx.contentResolver.openInputStream(uri) ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                try {
                    val decodedText = String(QREncoder().decode(stream))
                    processIntroduction(decodedText)
                } catch (e: FormatException) {
                    Log.e("Accept Intro", "Error decoding barcode " + e.message, e)
                }
            }
        }
    }

    private val scanQrCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (intentResult != null) {
            if (intentResult.contents == null) {
                Toast.makeText(requireContext(), "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                // Wait for initialization before processing (handles Activity recreation)
                viewLifecycleOwner.lifecycleScope.launch {
                    val bwActivity = activity as? BailiwickActivity ?: return@launch
                    // Wait until initialization is complete
                    bwActivity.initState.first { it is InitState.Ready }
                    processIntroduction(intentResult.contents)
                }
            }
        }
    }

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
            scanQrCode.launch(integrator.createScanIntent())
        }

        binding.btnImages.setOnClickListener {
            getScannableImage.launch("image/*")
        }

        binding.btnSend.setOnClickListener {
            val imagefolder = File(requireContext().cacheDir, "images")
            imagefolder.mkdirs()
            val f = File(imagefolder, "connect_response.png")
            FileOutputStream(f).use { out ->
                binding.imgResponseQr
                    .drawable
                    .toBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
            }
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

        // Wait for initialization before accessing bwModel (handles Activity recreation)
        viewLifecycleOwner.lifecycleScope.launch {
            val bwActivity = activity as? BailiwickActivity ?: return@launch
            bwActivity.initState.first { it is InitState.Ready }
            setupModeObserver(binding)
        }

        return binding.root
    }

    private fun setupModeObserver(binding: FragmentAcceptSubscriptionBinding) {
        bwModel.acceptViewModel.mode.observe(viewLifecycleOwner) { newMode ->
            when (newMode!!) {
                AcceptMode.CaptureUser -> {
                    binding.layoutButtons.visibility = View.VISIBLE
                    binding.layoutSendResponse.visibility = View.GONE
                }
                AcceptMode.SendResponse -> {
                    binding.layoutButtons.visibility = View.GONE
                    binding.layoutSendResponse.visibility = View.VISIBLE
                    binding.btnSend.isEnabled = false

                    val password = ""

                    viewLifecycleOwner.lifecycleScope.launch {
                        val ciphertext = withContext(Dispatchers.Default) {
                            val cipher = AESEncryptor.fromPassword(password)

                            val response = buildResponse(bwModel.name, bwModel.network.nodeId)
                            cipher.encrypt(GsonProvider.gson.toJson(response).toByteArray())
                        }
                        binding.imgResponseQr.setImageBitmap(QRCode.create(ciphertext))
                        binding.btnSend.isEnabled = true
                    }
                }
                AcceptMode.NoResponseReqd -> {
                    binding.layoutButtons.visibility = View.GONE
                    binding.layoutSendResponse.visibility = View.GONE
                    val ctx = context ?: return@observe
                    val builder = AlertDialog.Builder(ctx)
                    // TODO: String resource-ify these
                    builder.setMessage("Your introduction to ${bwModel.acceptViewModel.request?.name ?: "Unknown"} is complete!")
                    builder.setTitle("Introduction Made")

                    builder.apply {
                        setPositiveButton(R.string.ok) { _, _ ->
                            view?.findNavController()?.navigate(R.id.action_acceptSubscriptionFragment_to_contentFragment)
                        }
                    }.create().show()
                }
            }
        }
    }

    suspend fun buildResponse(name: String, nodeId: NodeId): Introduction {
        val docTicket = bwModel.iroh.myDocTicket()
        return Introduction(
            isResponse = true,
            peerId = nodeId,
            name = name,
            publicKey = Base64.getEncoder().encodeToString(bwModel.keyring.publicKey.encoded),
            docTicket = docTicket
        )
    }

    private fun processIntroduction(rawData: String) {
        val ctx = context ?: return
        // TODO: Capture password
        val aes = AESEncryptor.fromPassword("")
        val json = String(aes.decrypt(Base64.getDecoder().decode(rawData)))
        val intro = GsonProvider.gson.fromJson(json, Introduction::class.java)

        val filesDir = ctx.filesDir.toPath()

        // TODO: Display identity and ask for confirmation
        // TODO: And which Circles to add them to. If any.
        viewLifecycleOwner.lifecycleScope.launch {
            val alreadyFriends = withContext(Dispatchers.Default) {
                val db = getBailiwickDb(ctx)

                // Check to see if we already have this user
                val isExistingUser = db.userDao().publicKeyFor(intro.peerId) != null

                // Join the peer's doc using their ticket
                val peerDoc = bwModel.iroh.joinDoc(intro.docTicket)
                if (peerDoc == null) {
                    Log.e("AcceptIntro", "Failed to join doc for ${intro.name}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Failed to connect to peer", Toast.LENGTH_LONG).show()
                    }
                    return@withContext true // Treat as "already friends" to skip further processing
                }

                val docNamespaceId = peerDoc.namespaceId()
                Log.i("AcceptIntro", "Joined doc for ${intro.name}: $docNamespaceId")

                // Store/update the peer's doc namespace for syncing content
                // Also store the ticket so we can re-join after app restart
                db.peerDocDao().upsert(
                    PeerDoc(
                        nodeId = intro.peerId,
                        docNamespaceId = docNamespaceId,
                        displayName = intro.name,
                        lastSyncedAt = 0,
                        isSubscribed = true,
                        docTicket = intro.docTicket
                    )
                )
                Log.i("AcceptIntro", "Stored PeerDoc for ${intro.name}: $docNamespaceId")

                if (isExistingUser) {
                    return@withContext true
                }

                // Store the new user and their key
                db.userDao().insert(User(intro.peerId, intro.publicKey))

                // Create an Action with our "everyone" key. It will be encrypted with their Public key
                val rsa = RsaWithAesEncryptor(bwModel.keyring.privateKey, bwModel.keyring.publicKey)
                val everyoneId = bwModel.network.circles.find { it.name == EVERYONE_CIRCLE }?.id ?: 0
                val circKey = Keyring.keyForCircle(
                    db.keyDao(),
                    filesDir,
                    everyoneId.toInt(),
                    rsa
                )

                bwModel.network.storeAction(
                    Action.updateKeyAction(
                        intro.peerId,
                        Base64.getEncoder().encodeToString(circKey)
                    )
                )
                false
            }

            if (alreadyFriends) {
                Toast.makeText(
                    context,
                    "You are already friends with this user!",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            bwModel.acceptViewModel.request = intro
            if (intro.isResponse) {
                bwModel.acceptViewModel.mode.postValue(AcceptMode.NoResponseReqd)
            } else {
                bwModel.acceptViewModel.mode.postValue(AcceptMode.SendResponse)
            }
        }
    }
}