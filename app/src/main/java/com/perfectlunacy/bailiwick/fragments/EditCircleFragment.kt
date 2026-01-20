package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.CircleMemberAdapter
import com.perfectlunacy.bailiwick.adapters.ContactSearchAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentEditCircleBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleMember
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.util.AvatarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditCircleFragment : BailiwickFragment() {

    private var _binding: FragmentEditCircleBinding? = null
    private val binding get() = _binding!!

    private var circleId: Long = -1L
    private var circle: Circle? = null
    private var originalName: String = ""

    private val addedMemberIds = mutableSetOf<Long>()
    private val removedMemberIds = mutableSetOf<Long>()
    private var originalMemberIds = setOf<Long>()

    private var allContacts: List<Identity> = emptyList()
    private var memberAdapter: CircleMemberAdapter? = null
    private var searchAdapter: ContactSearchAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_edit_circle,
            container,
            false
        )

        circleId = arguments?.getLong("circle_id", -1L) ?: -1L

        setupToolbar()
        setupAdapters()
        setupListeners()
        loadCircleData()

        return binding.root
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBack()
        }

        binding.btnMenu.setOnClickListener { view ->
            showOverflowMenu(view)
        }
    }

    private fun setupAdapters() {
        memberAdapter = CircleMemberAdapter(
            requireContext(),
            onRemoveClick = { identity ->
                onRemoveMember(identity)
            },
            onMemberClick = { identity ->
                navigateToContact(identity)
            }
        )
        binding.listMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.listMembers.adapter = memberAdapter

        searchAdapter = ContactSearchAdapter(requireContext()) { identity ->
            onAddMember(identity)
        }
        binding.listSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.listSuggestions.adapter = searchAdapter
    }

    private fun setupListeners() {
        binding.txtCircleName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSaveButtonState()
            }
        })

        binding.txtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s?.toString() ?: "")
            }
        })

        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadCircleData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (loadedCircle, memberIds, contacts) = withContext(Dispatchers.IO) {
                val c = bwModel.db.circleDao().find(circleId)
                val mIds = bwModel.db.circleMemberDao().membersFor(circleId).toSet()
                val allIdentities = bwModel.db.identityDao().all()
                    .filter { it.owner != bwModel.network.nodeId }
                Triple(c, mIds, allIdentities)
            }

            circle = loadedCircle
            originalName = loadedCircle?.name ?: ""
            originalMemberIds = memberIds
            allContacts = contacts

            binding.txtCircleName.setText(originalName)
            binding.toolbar.title = originalName

            loadMembers(memberIds)
        }
    }

    private fun loadMembers(memberIds: Set<Long>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val memberItems = withContext(Dispatchers.IO) {
                memberIds.mapNotNull { id ->
                    val identity = bwModel.db.identityDao().find(id)
                    identity?.let {
                        val avatar = AvatarLoader.loadAvatar(it, requireContext().filesDir.toPath())
                        CircleMemberAdapter.MemberItem(it, avatar)
                    }
                }
            }
            memberAdapter?.setMembers(memberItems)
            updateMembersHeader()
        }
    }

    private fun filterContacts(query: String) {
        if (query.isBlank()) {
            binding.listSuggestions.visibility = View.GONE
            searchAdapter?.clear()
            return
        }

        val currentMemberIds = getCurrentMemberIds()
        val filtered = allContacts.filter { contact ->
            contact.id !in currentMemberIds &&
            contact.name.contains(query, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            binding.listSuggestions.visibility = View.GONE
            searchAdapter?.clear()
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                val suggestions = withContext(Dispatchers.IO) {
                    filtered.map { identity ->
                        val avatar = AvatarLoader.loadAvatar(identity, requireContext().filesDir.toPath())
                        ContactSearchAdapter.SuggestionItem(identity, avatar)
                    }
                }
                searchAdapter?.updateSuggestions(suggestions)
                binding.listSuggestions.visibility = View.VISIBLE
            }
        }
    }

    private fun getCurrentMemberIds(): Set<Long> {
        return (originalMemberIds + addedMemberIds) - removedMemberIds
    }

    private fun onAddMember(identity: Identity) {
        if (identity.id in removedMemberIds) {
            removedMemberIds.remove(identity.id)
        } else {
            addedMemberIds.add(identity.id)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val avatar = withContext(Dispatchers.IO) {
                AvatarLoader.loadAvatar(identity, requireContext().filesDir.toPath())
            }
            memberAdapter?.addMember(CircleMemberAdapter.MemberItem(identity, avatar))
        }

        binding.txtSearch.text?.clear()
        binding.listSuggestions.visibility = View.GONE
        searchAdapter?.clear()

        updateMembersHeader()
        updateSaveButtonState()
    }

    private fun onRemoveMember(identity: Identity) {
        if (identity.id in addedMemberIds) {
            addedMemberIds.remove(identity.id)
        } else {
            removedMemberIds.add(identity.id)
        }

        memberAdapter?.markRemoved(identity.id)
        updateMembersHeader()
        updateSaveButtonState()
    }

    private fun updateMembersHeader() {
        val count = getCurrentMemberIds().size
        binding.txtMembersHeader.text = getString(R.string.members_count, count)
    }

    private fun updateSaveButtonState() {
        val currentName = binding.txtCircleName.text?.toString() ?: ""
        val nameChanged = currentName != originalName
        val membersChanged = addedMemberIds.isNotEmpty() || removedMemberIds.isNotEmpty()

        binding.btnSave.isEnabled = (nameChanged || membersChanged) && currentName.isNotBlank()
    }

    private fun hasChanges(): Boolean {
        val currentName = binding.txtCircleName.text?.toString() ?: ""
        return currentName != originalName || addedMemberIds.isNotEmpty() || removedMemberIds.isNotEmpty()
    }

    private fun saveChanges() {
        val newName = binding.txtCircleName.text?.toString()?.trim() ?: ""

        if (newName.isBlank()) {
            binding.layoutCircleName.error = getString(R.string.circle_name_required)
            return
        }

        binding.layoutCircleName.error = null
        binding.btnSave.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Update circle name
                circle?.let {
                    it.name = newName
                    bwModel.db.circleDao().update(it)
                }

                // Add new members
                for (id in addedMemberIds) {
                    bwModel.db.circleMemberDao().insert(CircleMember(circleId, id))
                }

                // Remove members
                for (id in removedMemberIds) {
                    bwModel.db.circleMemberDao().delete(circleId, id)
                }
            }

            Toast.makeText(context, R.string.circle_updated, Toast.LENGTH_SHORT).show()
            findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_CIRCLE_ID, circleId)
            findNavController().popBackStack()
        }
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_edit_circle, popup.menu)

        // Hide delete option for "everyone" circle
        if (circle?.name == EVERYONE_CIRCLE) {
            popup.menu.findItem(R.id.action_delete)?.isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmation() {
        val circleName = circle?.name ?: return

        if (circleName == EVERYONE_CIRCLE) {
            Toast.makeText(context, R.string.cannot_delete_everyone, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_circle)
            .setMessage(getString(R.string.delete_circle_confirm, circleName))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteCircle()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteCircle() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Delete all members first
                for (memberId in originalMemberIds) {
                    bwModel.db.circleMemberDao().delete(circleId, memberId)
                }

                // Delete the circle
                circle?.let {
                    bwModel.db.circleDao().delete(it)
                }
            }

            Toast.makeText(context, R.string.circle_deleted, Toast.LENGTH_SHORT).show()
            findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_DELETED, true)
            findNavController().popBackStack()
        }
    }

    private fun handleBack() {
        if (hasChanges()) {
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

    private fun navigateToContact(identity: Identity) {
        val bundle = ContactFragment.newBundle(identity.id)
        findNavController().navigate(
            R.id.action_editCircleFragment_to_contactFragment,
            bundle
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_CIRCLE_ID = "edit_circle_result_id"
        const val RESULT_DELETED = "edit_circle_deleted"

        fun newBundle(circleId: Long): Bundle {
            return Bundle().apply {
                putLong("circle_id", circleId)
            }
        }
    }
}
