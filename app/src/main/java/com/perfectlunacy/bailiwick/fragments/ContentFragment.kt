package com.perfectlunacy.bailiwick.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.CircleFilterAdapter
import com.perfectlunacy.bailiwick.adapters.PhotoPreviewAdapter
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.adapters.UserButtonAdapter
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.storage.BlobCache
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostFile
import com.perfectlunacy.bailiwick.services.GossipService
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.storage.FilterPreferences
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.util.AvatarLoader
import com.perfectlunacy.bailiwick.util.PhotoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*


/**
 * The main [Fragment] of Bailiwick. This class manages the doomscrollable view of downloaded Content
 */
class ContentFragment : BailiwickFragment() {

    private var _binding: FragmentContentBinding? = null

    // Filter state
    private lateinit var filterPrefs: FilterPreferences
    private var filterByUserId: Long? = null
    private var filterByCircleId: Long? = null

    // Photo handling
    private lateinit var photoPicker: PhotoPicker
    private val selectedPhotos = mutableListOf<Bitmap>()

    // Circle filter adapter
    private var circleFilterAdapter: CircleFilterAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoPicker = PhotoPicker(this)
        filterPrefs = FilterPreferences(requireContext())
    }

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

        val binding = _binding!!

        // Restore filter state
        restoreFilterState()

        setupCircleFilter(binding)
        setupUserFilter(binding)
        setupPostComposer(binding)
        setupPostList(binding)

        bwModel.viewModelScope.launch {
            val nodeId = withContext(Dispatchers.Default) {
                Bailiwick.getInstance().iroh?.nodeId() ?: "not initialized"
            }
            binding.txtPeer.text = nodeId
        }

        return binding.root
    }

    private fun restoreFilterState() {
        when (filterPrefs.filterMode) {
            FilterPreferences.FilterMode.CIRCLE -> {
                filterByCircleId = filterPrefs.selectedCircleId.takeIf { it >= 0 }
            }
            FilterPreferences.FilterMode.PERSON -> {
                filterByUserId = filterPrefs.selectedUserId.takeIf { it >= 0 }
            }
            else -> {
                filterByCircleId = null
                filterByUserId = null
            }
        }
    }

    private fun setupCircleFilter(binding: FragmentContentBinding) {
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.listCircles.layoutManager = layoutManager

        bwModel.viewModelScope.launch {
            val circles = withContext(Dispatchers.Default) {
                // Filter out the "everyone" circle since it's redundant with the "All" option
                bwModel.network.circles.filter { it.name != EVERYONE_CIRCLE }
            }

            circleFilterAdapter = CircleFilterAdapter(
                requireContext(),
                circles,
                filterByCircleId
            ) { selectedCircle ->
                filterByCircle(selectedCircle)
            }
            binding.listCircles.adapter = circleFilterAdapter
        }
    }

    private fun setupUserFilter(binding: FragmentContentBinding) {
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.listUsers.layoutManager = layoutManager

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val users = bwModel.getUsers()
                withContext(Dispatchers.Main) {
                    binding.listUsers.adapter = UserButtonAdapter(requireContext(), users) { selectedUser ->
                        filterPostsByUser(selectedUser)
                    }
                }
                displayAvatar(binding)
            }
        }
    }

    private fun setupPostComposer(binding: FragmentContentBinding) {
        binding.btnRefresh.setOnClickListener {
            refreshContent()
        }

        binding.btnAddSubscription.setOnClickListener {
            requireView().findNavController().navigate(R.id.action_contentFragment_to_connectFragment)
        }

        binding.btnAddImage.setOnClickListener {
            showPhotoOptions()
        }

        binding.btnPost.setOnClickListener {
            submitPost(binding)
        }
    }

    private fun showPhotoOptions() {
        // For now, directly open photo picker. Could show a dialog with camera/gallery options.
        photoPicker.pickPhoto(
            onSelected = { bitmap ->
                selectedPhotos.add(bitmap)
                updatePhotoPreview()
            },
            onError = { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updatePhotoPreview() {
        val binding = _binding ?: return
        if (selectedPhotos.isEmpty()) {
            binding.listPhotoPreviews.visibility = View.GONE
        } else {
            binding.listPhotoPreviews.visibility = View.VISIBLE
            // Set up horizontal layout manager if not already set
            if (binding.listPhotoPreviews.layoutManager == null) {
                binding.listPhotoPreviews.layoutManager = LinearLayoutManager(
                    requireContext(),
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
            }
            binding.listPhotoPreviews.adapter = PhotoPreviewAdapter(selectedPhotos) { position ->
                // Remove photo on click
                selectedPhotos.removeAt(position)
                updatePhotoPreview()
            }
        }
    }

    private fun submitPost(binding: FragmentContentBinding) {
        val text = binding.txtPostText.text.toString()
        if (text.isBlank() && selectedPhotos.isEmpty()) {
            return
        }

        binding.txtPostText.text.clear()
        binding.btnPost.isEnabled = false

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val keyring = Bailiwick.getInstance().keyring

                // Store photos as blobs
                val postFiles = mutableListOf<PostFile>()
                for ((index, photo) in selectedPhotos.withIndex()) {
                    val bytes = photoPicker.compressBitmap(photo)
                    val hash = bwModel.iroh.storeBlob(bytes)
                    bwModel.network.storeFile(hash, ByteArrayInputStream(bytes))
                    Log.d(TAG, "Stored photo $index with hash: $hash, size: ${bytes.size}")
                    postFiles.add(PostFile(0, hash, "image/jpeg"))
                }

                val newPost = Post(
                    bwModel.network.me.id,
                    null,
                    Calendar.getInstance().timeInMillis,
                    null,
                    text,
                    ""
                )

                val signer = RsaSignature(keyring.publicKey, keyring.privateKey)
                newPost.sign(signer, postFiles)
                val circId = bwModel.network.circles.first().id
                bwModel.network.storePost(circId, newPost)

                // Insert PostFiles into database with the post ID
                val db = bwModel.db
                for (postFile in postFiles) {
                    val fileWithPostId = PostFile(newPost.id, postFile.blobHash, postFile.mimeType)
                    db.postFileDao().insert(fileWithPostId)
                }

                Log.i(TAG, "Saved new post with ${postFiles.size} photos")

                // Publish post to Iroh blob storage with circle key encryption
                try {
                    val cipher = try {
                        EncryptorFactory.forCircle(db.keyDao(), circId)
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "No key for circle $circId, using noop: ${e.message}")
                        NoopEncryptor()
                    }

                    val publisher = ContentPublisher(bwModel.iroh, db)
                    val postHash = publisher.publishPost(newPost, circId, cipher)

                    // Verify the blobHash was set
                    val updatedPost = db.postDao().find(newPost.id)
                    Log.i(TAG, "Published post ${newPost.id} to Iroh with hash: $postHash, blobHash in DB: ${updatedPost?.blobHash}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish post to Iroh: ${e.message}")
                }

                // Trigger manifest sync to notify peers
                GossipService.getInstance()?.publishManifest()
                Log.i(TAG, "Triggered manifest publish")
            }

            selectedPhotos.clear()
            updatePhotoPreview()
            binding.btnPost.isEnabled = true
        }
    }

    private fun setupPostList(binding: FragmentContentBinding) {
        buildAdapter(binding.listContent)

        // Observe LiveData for automatic UI updates when posts change
        bwModel.postsLive.observe(viewLifecycleOwner) { posts ->
            Log.d(TAG, "LiveData updated: ${posts.size} posts")
            applyFilters(posts)
        }
    }

    private fun displayAvatar(binding: FragmentContentBinding) {
        bwModel.viewModelScope.launch {
            val avatar = withContext(Dispatchers.Default) {
                AvatarLoader.loadAvatar(bwModel.network.me, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
            }
            binding.imgMyAvatar.setImageBitmap(avatar)

            // Allow tap on own avatar to view own profile
            binding.imgMyAvatar.setOnClickListener {
                navigateToUserProfile(bwModel.network.me.id)
            }
        }
    }

    private var adapter: Optional<PostAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView) {
        adapter = Optional.of(buildListAdapter())
        messagesList.adapter = adapter.get()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshContent() {
        // Clear all filters
        filterByUserId = null
        filterByCircleId = null
        filterPrefs.clearFilters()
        circleFilterAdapter?.setSelectedCircle(null)

        // Trigger UI update from current LiveData value
        bwModel.postsLive.value?.let { posts ->
            applyFilters(posts)
        }
    }

    private fun filterByCircle(circle: Circle?) {
        Log.d(TAG, "Filtering by circle: ${circle?.name}")
        filterByCircleId = circle?.id
        filterByUserId = null  // Clear user filter when circle filter is applied

        if (circle != null) {
            filterPrefs.setCircleFilter(circle.id)
        } else {
            filterPrefs.clearFilters()
        }

        bwModel.postsLive.value?.let { posts ->
            applyFilters(posts)
        }
    }

    private fun filterPostsByUser(user: Identity) {
        Log.d(TAG, "Filtering posts by user: ${user.name} (id=${user.id})")
        filterByUserId = user.id
        filterByCircleId = null  // Clear circle filter when user filter is applied
        circleFilterAdapter?.setSelectedCircle(null)

        filterPrefs.setPersonFilter(user.id)

        bwModel.postsLive.value?.let { posts ->
            applyFilters(posts)
        }
    }

    private fun applyFilters(posts: List<Post>) {
        var filteredPosts = posts

        // Apply circle filter
        filterByCircleId?.let { circleId ->
            bwModel.viewModelScope.launch {
                val circlePosts = withContext(Dispatchers.Default) {
                    bwModel.network.circlePosts(circleId)
                }
                val circlePostIds = circlePosts.map { it.id }.toSet()
                val filtered = posts.filter { it.id in circlePostIds }
                Log.d(TAG, "Circle filter: ${filtered.size} posts from circle $circleId")
                adapter.get().clear()
                adapter.get().addToEnd(filtered.sortedByDescending { it.timestamp })
            }
            return
        }

        // Apply user filter
        filterByUserId?.let { userId ->
            filteredPosts = posts.filter { it.authorId == userId }
            Log.d(TAG, "User filter: ${filteredPosts.size} posts from user $userId")
        }

        adapter.get().clear()
        adapter.get().addToEnd(filteredPosts.sortedByDescending { it.timestamp })
    }

    private fun buildListAdapter(): PostAdapter {
        return PostAdapter(
            getBailiwickDb(requireContext()),
            bwModel,
            requireContext(),
            ArrayList(),
            onAuthorClick = { userId ->
                navigateToUserProfile(userId)
            },
            onDeleteClick = { post ->
                deletePost(post)
            },
            currentUserId = bwModel.network.me.id
        )
    }

    private fun deletePost(post: Post) {
        // Show confirmation dialog
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_post_title)
            .setMessage(R.string.delete_post_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                performDeletePost(post)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDeletePost(post: Post) {
        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val blobHash = post.blobHash
                if (blobHash == null) {
                    Log.w(TAG, "Cannot delete post ${post.id}: no blobHash")
                    return@withContext
                }

                // Delete associated files from cache
                val files = bwModel.db.postFileDao().filesForPost(post.id)
                val blobCache = BlobCache(Bailiwick.getInstance().cacheDir)
                for (file in files) {
                    blobCache.delete(file.blobHash)
                }

                // Delete post files from database
                bwModel.db.postFileDao().deleteForPost(post.id)

                // Delete the post from database
                bwModel.db.postDao().delete(post.id)
                Log.i(TAG, "Deleted post ${post.id} locally")

                // Create and store delete action for broadcast to peers
                val deleteAction = Action.deletePostAction(blobHash)
                bwModel.db.actionDao().insert(deleteAction)

                // Trigger gossip to broadcast the delete action
                GossipService.getInstance()?.publishManifest()
            }

            // Remove from adapter
            adapter.ifPresent { it.removePost(post) }

            Toast.makeText(context, R.string.post_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToUserProfile(userId: Long) {
        val bundle = UserProfileFragment.newBundle(userId)
        requireView().findNavController().navigate(
            R.id.action_contentFragment_to_userProfileFragment,
            bundle
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ContentFragment"
    }
}
