package com.perfectlunacy.bailiwick.fragments

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentEditIdentityBinding
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.services.GossipService
import com.perfectlunacy.bailiwick.util.AvatarLoader
import com.perfectlunacy.bailiwick.util.PhotoPicker
import com.perfectlunacy.bailiwick.util.RobotAvatarGenerator
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Fragment for editing the user's identity (name and avatar).
 */
class EditIdentityFragment : BailiwickFragment() {

    private var _binding: FragmentEditIdentityBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoPicker: PhotoPicker
    private var identity: Identity? = null
    private var selectedAvatar: Bitmap? = null
    private var avatarChanged: Boolean = false
    private var originalName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoPicker = PhotoPicker(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_edit_identity,
            container,
            false
        )

        setupHeader()
        loadIdentity()
        setupAvatarPicker()

        return binding.root
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            handleBackPressed()
        }

        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadIdentity() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (user, avatar) = withContext(Dispatchers.Default) {
                val user = bwModel.network.me

                val avatar = AvatarLoader.loadAvatar(user, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))

                Pair(user, avatar)
            }

            identity = user
            originalName = user.name
            binding.identity = user

            // Set current avatar
            selectedAvatar = avatar
            binding.btnAvatar.setImageBitmap(avatar)
        }
    }

    private fun setupAvatarPicker() {
        binding.btnAvatar.setOnClickListener {
            showPhotoOptions()
        }

        binding.btnCamera.setOnClickListener {
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            pickFromGallery()
        }

        binding.btnGenerateAvatar.setOnClickListener {
            generateRobotAvatar()
        }
    }

    private fun showPhotoOptions() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_photo),
            getString(R.string.generate_robot_avatar)
        )

        AlertDialog.Builder(requireContext())
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
                avatarChanged = true
                binding.btnAvatar.setImageBitmap(bitmap)
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
                avatarChanged = true
                binding.btnAvatar.setImageBitmap(bitmap)
            },
            onError = { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun generateRobotAvatar() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading()
                val bitmap = withContext(Dispatchers.IO) {
                    RobotAvatarGenerator.generate()
                }
                hideLoading()
                selectedAvatar = bitmap
                avatarChanged = true
                binding.btnAvatar.setImageBitmap(bitmap)
            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(context, "Failed to generate avatar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentName = binding.txtPublicName.text?.toString() ?: ""
        return avatarChanged || currentName != originalName
    }

    private fun handleBackPressed() {
        if (hasUnsavedChanges()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.discard_changes_title)
                .setMessage(R.string.discard_changes_message)
                .setPositiveButton(R.string.discard) { _, _ ->
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.keep_editing, null)
                .show()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun saveChanges() {
        val newName = binding.txtPublicName.text?.toString() ?: ""
        val currentIdentity = identity ?: return

        showLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var newAvatarHash = currentIdentity.profilePicHash

                    // If avatar changed, upload the new one
                    if (avatarChanged && selectedAvatar != null) {
                        val out = ByteArrayOutputStream()
                        selectedAvatar!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                        val avatarBytes = out.toByteArray()

                        newAvatarHash = bwModel.iroh.storeBlob(avatarBytes)
                        bwModel.network.storeFile(newAvatarHash, java.io.ByteArrayInputStream(avatarBytes))

                        Log.d(TAG, "Uploaded new avatar: $newAvatarHash")
                    }

                    // Update the identity
                    val updatedIdentity = Identity(
                        currentIdentity.blobHash,
                        currentIdentity.owner,
                        newName,
                        newAvatarHash
                    )
                    updatedIdentity.id = currentIdentity.id

                    bwModel.db.identityDao().update(updatedIdentity)

                    Log.d(TAG, "Updated identity: name=$newName, avatarHash=$newAvatarHash")

                    // Republish identity to Iroh blob storage (updates blobHash)
                    val publisher = ContentPublisher(bwModel.iroh, bwModel.db)
                    val newBlobHash = publisher.publishIdentity(updatedIdentity)
                    Log.d(TAG, "Republished identity with new blobHash: $newBlobHash")

                    // Trigger manifest sync to notify peers of updated identity
                    GossipService.getInstance()?.publishManifest()
                    Log.i(TAG, "Triggered manifest publish for identity update")
                }

                hideLoading()
                Toast.makeText(context, R.string.profile_updated, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save changes", e)
                hideLoading()
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading() {
        binding.overlayLoading.visibility = View.VISIBLE
        binding.layoutContent.alpha = 0.5f
        setFormEnabled(false)
    }

    private fun hideLoading() {
        binding.overlayLoading.visibility = View.GONE
        binding.layoutContent.alpha = 1.0f
        setFormEnabled(true)
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.btnSave.isEnabled = enabled
        binding.btnAvatar.isEnabled = enabled
        binding.btnCamera.isEnabled = enabled
        binding.btnGallery.isEnabled = enabled
        binding.btnGenerateAvatar.isEnabled = enabled
        binding.txtPublicName.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditIdentityFragment"
    }
}
