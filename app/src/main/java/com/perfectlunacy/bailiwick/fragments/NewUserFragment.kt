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
import com.perfectlunacy.bailiwick.util.PhotoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Account creation fragment with profile photo selection.
 */
class NewUserFragment : BailiwickFragment() {

    private var _binding: FragmentNewUserBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoPicker: PhotoPicker
    private var selectedAvatar: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoPicker = PhotoPicker(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_new_user,
            container,
            false
        )

        // Load default avatar
        selectedAvatar = BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))

        setupFormValidation()
        setupAvatarPicker()
        setupSubmitButton()

        return binding.root
    }

    private fun setupFormValidation() {
        val validateForm = {
            val usernameValid = binding.newUserName.text.toString().length >= 4
            val passwordValid = binding.newPassword.text.toString().length >= 8
            val passwordsMatch = binding.confirmPassword.text.toString() == binding.newPassword.text.toString()

            // Show/hide error message
            when {
                binding.newPassword.text.toString().isNotEmpty() &&
                        binding.confirmPassword.text.toString().isNotEmpty() &&
                        !passwordsMatch -> {
                    binding.txtError.visibility = View.VISIBLE
                    binding.txtError.text = getString(R.string.passwords_dont_match)
                }
                binding.newPassword.text.toString().isNotEmpty() &&
                        binding.newPassword.text.toString().length < 8 -> {
                    binding.txtError.visibility = View.VISIBLE
                    binding.txtError.text = getString(R.string.password_too_short)
                }
                else -> {
                    binding.txtError.visibility = View.GONE
                }
            }

            binding.newUserBtnGo.isEnabled = usernameValid && passwordValid && passwordsMatch

            Log.d(TAG, "Form validation: username=$usernameValid, password=$passwordValid, match=$passwordsMatch")
        }

        binding.newUserName.doOnTextChanged { _, _, _, _ -> validateForm() }
        binding.newPassword.doOnTextChanged { _, _, _, _ -> validateForm() }
        binding.confirmPassword.doOnTextChanged { _, _, _, _ -> validateForm() }
    }

    private fun setupAvatarPicker() {
        binding.avatar.setOnClickListener {
            showPhotoOptions()
        }
    }

    private fun showPhotoOptions() {
        // Show dialog with options: Camera or Gallery
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_photo)
        )

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_picture))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickFromGallery()
                }
            }
            .show()
    }

    private fun takePhoto() {
        photoPicker.takePhoto(
            onSelected = { bitmap ->
                selectedAvatar = bitmap
                binding.avatar.setImageBitmap(bitmap)
            },
            onError = { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun pickFromGallery() {
        photoPicker.pickPhoto(
            onSelected = { bitmap ->
                selectedAvatar = bitmap
                binding.avatar.setImageBitmap(bitmap)
            },
            onError = { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setupSubmitButton() {
        binding.newUserBtnGo.setOnClickListener {
            createAccount()
        }

        // Quick test account buttons
        binding.btnTestAlice.setOnClickListener {
            createQuickTestAccount("Alice")
        }
        binding.btnTestBob.setOnClickListener {
            createQuickTestAccount("Bob")
        }
        binding.btnTestRandom.setOnClickListener {
            val randomSuffix = (1000..9999).random()
            createQuickTestAccount("Tester$randomSuffix")
        }
    }

    private fun createAccount() {
        // Disable form during creation
        setFormEnabled(false)
        binding.progressLoading.visibility = View.VISIBLE

        val displayName = binding.newPublicName.text.toString().ifEmpty {
            binding.newUserName.text.toString()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val avatar = selectedAvatar ?: BitmapFactory.decodeStream(
                        requireContext().assets.open("avatar.png")
                    )

                    val out = ByteArrayOutputStream()
                    avatar.compress(Bitmap.CompressFormat.PNG, 100, out)

                    val nodeId = bwModel.network.nodeId
                    val avatarHash = bwModel.iroh.storeBlob(out.toByteArray())
                    bwModel.network.storeFile(avatarHash, java.io.ByteArrayInputStream(out.toByteArray()))

                    newAccount(nodeId, displayName, avatarHash)
                }

                // Navigate to main content
                view?.findNavController()?.navigate(R.id.action_newUserFragment_to_contentFragment)

            } catch (e: Exception) {
                Log.e(TAG, "Account creation failed", e)
                withContext(Dispatchers.Main) {
                    binding.txtError.visibility = View.VISIBLE
                    binding.txtError.text = "Account creation failed: ${e.message}"
                    setFormEnabled(true)
                    binding.progressLoading.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Create a quick test account with predefined credentials.
     * For development/testing only.
     */
    private fun createQuickTestAccount(displayName: String) {
        // Generate random username and password
        val randomId = System.currentTimeMillis() % 10000
        val username = "${displayName.lowercase()}_$randomId"
        val password = "testpass${randomId}"

        Log.i(TAG, "Creating quick test account: $displayName (user: $username)")

        // Fill in the form
        binding.newPublicName.setText(displayName)
        binding.newUserName.setText(username)
        binding.newPassword.setText(password)
        binding.confirmPassword.setText(password)

        // Trigger account creation
        createAccount()
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.newUserName.isEnabled = enabled
        binding.newPublicName.isEnabled = enabled
        binding.newPassword.isEnabled = enabled
        binding.confirmPassword.isEnabled = enabled
        binding.newUserBtnGo.isEnabled = enabled
        binding.avatar.isEnabled = enabled
    }

    private fun newAccount(nodeId: NodeId, name: String, avatarHash: String) {
        val ctx = context ?: return
        val db = bwModel.db
        val filesDir = ctx.filesDir.toPath()

        Log.d(TAG, "Creating new account: nodeId=$nodeId, name=$name")

        val identity = Identity(null, nodeId, name, avatarHash)
        val identityId = db.identityDao().insert(identity)
        Log.d(TAG, "Inserted identity with id=$identityId")

        // Verify the identity was saved
        val saved = db.identityDao().find(identityId)
        Log.d(TAG, "Verified saved identity: id=${saved.id}, owner=${saved.owner}, name=${saved.name}")

        db.subscriptionDao().insert(Subscription(nodeId, 0)) // Always subscribed to ourselves

        val circle = Circle(EVERYONE_CIRCLE, identityId, null)
        val circleId = db.circleDao().insert(circle)

        // Create a key for this circle
        val rsaCipher = RsaWithAesEncryptor(bwModel.keyring.privateKey, bwModel.keyring.publicKey)
        Keyring.generateAesKey(db.keyDao(), filesDir, circleId, rsaCipher)

        // Add a new member to the circle
        db.circleMemberDao().insert(CircleMember(circleId, identityId))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = NewUserFragment()

        const val TAG = "NewUserFragment"
    }
}
