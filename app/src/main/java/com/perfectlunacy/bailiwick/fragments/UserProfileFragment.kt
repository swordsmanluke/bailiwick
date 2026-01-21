package com.perfectlunacy.bailiwick.fragments

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.CircleMembershipAdapter
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.crypto.KeyExporter
import com.perfectlunacy.bailiwick.databinding.FragmentUserProfileBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleMember
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.util.AvatarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment for displaying a user's profile with their posts and circle membership.
 *
 * Can show:
 * - Own profile (with edit button)
 * - Other user's profile (with subscribe/unsubscribe and circle management)
 */
class UserProfileFragment : BailiwickFragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

    private var userId: Long = -1
    private var identity: Identity? = null
    private var isOwnProfile: Boolean = false

    private var circleMembershipAdapter: CircleMembershipAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getLong(ARG_USER_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_user_profile,
            container,
            false
        )

        setupHeader()
        loadUserProfile()

        return binding.root
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadUserProfile() {
        bwModel.viewModelScope.launch {
            val (user, isOwn, avatar, posts, circles, memberOfCircleIds) = withContext(Dispatchers.Default) {
                val user = if (userId > 0) {
                    bwModel.db.identityDao().find(userId)
                } else {
                    bwModel.network.me
                }

                val isOwn = user.id == bwModel.network.me.id

                val avatar = AvatarLoader.loadAvatar(user, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))

                val posts = bwModel.db.postDao().postsFor(user.id)
                    .sortedByDescending { it.timestamp }

                // Get circles (only the user's own circles for membership management)
                val circles = bwModel.network.circles
                    .filter { it.name != EVERYONE_CIRCLE }

                // Get which circles this user is a member of
                val memberOfCircleIds = bwModel.db.circleMemberDao()
                    .circlesFor(user.id)
                    .toSet()

                ProfileData(user, isOwn, avatar, posts, circles, memberOfCircleIds)
            }

            identity = user
            isOwnProfile = isOwn
            binding.identity = user
            binding.isOwnProfile = isOwn

            // Update UI based on profile type
            updateProfileUI(isOwn)

            // Set avatar
            binding.imgAvatar.setImageBitmap(avatar)

            // Setup circle membership
            setupCircleMembership(circles, memberOfCircleIds)

            // Setup posts list
            setupPostsList(posts)
        }
    }

    private fun updateProfileUI(isOwnProfile: Boolean) {
        if (isOwnProfile) {
            // Own profile - show edit and export buttons
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnExportIdentity.visibility = View.VISIBLE
            binding.cardCircles.visibility = View.GONE  // Don't show circle membership for own profile

            binding.btnEditProfile.setOnClickListener {
                findNavController().navigate(R.id.action_userProfileFragment_to_editIdentityFragment)
            }

            binding.btnExportIdentity.setOnClickListener {
                showExportDialog()
            }
        } else {
            binding.btnExportIdentity.visibility = View.GONE
            // Other user's profile - show circle membership
            binding.btnEditProfile.visibility = View.GONE
            binding.cardCircles.visibility = View.VISIBLE
            binding.btnManageContact.visibility = View.VISIBLE

            // Manage Contact button - navigate to contact management
            binding.btnManageContact.setOnClickListener {
                navigateToContact()
            }
        }
    }

    private fun navigateToContact() {
        val bundle = ContactFragment.newBundle(userId)
        findNavController().navigate(
            R.id.action_userProfileFragment_to_contactFragment,
            bundle
        )
    }

    private fun setupCircleMembership(circles: List<Circle>, memberOfCircleIds: Set<Long>) {
        if (circles.isEmpty()) {
            binding.listCircles.visibility = View.GONE
            binding.txtNoCircles.visibility = View.VISIBLE
            return
        }

        binding.listCircles.visibility = View.VISIBLE
        binding.txtNoCircles.visibility = View.GONE

        binding.listCircles.layoutManager = LinearLayoutManager(requireContext())

        circleMembershipAdapter = CircleMembershipAdapter(
            requireContext(),
            circles,
            memberOfCircleIds
        ) { circle, isMember ->
            toggleCircleMembership(circle, isMember)
        }

        binding.listCircles.adapter = circleMembershipAdapter
    }

    private fun toggleCircleMembership(circle: Circle, isMember: Boolean) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                if (isMember) {
                    bwModel.db.circleMemberDao().insert(CircleMember(circle.id, userId))
                } else {
                    bwModel.db.circleMemberDao().delete(circle.id, userId)
                }
            }

            val message = if (isMember) {
                getString(R.string.added_to_circle, circle.name)
            } else {
                getString(R.string.removed_from_circle, circle.name)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            circleMembershipAdapter?.updateMembership(circle.id, isMember)
        }
    }

    private fun setupPostsList(posts: List<com.perfectlunacy.bailiwick.models.db.Post>) {
        if (posts.isEmpty()) {
            binding.listPosts.visibility = View.GONE
            binding.txtNoPosts.visibility = View.VISIBLE
            binding.txtPostCount.text = getString(R.string.no_posts_yet)
            return
        }

        binding.listPosts.visibility = View.VISIBLE
        binding.txtNoPosts.visibility = View.GONE

        // Set post count
        binding.txtPostCount.text = if (posts.size == 1) {
            getString(R.string.post_count_one)
        } else {
            getString(R.string.post_count, posts.size)
        }

        val adapter = PostAdapter(
            getBailiwickDb(requireContext()),
            bwModel,
            requireContext(),
            ArrayList(posts)
        )
        binding.listPosts.adapter = adapter

        // Set fixed height based on posts to avoid nested scrolling issues
        setListViewHeightBasedOnItems(binding.listPosts, posts.size)
    }

    private fun setListViewHeightBasedOnItems(listView: android.widget.ListView, itemCount: Int) {
        // Estimate ~200dp per post item
        val itemHeight = (200 * resources.displayMetrics.density).toInt()
        val totalHeight = itemHeight * itemCount.coerceAtMost(5)  // Max 5 visible at once
        val params = listView.layoutParams
        params.height = totalHeight
        listView.layoutParams = params
    }

    private fun showExportDialog() {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val passwordInput = EditText(context).apply {
            hint = getString(R.string.export_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val confirmInput = EditText(context).apply {
            hint = getString(R.string.export_password_confirm)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        dialogView.addView(passwordInput)
        dialogView.addView(confirmInput)

        AlertDialog.Builder(context)
            .setTitle(R.string.export_identity)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = passwordInput.text.toString()
                val confirm = confirmInput.text.toString()

                when {
                    password.length < 8 -> {
                        Toast.makeText(context, R.string.password_too_short, Toast.LENGTH_SHORT).show()
                    }
                    password != confirm -> {
                        Toast.makeText(context, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        performExport(password)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performExport(password: String) {
        val displayName = identity?.name ?: "unknown"
        val context = requireContext()

        bwModel.viewModelScope.launch {
            try {
                val exportData = withContext(Dispatchers.IO) {
                    KeyExporter.export(context, bwModel.db, password)
                }

                // Save to cache directory for sharing
                val filename = KeyExporter.getRecommendedFilename(displayName)
                val exportDir = File(context.cacheDir, "exports")
                val outputFile = File(exportDir, filename)

                withContext(Dispatchers.IO) {
                    KeyExporter.saveToFile(exportData, outputFile)
                }

                Toast.makeText(
                    context,
                    getString(R.string.export_success),
                    Toast.LENGTH_SHORT
                ).show()

                // Share the file
                shareExportFile(outputFile)

            } catch (e: KeyExporter.ExportException) {
                Log.e(TAG, "Export failed", e)
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected export error", e)
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareExportFile(file: File) {
        val context = requireContext()
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.perfectlunacy.shareimage.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Bailiwick Identity Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Save backup to..."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share export file", e)
            Toast.makeText(context, "Saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "UserProfileFragment"
        const val ARG_USER_ID = "user_id"

        fun newBundle(userId: Long): Bundle {
            return Bundle().apply {
                putLong(ARG_USER_ID, userId)
            }
        }
    }

    /**
     * Data class to hold all profile data loaded in a single coroutine.
     */
    private data class ProfileData(
        val user: Identity,
        val isOwn: Boolean,
        val avatar: android.graphics.Bitmap,
        val posts: List<com.perfectlunacy.bailiwick.models.db.Post>,
        val circles: List<Circle>,
        val memberOfCircleIds: Set<Long>
    )
}
