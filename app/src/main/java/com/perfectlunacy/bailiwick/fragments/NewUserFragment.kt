package com.perfectlunacy.bailiwick.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
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
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.ALL_CIRCLE
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.util.PhotoPicker
import com.perfectlunacy.bailiwick.util.RobotAvatarGenerator
import com.perfectlunacy.bailiwick.util.SignUpFormValidator
import com.perfectlunacy.bailiwick.util.SignUpFormValidator.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Account creation fragment with profile photo selection, inline validation,
 * and improved loading/success states.
 */
class NewUserFragment : BailiwickFragment() {

    private var _binding: FragmentNewUserBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoPicker: PhotoPicker
    private var selectedAvatar: Bitmap? = null
    private var displayName: String = ""

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
        setupKeyboardActions()

        return binding.root
    }

    private fun setupFormValidation() {
        binding.newUserName.doOnTextChanged { text, _, _, _ ->
            validateUsername(text?.toString() ?: "")
            updateSubmitButtonState()
        }

        binding.newPassword.doOnTextChanged { text, _, _, _ ->
            validatePassword(text?.toString() ?: "")
            updateSubmitButtonState()
        }

        binding.confirmPassword.doOnTextChanged { text, _, _, _ ->
            validateConfirmPassword(text?.toString() ?: "")
            updateSubmitButtonState()
        }
    }

    private fun validateUsername(username: String): Boolean {
        val result = SignUpFormValidator.validateUsername(username)
        binding.layoutUsername.error = when (result) {
            is ValidationResult.TooShort -> getString(R.string.username_too_short)
            else -> null
        }
        return result.isValid
    }

    private fun validatePassword(password: String): Boolean {
        val result = SignUpFormValidator.validatePassword(password)
        binding.layoutPassword.error = when (result) {
            is ValidationResult.TooShort -> getString(R.string.password_too_short)
            else -> null
        }

        // Re-validate confirm password when password changes
        val confirmPassword = binding.confirmPassword.text?.toString() ?: ""
        if (confirmPassword.isNotEmpty()) {
            validateConfirmPassword(confirmPassword)
        }

        return result.isValid
    }

    private fun validateConfirmPassword(confirmPassword: String): Boolean {
        val password = binding.newPassword.text?.toString() ?: ""
        val result = SignUpFormValidator.validateConfirmPassword(password, confirmPassword)
        binding.layoutConfirmPassword.error = when (result) {
            is ValidationResult.Mismatch -> getString(R.string.passwords_dont_match)
            else -> null
        }
        return result.isValid
    }

    private fun updateSubmitButtonState() {
        val username = binding.newUserName.text?.toString() ?: ""
        val password = binding.newPassword.text?.toString() ?: ""
        val confirmPassword = binding.confirmPassword.text?.toString() ?: ""

        val isValid = SignUpFormValidator.isFormValid(username, password, confirmPassword)
        binding.newUserBtnGo.isEnabled = isValid

        Log.d(TAG, "Form validation: isValid=$isValid")
    }

    private fun setupKeyboardActions() {
        binding.confirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.newUserBtnGo.isEnabled) {
                createAccount()
                true
            } else {
                false
            }
        }
    }

    private fun setupAvatarPicker() {
        binding.avatar.setOnClickListener {
            showPhotoOptions()
        }
    }

    private fun showPhotoOptions() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_photo),
            getString(R.string.generate_robot_avatar)
        )

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_picture))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickFromGallery()
                    2 -> generateRobotAvatar()
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

    private fun generateRobotAvatar() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoadingOverlay(getString(R.string.loading))
                val bitmap = withContext(Dispatchers.IO) {
                    RobotAvatarGenerator.generate()
                }
                hideLoadingOverlay()
                selectedAvatar = bitmap
                binding.avatar.setImageBitmap(bitmap)
            } catch (e: Exception) {
                hideLoadingOverlay()
                Toast.makeText(context, "Failed to generate avatar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
        // Hide keyboard
        hideKeyboard()

        // Show loading overlay
        showLoadingOverlay(getString(R.string.creating_account))

        displayName = binding.newPublicName.text.toString().ifEmpty {
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

                // Show success overlay
                showSuccessOverlay(displayName)

                // Navigate after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    view?.findNavController()?.navigate(R.id.action_newUserFragment_to_contentFragment)
                }, SUCCESS_DISPLAY_DURATION)

            } catch (e: Exception) {
                Log.e(TAG, "Account creation failed", e)
                withContext(Dispatchers.Main) {
                    hideLoadingOverlay()
                    showError(getString(R.string.account_creation_failed) + ": ${e.message}")
                }
            }
        }
    }

    /**
     * Create a quick test account with predefined credentials.
     * For development/testing only.
     */
    private fun createQuickTestAccount(displayName: String) {
        val randomId = System.currentTimeMillis() % 10000
        val username = "${displayName.lowercase()}_$randomId"
        val password = "testpass${randomId}"

        Log.i(TAG, "Creating quick test account: $displayName (user: $username)")

        binding.newPublicName.setText(displayName)
        binding.newUserName.setText(username)
        binding.newPassword.setText(password)
        binding.confirmPassword.setText(password)

        // Trigger account creation
        createAccount()
    }

    private fun showLoadingOverlay(message: String) {
        binding.txtLoadingMessage.text = message
        binding.overlayLoading.visibility = View.VISIBLE
        setFormEnabled(false)
    }

    private fun hideLoadingOverlay() {
        binding.overlayLoading.visibility = View.GONE
        setFormEnabled(true)
    }

    private fun showSuccessOverlay(name: String) {
        binding.overlayLoading.visibility = View.GONE
        binding.txtWelcomeMessage.text = getString(R.string.welcome_message, name)
        binding.overlaySuccess.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        view?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.newUserName.isEnabled = enabled
        binding.newPublicName.isEnabled = enabled
        binding.newPassword.isEnabled = enabled
        binding.confirmPassword.isEnabled = enabled
        binding.newUserBtnGo.isEnabled = enabled && isFormValid()
        binding.avatar.isEnabled = enabled
        binding.btnTestAlice.isEnabled = enabled
        binding.btnTestBob.isEnabled = enabled
        binding.btnTestRandom.isEnabled = enabled
    }

    private fun isFormValid(): Boolean {
        val username = binding.newUserName.text?.toString() ?: ""
        val password = binding.newPassword.text?.toString() ?: ""
        val confirmPassword = binding.confirmPassword.text?.toString() ?: ""

        return SignUpFormValidator.isFormValid(username, password, confirmPassword)
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

        val circle = Circle(ALL_CIRCLE, identityId, null)
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
        const val SUCCESS_DISPLAY_DURATION = 1500L
    }
}
