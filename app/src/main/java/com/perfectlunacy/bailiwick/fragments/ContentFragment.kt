package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.perfectlunacy.bailiwick.MockDataWriter
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.storage.MockBailiwickNetwork
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

/**
 * The main [Fragment] of Bailiwick. This class manages the doomscrollable view of downloaded Content
 */
class ContentFragment : BailiwickFragment() {

    val mocker:MockDataWriter
        get() {
            return MockDataWriter(bwModel.bwNetwork as MockBailiwickNetwork)
        }

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

        binding.btnMockData.setOnClickListener {
            mocker.generateFakePost(requireContext())
            refreshContent(adapter.get())
        }

        binding.btnNextUser.setOnClickListener{
            bwModel.selectNextUser()
            binding.user = bwModel.selectedUser
            if(adapter.isPresent) { refreshContent(adapter.get()) }
        }

        binding.btnPrevUser.setOnClickListener{
            bwModel.selectPrevUser()
            binding.user = bwModel.selectedUser
            if(adapter.isPresent) { refreshContent(adapter.get()) }
        }

        if (bwModel.selectedUser != null) {
            binding.user = bwModel.selectedUser
        }
        buildAdapter(binding.listContent)
        if(adapter.isPresent) { refreshContent(adapter.get()) }

        return binding.root
    }

    private var adapter: Optional<PostAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView) {
        adapter = Optional.of(buildListAdapter())
        messagesList.setAdapter(adapter.get())
    }

    private fun refreshContent(adapter: PostAdapter) {
        GlobalScope.launch {
            bwModel.refreshContent()
        }

        adapter.clear()
        val posts = bwModel.content[bwModel.selectedUser] ?: emptyList() // Wrap in the style the adapter expects
        adapter.addToEnd(posts)
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