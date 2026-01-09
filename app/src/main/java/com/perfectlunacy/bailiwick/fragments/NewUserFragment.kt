package com.perfectlunacy.bailiwick.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.databinding.FragmentNewUserBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleMember
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Subscription
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.storage.NodeId
import io.bloco.faker.Faker
import kotlinx.coroutines.*
import java.io.*
import java.net.URL
import java.util.*

/**
 * A simple [Fragment] subclass.
 * Use the [NewUserFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class NewUserFragment : BailiwickFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val binding = DataBindingUtil.inflate<FragmentNewUserBinding>(inflater, R.layout.fragment_new_user, container, false)
        var avatar = BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))

        binding.newUserName.doOnTextChanged { text, start, before, count ->
            val goIsEnabled = binding.newUserName.text.toString().length > 3 &&
                    binding.confirmPassword.text.toString() == binding.newPassword.text.toString() &&
                    binding.newPassword.text.toString().length > 8

            Log.d(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")

            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        binding.avatar.setOnClickListener {
            // TODO: Instead of using Faker, build this URL myself. Maybe some radio buttons?
            viewLifecycleOwner.lifecycleScope.launch {
                val downloadedAvatar = withContext(Dispatchers.IO) {
                    val imgUrl = URL(Faker().avatar.image(Calendar.getInstance().timeInMillis.toString()))
                    BitmapFactory.decodeStream(imgUrl.openConnection().getInputStream())
                }
                avatar = downloadedAvatar
                binding.avatar.setImageBitmap(avatar)
            }
        }

        // Make
        // sure new and confirmed password fields are the same
        binding.newPassword.doOnTextChanged { text, start, before, count ->
            val goIsEnabled = binding.newUserName.text.toString().length > 3 &&
                    binding.confirmPassword.text.toString() == binding.newPassword.text.toString() &&
                    binding.newPassword.text.toString().length > 8

            Log.d(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")
            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        binding.confirmPassword.doOnTextChanged { text, start, before, count ->
            val goIsEnabled = binding.newUserName.text.toString().length > 3 &&
                    binding.confirmPassword.text.toString() == binding.newPassword.text.toString() &&
                    binding.newPassword.text.toString().length > 8

            Log.d(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")
            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        binding.newUserBtnGo.setOnClickListener {
            binding.newUserBtnGo.isEnabled = false
            binding.newPassword.isEnabled = false
            binding.confirmPassword.isEnabled = false
            binding.newUserName.isEnabled = false

            Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()

            // TODO: Show Spinner until this completes
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val out = ByteArrayOutputStream()
                    avatar.compress(Bitmap.CompressFormat.PNG, 100, out)
                    val nodeId = bwModel.network.nodeId
                    val avatarCid = bwModel.iroh.storeBlob(out.toByteArray())
                    bwModel.network.storeFile(avatarCid, ByteArrayInputStream(out.toByteArray()))
                    // Use public name for display, fall back to username if empty
                    val displayName = binding.newPublicName.text.toString().ifEmpty {
                        binding.newUserName.text.toString()
                    }
                    newAccount(nodeId, displayName, avatarCid)
                }
                view?.findNavController()?.navigate(R.id.action_newUserFragment_to_contentFragment)
            }
        }

        binding.btnRando.setOnClickListener {
            Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val out = ByteArrayOutputStream()
                    avatar.compress(Bitmap.CompressFormat.PNG, 100, out)
                    val nodeId = bwModel.network.nodeId
                    val avatarCid = bwModel.iroh.storeBlob(out.toByteArray())
                    bwModel.network.storeFile(avatarCid, ByteArrayInputStream(out.toByteArray()))
                    newAccount(nodeId, Faker().name.name(), avatarCid)
                }
                view?.findNavController()?.navigate(R.id.action_newUserFragment_to_contentFragment)
            }
        }

        return binding.root
    }

    private fun newAccount(nodeId: NodeId, name: String, avatarHash: String) {
        val ctx = context ?: return
        // Use the same database instance from the ViewModel to ensure consistency
        val db = bwModel.db
        val filesDir = ctx.filesDir.toPath()

        Log.d(TAG, "Creating new account: nodeId=$nodeId, name=$name")
        val identity = Identity(null, nodeId, name, avatarHash)
        val identityId = db.identityDao().insert(identity)
        Log.d(TAG, "Inserted identity with id=$identityId")

        // Verify the identity was saved
        val saved = db.identityDao().find(identityId)
        Log.d(TAG, "Verified saved identity: id=${saved.id}, owner=${saved.owner}, name=${saved.name}")

        db.subscriptionDao()
            .insert(Subscription(nodeId, 0)) // Always subscribed to ourselves

        val circle = Circle(EVERYONE_CIRCLE, identityId, null)
        val circleId = db.circleDao().insert(circle)

        // Create a key for this circle
        val rsaCipher = RsaWithAesEncryptor(bwModel.keyring.privateKey, bwModel.keyring.publicKey)
        Keyring.generateAesKey(db.keyDao(), filesDir, circleId, rsaCipher)

        // Add a new member to the circle
        db.circleMemberDao().insert(CircleMember(circleId, identityId))
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