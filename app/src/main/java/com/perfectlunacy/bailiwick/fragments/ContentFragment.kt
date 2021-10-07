package com.perfectlunacy.bailiwick.fragments

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.perfectlunacy.bailiwick.MockDataWriter
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.SocialItemAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.fragments.views.PostMessage
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.storage.MockBailiwickNetwork
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * The main [Fragment] of Bailiwick. This class manages the doomscrollable view of downloaded Content
 */
class ContentFragment : BailiwickFragment() {

    val mocker:MockDataWriter
        get() {
            return MockDataWriter(bwModel.db, bwModel.bwNetwork as MockBailiwickNetwork)
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

    private var adapter: Optional<SocialItemAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView) {
        adapter = Optional.of(buildListAdapter())
        messagesList.setAdapter(adapter.get())
    }

    private fun refreshContent(adapter: SocialItemAdapter) {
        GlobalScope.launch {
            bwModel.refreshContent()
        }

        adapter.clear()
        val posts = bwModel.content[bwModel.selectedUser]?.map { PostMessage(it) } ?: emptyList() // Wrap in the style the adapter expects
        adapter.addToEnd(posts)

        Log.i(TAG, "Found ${posts.count()} posts! ${posts.filter { !it.imageUrl().isNullOrBlank() }.count()} with attachments!")
    }

    private fun buildListAdapter(): SocialItemAdapter {
        val adapter: SocialItemAdapter = SocialItemAdapter(
            requireContext(),
            ArrayList()
        )
        return adapter
    }

    companion object {
        const val TAG="ContentFragment"
    }
}