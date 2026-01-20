package com.perfectlunacy.bailiwick.fragments

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.ContactCircleAdapter
import com.perfectlunacy.bailiwick.adapters.ContactIdentityAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentContactBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleMember
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.util.AvatarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for managing a contact's relationship - mute, delete, and circle membership.
 *
 * Unlike UserProfileFragment which shows posts, this screen focuses on
 * relationship management:
 * - View multiple identities the contact has shared
 * - Mute/unmute the contact
 * - Delete the contact entirely
 * - Manage circle membership (add to / remove from circles)
 */
class ContactFragment : BailiwickFragment() {

    private var _binding: FragmentContactBinding? = null
    private val binding get() = _binding!!

    private var identityId: Long = -1
    private var user: User? = null
    private var identities: List<Pair<Identity, Bitmap>> = emptyList()
    private var selectedIdentityIndex: Int = 0

    private var identityAdapter: ContactIdentityAdapter? = null
    private var circleAdapter: ContactCircleAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        identityId = arguments?.getLong(ARG_IDENTITY_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_contact,
            container,
            false
        )

        setupHeader()
        loadContactData()

        return binding.root
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnMute.setOnClickListener {
            user?.let { toggleMute(it) }
        }

        binding.btnDelete.setOnClickListener {
            user?.let { showDeleteConfirmation(it) }
        }

        binding.btnViewPosts.setOnClickListener {
            navigateToUserProfile()
        }
    }

    private fun loadContactData() {
        bwModel.viewModelScope.launch {
            val contactData = withContext(Dispatchers.Default) {
                // Load the identity first
                val identity = bwModel.db.identityDao().find(identityId)
                    ?: return@withContext null

                // Find the User by the identity's owner (nodeId)
                val user = bwModel.db.userDao().findByNodeId(identity.owner)
                    ?: return@withContext null

                // Load all identities for this contact
                val allIdentities = bwModel.db.identityDao().identitiesFor(identity.owner)

                // Load avatars for each identity
                val defaultAvatar = BitmapFactory.decodeStream(
                    requireContext().assets.open("avatar.png")
                )
                val identitiesWithAvatars = allIdentities.map { id ->
                    val avatar = AvatarLoader.loadAvatar(id, requireContext().filesDir.toPath())
                        ?: defaultAvatar
                    Pair(id, avatar)
                }

                // Find which identity was originally selected
                val selectedIndex = allIdentities.indexOfFirst { it.id == identityId }
                    .coerceAtLeast(0)

                // Get all circles (excluding "everyone")
                val allCircles = bwModel.network.circles
                    .filter { it.name != EVERYONE_CIRCLE }

                // Get circles the user is a member of
                val memberOfCircleIds = bwModel.db.circleMemberDao()
                    .circlesFor(identityId)
                    .toSet()

                val memberCircles = allCircles.filter { memberOfCircleIds.contains(it.id) }

                ContactData(
                    user = user,
                    identitiesWithAvatars = identitiesWithAvatars,
                    selectedIndex = selectedIndex,
                    allCircles = allCircles,
                    memberCircles = memberCircles.toMutableList()
                )
            }

            if (contactData == null) {
                Toast.makeText(context, R.string.contact_load_error, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return@launch
            }

            user = contactData.user
            identities = contactData.identitiesWithAvatars
            selectedIdentityIndex = contactData.selectedIndex

            // Update UI
            updateMuteButton(contactData.user.isMuted)
            binding.txtMutedIndicator.visibility =
                if (contactData.user.isMuted) View.VISIBLE else View.GONE

            // Set initial contact name
            if (identities.isNotEmpty()) {
                binding.contactName = identities[selectedIdentityIndex].first.name
            }

            // Setup identity carousel
            setupIdentityCarousel(identities, selectedIdentityIndex)

            // Setup add to circle dropdown
            setupAddToCircleDropdown(contactData.allCircles, contactData.memberCircles)

            // Setup circles list
            setupCirclesList(contactData.memberCircles)
        }
    }

    private fun updateMuteButton(isMuted: Boolean) {
        binding.btnMute.setImageResource(
            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
        binding.btnMute.contentDescription = getString(
            if (isMuted) R.string.unmute_contact else R.string.mute_contact
        )
    }

    private fun setupIdentityCarousel(
        identities: List<Pair<Identity, Bitmap>>,
        selectedIndex: Int
    ) {
        binding.listIdentities.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        identityAdapter = ContactIdentityAdapter(
            requireContext(),
            identities,
            selectedIndex
        ) { identity, index ->
            selectedIdentityIndex = index
            binding.contactName = identity.name
        }

        binding.listIdentities.adapter = identityAdapter
    }

    private fun setupAddToCircleDropdown(allCircles: List<Circle>, memberCircles: List<Circle>) {
        val memberCircleIds = memberCircles.map { it.id }.toSet()

        // Create display items - greyed out circles the contact is already in
        val circleItems = allCircles.map { circle ->
            if (memberCircleIds.contains(circle.id)) {
                "${circle.name} (${getString(R.string.already_a_member)})"
            } else {
                circle.name
            }
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            circleItems
        )

        binding.dropdownAddCircle.setAdapter(adapter)
        binding.dropdownAddCircle.setOnItemClickListener { _, _, position, _ ->
            val circle = allCircles[position]

            // Check if already a member
            if (memberCircleIds.contains(circle.id)) {
                binding.dropdownAddCircle.text.clear()
                return@setOnItemClickListener
            }

            addToCircle(circle)
            binding.dropdownAddCircle.text.clear()
        }
    }

    private fun setupCirclesList(memberCircles: MutableList<Circle>) {
        if (memberCircles.isEmpty()) {
            binding.listCircles.visibility = View.GONE
            binding.txtNoCircles.visibility = View.VISIBLE
            return
        }

        binding.listCircles.visibility = View.VISIBLE
        binding.txtNoCircles.visibility = View.GONE

        binding.listCircles.layoutManager = LinearLayoutManager(requireContext())

        circleAdapter = ContactCircleAdapter(
            requireContext(),
            memberCircles,
            onRemoveClick = { circle, position ->
                removeFromCircle(circle, position)
            },
            onCircleClick = { circle ->
                navigateToEditCircle(circle)
            }
        )

        binding.listCircles.adapter = circleAdapter
    }

    private fun navigateToEditCircle(circle: Circle) {
        val bundle = EditCircleFragment.newBundle(circle.id)
        findNavController().navigate(
            R.id.action_contactFragment_to_editCircleFragment,
            bundle
        )
    }

    private fun navigateToUserProfile() {
        val bundle = UserProfileFragment.newBundle(identityId)
        findNavController().navigate(
            R.id.action_contactFragment_to_userProfileFragment,
            bundle
        )
    }

    private fun addToCircle(circle: Circle) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                bwModel.db.circleMemberDao().insert(CircleMember(circle.id, identityId))
            }

            Toast.makeText(
                context,
                getString(R.string.added_to_circle, circle.name),
                Toast.LENGTH_SHORT
            ).show()

            // Update the circles list
            circleAdapter?.addCircle(circle)

            // Show the list if it was hidden
            binding.listCircles.visibility = View.VISIBLE
            binding.txtNoCircles.visibility = View.GONE

            // Refresh the dropdown to show updated "already a member" status
            refreshAddToCircleDropdown()
        }
    }

    private fun removeFromCircle(circle: Circle, position: Int) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                bwModel.db.circleMemberDao().delete(circle.id, identityId)
            }

            Toast.makeText(
                context,
                getString(R.string.removed_from_circle, circle.name),
                Toast.LENGTH_SHORT
            ).show()

            // Update the adapter
            circleAdapter?.removeCircle(position)

            // Show empty state if no circles left
            if (circleAdapter?.getCircles()?.isEmpty() == true) {
                binding.listCircles.visibility = View.GONE
                binding.txtNoCircles.visibility = View.VISIBLE
            }

            // Refresh the dropdown
            refreshAddToCircleDropdown()
        }
    }

    private fun refreshAddToCircleDropdown() {
        bwModel.viewModelScope.launch {
            val (allCircles, memberCircles) = withContext(Dispatchers.Default) {
                val allCircles = bwModel.network.circles
                    .filter { it.name != EVERYONE_CIRCLE }

                val memberOfCircleIds = bwModel.db.circleMemberDao()
                    .circlesFor(identityId)
                    .toSet()

                val memberCircles = allCircles.filter { memberOfCircleIds.contains(it.id) }

                Pair(allCircles, memberCircles)
            }

            setupAddToCircleDropdown(allCircles, memberCircles)
        }
    }

    private fun toggleMute(user: User) {
        if (user.isMuted) {
            // Unmute immediately (no confirmation needed)
            performUnmute(user)
        } else {
            // Show confirmation for mute
            showMuteConfirmation(user)
        }
    }

    private fun showMuteConfirmation(user: User) {
        val contactName = identities.getOrNull(selectedIdentityIndex)?.first?.name ?: "this contact"

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.mute_confirmation_title, contactName))
            .setMessage(R.string.mute_confirmation_message)
            .setPositiveButton(R.string.mute_contact) { _, _ ->
                performMute(user)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performMute(user: User) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                bwModel.db.userDao().setMuted(user.id, true)
            }

            user.isMuted = true
            updateMuteButton(true)
            binding.txtMutedIndicator.visibility = View.VISIBLE

            val contactName = identities.getOrNull(selectedIdentityIndex)?.first?.name ?: ""
            Toast.makeText(
                context,
                getString(R.string.muted_contact, contactName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun performUnmute(user: User) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                bwModel.db.userDao().setMuted(user.id, false)
            }

            user.isMuted = false
            updateMuteButton(false)
            binding.txtMutedIndicator.visibility = View.GONE

            val contactName = identities.getOrNull(selectedIdentityIndex)?.first?.name ?: ""
            Toast.makeText(
                context,
                getString(R.string.unmuted_contact, contactName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDeleteConfirmation(user: User) {
        val contactName = identities.getOrNull(selectedIdentityIndex)?.first?.name ?: "this contact"

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_contact_title)
            .setMessage(getString(R.string.delete_contact_message, contactName))
            .setPositiveButton(R.string.delete_contact) { _, _ ->
                performDelete(user)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performDelete(user: User) {
        val contactName = identities.getOrNull(selectedIdentityIndex)?.first?.name ?: ""

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                // Remove from all circles
                val memberOfCircles = bwModel.db.circleMemberDao().circlesFor(identityId)
                memberOfCircles.forEach { circleId ->
                    bwModel.db.circleMemberDao().delete(circleId, identityId)
                }

                // Delete all posts from this user
                val identities = bwModel.db.identityDao().identitiesFor(user.nodeId)
                identities.forEach { identity ->
                    val posts = bwModel.db.postDao().postsFor(identity.id)
                    posts.forEach { post ->
                        bwModel.db.postDao().delete(post.id)
                    }
                }

                // Delete the user
                bwModel.db.userDao().delete(user.id)
            }

            Toast.makeText(
                context,
                getString(R.string.deleted_contact, contactName),
                Toast.LENGTH_SHORT
            ).show()

            // Navigate back
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ContactFragment"
        const val ARG_IDENTITY_ID = "identity_id"

        fun newBundle(identityId: Long): Bundle {
            return Bundle().apply {
                putLong(ARG_IDENTITY_ID, identityId)
            }
        }
    }

    /**
     * Data class to hold all contact data loaded in a single coroutine.
     */
    private data class ContactData(
        val user: User,
        val identitiesWithAvatars: List<Pair<Identity, Bitmap>>,
        val selectedIndex: Int,
        val allCircles: List<Circle>,
        val memberCircles: MutableList<Circle>
    )
}
