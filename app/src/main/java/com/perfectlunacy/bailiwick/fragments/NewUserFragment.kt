package com.perfectlunacy.bailiwick.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentNewUserBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleMember
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Subscription
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
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
    @DelicateCoroutinesApi
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

            Log.i(TAG, "Go Enabled: $goIsEnabled, pass eq: ${binding.confirmPassword.text.toString() == binding.newPassword.text.toString()}, passLen: ${binding.newPassword.text.length}, userlen: ${binding.newUserName.text.length}")

            binding.newUserBtnGo.isEnabled = goIsEnabled
        }

        binding.avatar.setOnClickListener {
            // TODO: Instead of using Faker, build this URL myself. Maybe some radio buttons?
            GlobalScope.launch {
                val imgUrl = URL(Faker().avatar.image(Calendar.getInstance().timeInMillis.toString()))
                avatar = BitmapFactory.decodeStream(imgUrl.openConnection().getInputStream())
                Handler(requireContext().mainLooper).post { binding.avatar.setImageBitmap(avatar) }
            }
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

        binding.newUserBtnGo.setOnClickListener {
            binding.newUserBtnGo.isEnabled = false
            binding.newPassword.isEnabled = false
            binding.confirmPassword.isEnabled = false
            binding.newUserName.isEnabled = false

            Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()

            // TODO: Show Spinner until this completes
            GlobalScope.launch {
                val out = ByteArrayOutputStream()
                avatar.compress(Bitmap.CompressFormat.PNG, 100, out)
                val peerId = bwModel.network.peerId
                // TODO: This needs to be a CID as well.
                bwModel.network.storeFile("avatar.png", ByteArrayInputStream(out.toByteArray()))
                newAccount(peerId, binding.name ?: "", "avatar.png")

                val nav = requireView().findNavController()
                Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_newUserFragment_to_contentFragment) }
            }
        }

        // FIXME: Delete this permanently once account creation is deemed ready
        binding.btnSwdslk.setOnClickListener {
            Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()

            val out = ByteArrayOutputStream()
            avatar.compress(Bitmap.CompressFormat.PNG, 100, out)
            val peerId = bwModel.network.peerId
            // TODO: This needs to be a CID as well.
            bwModel.network.storeFile("avatar.png", ByteArrayInputStream(out.toByteArray()))
            newAccount(peerId, "Lucas Taylor", "avatar.png")

            val nav = requireView().findNavController()
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_newUserFragment_to_contentFragment) }
        }

        binding.btnRando.setOnClickListener {
            Toast.makeText(this.context, "Creating account, please wait...", Toast.LENGTH_LONG).show()
            val out = ByteArrayOutputStream()
            avatar.compress(Bitmap.CompressFormat.PNG, 100, out)
            val peerId = bwModel.network.peerId
            bwModel.network.storeFile("avatar.png", ByteArrayInputStream(out.toByteArray()))
            newAccount(peerId, Faker().name.name(), "avatar.png")

            val nav = requireView().findNavController()
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_newUserFragment_to_contentFragment) }
        }

        return binding.root
    }

    private fun newAccount(peerId: PeerId, name: String, avatarCid: String) {
        val db = getBailiwickDb(requireContext())
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val identity = Identity(null, peerId, name, avatarCid)
                val identityId = db.identityDao().insert(identity)

                db.subscriptionDao()
                    .insert(Subscription(peerId, 0)) // Always subscribed to ourselves

                val circle = Circle("everyone", identityId, null)
                val circleId = db.circleDao().insert(circle)

                db.circleMemberDao().insert(CircleMember(circleId, identityId))
            }
        }
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