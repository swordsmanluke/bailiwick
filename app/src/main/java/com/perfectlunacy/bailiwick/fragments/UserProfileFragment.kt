package com.perfectlunacy.bailiwick.fragments

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.CircleMembershipAdapter
import com.perfectlunacy.bailiwick.adapters.PostAdapter
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
            // Own profile - show edit button, hide subscribe buttons
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnSubscribe.visibility = View.GONE
            binding.btnUnsubscribe.visibility = View.GONE
            binding.cardCircles.visibility = View.GONE  // Don't show circle membership for own profile

            binding.btnEditProfile.setOnClickListener {
                Toast.makeText(context, "Edit profile coming soon", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Other user's profile - show circle membership and subscribe options
            binding.btnEditProfile.visibility = View.GONE
            binding.cardCircles.visibility = View.VISIBLE

            // Determine if user is subscribed (in any circle)
            bwModel.viewModelScope.launch {
                val isSubscribed = withContext(Dispatchers.Default) {
                    val circles = bwModel.db.circleMemberDao().circlesFor(userId)
                    circles.isNotEmpty()
                }

                if (isSubscribed) {
                    binding.btnSubscribe.visibility = View.GONE
                    binding.btnUnsubscribe.visibility = View.VISIBLE
                } else {
                    binding.btnSubscribe.visibility = View.VISIBLE
                    binding.btnUnsubscribe.visibility = View.GONE
                }
            }

            // Subscribe button - adds to first circle
            binding.btnSubscribe.setOnClickListener {
                subscribeToUser()
            }

            // Unsubscribe button - removes from all circles
            binding.btnUnsubscribe.setOnClickListener {
                unsubscribeFromUser()
            }
        }
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

            // Update subscribe/unsubscribe button visibility
            updateSubscribeButtonState()
        }
    }

    private fun subscribeToUser() {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                // Add to first available circle
                val firstCircle = bwModel.network.circles
                    .filter { it.name != EVERYONE_CIRCLE }
                    .firstOrNull()

                if (firstCircle != null) {
                    bwModel.db.circleMemberDao().insert(CircleMember(firstCircle.id, userId))
                }
            }

            Toast.makeText(context, R.string.subscribe, Toast.LENGTH_SHORT).show()
            loadUserProfile()  // Refresh the view
        }
    }

    private fun unsubscribeFromUser() {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                // Remove from all circles
                val memberOfCircles = bwModel.db.circleMemberDao().circlesFor(userId)
                memberOfCircles.forEach { circleId ->
                    bwModel.db.circleMemberDao().delete(circleId, userId)
                }
            }

            Toast.makeText(context, R.string.unsubscribe, Toast.LENGTH_SHORT).show()
            loadUserProfile()  // Refresh the view
        }
    }

    private fun updateSubscribeButtonState() {
        bwModel.viewModelScope.launch {
            val isSubscribed = withContext(Dispatchers.Default) {
                val circles = bwModel.db.circleMemberDao().circlesFor(userId)
                circles.isNotEmpty()
            }

            if (isSubscribed) {
                binding.btnSubscribe.visibility = View.GONE
                binding.btnUnsubscribe.visibility = View.VISIBLE
            } else {
                binding.btnSubscribe.visibility = View.VISIBLE
                binding.btnUnsubscribe.visibility = View.GONE
            }
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
