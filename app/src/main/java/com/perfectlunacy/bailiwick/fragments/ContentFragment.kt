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
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
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

        GlobalScope.launch {
            val cipher = bwModel.network.encryptorForKey("${bwModel.network.peerId}:everyone")
            val picCiphers = MultiCipher(listOf(cipher, NoopEncryptor())) { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size) != null
            }

            val profilePicBytes =
                bwModel.network.download(bwModel.network.identity.profilePicCid)

            val avatar = if (profilePicBytes == null) {
                BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
            } else {
                val picBytes = picCiphers.decrypt(profilePicBytes)
                BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size)
            }

            Handler(requireContext().mainLooper).post { binding.imgMyAvatar.setImageBitmap(avatar) }
        }

        binding.btnAddSubscription.setOnClickListener {
            val nav = requireView().findNavController()
            Handler(requireContext().mainLooper).post { nav.navigate(R.id.action_contentFragment_to_connectFragment) }
        }

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
                bwModel.savePost(newPost, "${bwModel.network.peerId}:everyone")
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