package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.models.ipfs.Post
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

/**
 * The main [Fragment] of Bailiwick. This class manages the doomscrollable view of downloaded Content
 */
class ContentFragment : BailiwickFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DataBindingUtil.inflate<FragmentContentBinding>(
            inflater,
            R.layout.fragment_content,
            container,
            false
        )

        binding.btnPost.setOnClickListener {
            val text = binding.txtPostText.text.toString()
            binding.txtPostText.text.clear()

            GlobalScope.launch {
                val newPost = Post(
                    Calendar.getInstance().timeInMillis,
                    null,
                    text,
                    emptyList(),
                    ""
                )
                bwModel.savePost(newPost, "${bwModel.bwNetwork.peerId}:everyone")
                Log.i(TAG, "Saved new post. Refreshing...")
                refreshContent()
            }
        }

        if (bwModel.selectedUser != null) {
            binding.user = bwModel.selectedUser
        }

        buildAdapter(binding.listContent)
        refreshContent()

        return binding.root
    }

    private var adapter: Optional<PostAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView) {
        adapter = Optional.of(buildListAdapter())
        messagesList.setAdapter(adapter.get())
    }

    private fun refreshContent() {
        GlobalScope.launch {
            val adapter: PostAdapter = adapter.get()
            bwModel.refreshContent()
            adapter.clear()
            val posts = bwModel.content["everyone"]?.toList() ?: emptyList() // Wrap in the style the adapter expects
            adapter.addToEnd(posts)
        }
    }

    private fun buildListAdapter(): PostAdapter {
        val adapter = PostAdapter(
            requireContext(),
            ArrayList()
        )
        return adapter
    }

    companion object {
        const val TAG="ContentFragment"
    }
}