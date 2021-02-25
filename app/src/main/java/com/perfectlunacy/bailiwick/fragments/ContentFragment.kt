package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.fragments.views.PostMessage
import com.squareup.picasso.Picasso
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.messages.MessagesList
import com.stfalcon.chatkit.messages.MessagesListAdapter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

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

        binding.btnMockData.setOnClickListener {
            bwModel.generateFakePost(requireContext())
            buildAdapter(binding.messagesList)
        }

        binding.btnNextUser.setOnClickListener{
            bwModel.selectNextUser()
            if(adapter.isPresent) { updateAdapter(adapter.get()) }
        }

        binding.btnPrevUser.setOnClickListener{
            bwModel.selectPrevUser()
            if(adapter.isPresent) { updateAdapter(adapter.get()) }
        }

        buildAdapter(binding.messagesList)

        return binding.root
    }

    private var adapter: Optional<MessagesListAdapter<PostMessage>> = Optional.empty()
    private fun buildAdapter(messagesList: MessagesList) {
        adapter = Optional.of(buildListAdapter())
        messagesList.setAdapter(adapter.get())
    }

    private fun updateAdapter(adapter: MessagesListAdapter<PostMessage>) {
        GlobalScope.launch {
            bwModel.refreshContent()
        }

        adapter.clear()
        val posts = bwModel.content[bwModel.selectedUser.get()]?.map { PostMessage(it) } // Wrap in the style the adapter expects
        adapter.addToEnd(posts, false)

        Log.i(TAG, "Found ${posts?.count() ?: 0} posts! ${posts?.filter { !it.imageUrl.isNullOrBlank() }?.count() ?: 0} with attachments!")
    }

    private fun buildListAdapter(): MessagesListAdapter<PostMessage> {
        val imgLoader: ImageLoader = bindImageLoader()
        val adapter: MessagesListAdapter<PostMessage> = MessagesListAdapter(
            bwModel.bwNetwork.myId(),
            imgLoader
        )
        return adapter
    }

    private fun bindImageLoader(): ImageLoader {
        val imgLoader: ImageLoader = object : ImageLoader {
            override fun loadImage(imageView: ImageView?, url: String?, payload: Any?) {
                File(url).also { file ->
                    if (file.exists()) {
                        Picasso.get().load(file).into(imageView)
                    }
                }
            }
        }
        return imgLoader
    }

    companion object {
        const val TAG="ContentFragment"
    }
}