package com.perfectlunacy.bailiwick.fragments

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.adapters.UserButtonAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.workers.IpfsUploadWorker
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList
import kotlin.io.path.pathString


/**
 * The main [Fragment] of Bailiwick. This class manages the doomscrollable view of downloaded Content
 */
class ContentFragment : BailiwickFragment() {

    private var _binding: FragmentContentBinding? = null

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

//        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
//        binding.listUsers.setLayoutManager(layoutManager)

        val binding = _binding!! // capture and assert not null to make the Kotlin compiler happy
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val users = bwModel.getUsers()
                binding.listUsers.adapter = UserButtonAdapter(requireContext(), users)
                displayAvatar(binding)
            }
        }

        binding.btnRefresh.setOnClickListener {
            bwModel.viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    DownloadRunner(
                        requireContext(),
                        Bailiwick.getInstance().db,
                        Bailiwick.getInstance().ipfs
                    ).run()
                }
            }
            refreshContent()
        }

        binding.btnAddSubscription.setOnClickListener {
            val nav = requireView().findNavController()
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_contentFragment_to_connectFragment) }
        }

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val sequence = Bailiwick.getInstance().db.sequenceDao().find(bwModel.ipfs.peerID)?.sequence ?: 0
                Handler(requireContext().mainLooper).post {
                    binding.txtPeer.text = "${bwModel.ipfs.peerID}:${sequence}"
                }
            }
        }


        binding.btnPost.setOnClickListener {
            val text = binding.txtPostText.text.toString()
            binding.txtPostText.text.clear()

            bwModel.viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    // TODO: Signatures
                    val newPost = Post(
                        bwModel.network.me.id,
                        null,
                        Calendar.getInstance().timeInMillis,
                        null,
                        text,
                        ""
                    )

                    val circId = bwModel.network.circles.first().id
                    bwModel.network.storePost(circId, newPost)

                    Log.i(TAG, "Saved new post. Refreshing...")
                    IpfsUploadWorker.enqueue(requireContext(), false) // Upload new content

                    refreshContent()
                }
            }
        }

        buildAdapter(binding.listContent)
        refreshContent()

        return binding.root
    }

    private fun displayAvatar(binding: FragmentContentBinding) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val avatar = bwModel.network.me.avatar(requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))

                Handler(requireContext().mainLooper).post {
                    binding.imgMyAvatar.setImageBitmap(avatar)
                }
            }
        }
    }

    private var adapter: Optional<PostAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView) {
        adapter = Optional.of(buildListAdapter())
        messagesList.setAdapter(adapter.get())
    }

    private fun refreshContent() {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val adapter: PostAdapter = adapter.get()
                bwModel.refreshContent()
                adapter.clear()
                val posts = bwModel.content["everyone"] ?: emptySet()
                adapter.addToEnd(posts.toList())

                val sequence = Bailiwick.getInstance().db.sequenceDao().find(bwModel.ipfs.peerID)?.sequence ?: 0
                Handler(requireContext().mainLooper).post {
                    _binding?.let {
                        it.txtPeer.text = "${bwModel.ipfs.peerID}:${sequence}"
                    }
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

    companion object {
        const val TAG="ContentFragment"
    }
}