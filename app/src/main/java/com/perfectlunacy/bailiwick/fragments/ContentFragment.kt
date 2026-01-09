package com.perfectlunacy.bailiwick.fragments

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.adapters.UserButtonAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostFile
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.util.AvatarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList


/**
 * The main [Fragment] of Bailiwick. This class manages the doomscrollable view of downloaded Content
 */
class ContentFragment : BailiwickFragment() {

    private var _binding: FragmentContentBinding? = null

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_content,
            container,
            false
        )

        val binding = _binding!! // capture and assert not null to make the Kotlin compiler happy

        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.listUsers.setLayoutManager(layoutManager)

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val users = bwModel.getUsers()
                withContext(Dispatchers.Main) {
                    binding.listUsers.adapter = UserButtonAdapter(requireContext(), users) { selectedUser ->
                        // Filter posts by selected user
                        filterPostsByUser(selectedUser)
                    }
                }
                displayAvatar(binding)
            }
        }

        binding.btnRefresh.setOnClickListener {
            // TODO: Implement Iroh-based content sync
            refreshContent()
        }

        binding.btnAddSubscription.setOnClickListener {
            requireView().findNavController().navigate(R.id.action_contentFragment_to_connectFragment)
        }

        bwModel.viewModelScope.launch {
            val nodeId = withContext(Dispatchers.Default) {
                Bailiwick.getInstance().iroh?.nodeId() ?: "not initialized"
            }
            binding.txtPeer.text = nodeId
        }


        binding.btnPost.setOnClickListener {
            val text = binding.txtPostText.text.toString()
            binding.txtPostText.text.clear()

            bwModel.viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    val keyring = Bailiwick.getInstance().keyring
                    val newPost = Post(
                        bwModel.network.me.id,
                        null,
                        Calendar.getInstance().timeInMillis,
                        null,
                        text,
                        ""
                    )

                    val signer = RsaSignature(keyring.publicKey, keyring.privateKey)
                    val files: List<PostFile> = emptyList()
                    newPost.sign(signer, files)
                    val circId = bwModel.network.circles.first().id
                    bwModel.network.storePost(circId, newPost)

                    Log.i(TAG, "Saved new post.")
                }
            }
        }

        buildAdapter(binding.listContent)
        refreshContent()

        return binding.root
    }

    private fun displayAvatar(binding: FragmentContentBinding) {
        bwModel.viewModelScope.launch {
            val avatar = withContext(Dispatchers.Default) {
                AvatarLoader.loadAvatar(bwModel.network.me, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
            }
            binding.imgMyAvatar.setImageBitmap(avatar)
        }
    }

    private var adapter: Optional<PostAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView) {
        adapter = Optional.of(buildListAdapter())
        messagesList.setAdapter(adapter.get())
    }

    @SuppressLint("SetTextI18n")
    private fun refreshContent() {
        bwModel.viewModelScope.launch {
            val nodeId = withContext(Dispatchers.Default) {
                val adapter: PostAdapter = adapter.get()
                bwModel.refreshContent()
                adapter.clear()
                val posts = bwModel.content[EVERYONE_CIRCLE] ?: emptySet()
                adapter.addToEnd(posts.toList().sortedByDescending { it.timestamp })

                Bailiwick.getInstance().iroh?.nodeId() ?: "not initialized"
            }
            _binding?.let {
                it.txtPeer.text = nodeId
            }
        }
    }

    private fun filterPostsByUser(user: Identity) {
        Log.d(TAG, "Filtering posts by user: ${user.name} (id=${user.id})")
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val adapter: PostAdapter = adapter.get()
                bwModel.refreshContent()
                val allPosts = bwModel.content[EVERYONE_CIRCLE] ?: emptySet()
                val filteredPosts = allPosts.filter { it.authorId == user.id }
                Log.d(TAG, "Found ${filteredPosts.size} posts from ${user.name} (out of ${allPosts.size} total)")
                withContext(Dispatchers.Main) {
                    adapter.clear()
                    adapter.addToEnd(filteredPosts.sortedByDescending { it.timestamp })
                }
            }
        }
    }

    private fun buildListAdapter(): PostAdapter {
        val adapter = PostAdapter(
            getBailiwickDb(requireContext()),
            bwModel,
            requireContext(),
            ArrayList()
        )
        return adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG="ContentFragment"
    }
}
