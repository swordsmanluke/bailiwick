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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import android.text.Editable
import android.text.TextWatcher
import com.perfectlunacy.bailiwick.adapters.CircleFilterAdapter
import com.perfectlunacy.bailiwick.adapters.MentionSuggestionsAdapter
import com.perfectlunacy.bailiwick.adapters.PhotoPreviewAdapter
import com.perfectlunacy.bailiwick.adapters.PostAdapter
import com.perfectlunacy.bailiwick.util.MentionParser
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.storage.BlobCache
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.databinding.FragmentContentBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.PostFile
import com.perfectlunacy.bailiwick.models.db.Reaction
import com.perfectlunacy.bailiwick.services.GossipService
import com.perfectlunacy.bailiwick.signatures.RsaSignature
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

    // Mention suggestions
    private var mentionSuggestionsAdapter: MentionSuggestionsAdapter? = null
    private var allUsernames: List<String> = emptyList()
    private var currentMentionStart: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoPicker = PhotoPicker(this)
        filterPrefs = FilterPreferences(requireContext())
    }

    override fun onResume() {
        super.onResume()

        // Check for circle creation result
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.get<Long>(CreateCircleFragment.RESULT_CIRCLE_ID)?.let { circleId ->
            savedStateHandle.remove<Long>(CreateCircleFragment.RESULT_CIRCLE_ID)
            onCircleCreated(circleId)
            return
        }

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

        setupHeader(binding)
        setupCircleFilter(binding)
        setupPostComposer(binding)
        setupPostList(binding)

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
                bwModel.network.circles
            }

            circleFilterAdapter = CircleFilterAdapter(
                requireContext(),
                circles,
                filterByCircleId,
                onCircleSelected = { selectedCircle ->
                    filterByCircle(selectedCircle)
                },
                onCircleLongPress = { circle ->
                    navigateToEditCircle(circle.id)
                }
            )
            binding.listCircles.adapter = circleFilterAdapter
        }

        binding.btnAddCircle.setOnClickListener {
            requireView().findNavController().navigate(R.id.action_contentFragment_to_createCircleFragment)
        }

        binding.btnAddConnection.setOnClickListener {
            requireView().findNavController().navigate(R.id.action_contentFragment_to_connectFragment)
        }

        // Observe result from EditCircleFragment
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Long>(
            EditCircleFragment.RESULT_CIRCLE_ID
        )?.observe(viewLifecycleOwner) { _ ->
            reloadCircles()
        }

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>(
            EditCircleFragment.RESULT_DELETED
        )?.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                filterByCircleId = null
                filterPrefs.clearFilters()
                reloadCircles()
            }
        }
    }

    private fun navigateToEditCircle(circleId: Long) {
        val bundle = EditCircleFragment.newBundle(circleId)
        requireView().findNavController().navigate(
            R.id.action_contentFragment_to_editCircleFragment,
            bundle
        )
    }

    private fun reloadCircles() {
        bwModel.viewModelScope.launch {
            val circles = withContext(Dispatchers.Default) {
                bwModel.network.circles
            }
            circleFilterAdapter?.updateCircles(circles)
        }
    }

    private fun setupHeader(binding: FragmentContentBinding) {
        // Load and display user info in header
        bwModel.viewModelScope.launch {
            val (avatar, userName) = withContext(Dispatchers.Default) {
                val me = bwModel.network.me
                val loadedAvatar = AvatarLoader.loadAvatar(me, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
                Pair(loadedAvatar, me.name)
            }
            binding.imgHeaderAvatar.setImageBitmap(avatar)
            binding.txtUserName.text = userName
        }

        // Tap on user identity to edit
        binding.layoutUserIdentity.setOnClickListener {
            navigateToEditIdentity()
        }
    }

    private fun navigateToEditIdentity() {
        requireView().findNavController().navigate(R.id.action_contentFragment_to_editIdentityFragment)
    }

    private fun setupPostComposer(binding: FragmentContentBinding) {
        // Display user avatar in post composer
        displayAvatar(binding)

        binding.btnAddImage.setOnClickListener {
            showPhotoOptions()
        }

        binding.btnPost.setOnClickListener {
            submitPost(binding)
        }

        // Setup mention suggestions
        setupMentionSuggestions(binding)
    }

    private fun setupMentionSuggestions(binding: FragmentContentBinding) {
        // Load usernames for autocomplete
        bwModel.viewModelScope.launch {
            allUsernames = withContext(Dispatchers.Default) {
                bwModel.getUsers().map { it.name }
            }
        }

        // Setup suggestions RecyclerView
        binding.listMentionSuggestions.layoutManager = LinearLayoutManager(requireContext())
        mentionSuggestionsAdapter = MentionSuggestionsAdapter { selectedUsername ->
            insertMention(binding, selectedUsername)
        }
        binding.listMentionSuggestions.adapter = mentionSuggestionsAdapter

        // Add text watcher to detect mentions
        binding.txtPostText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val cursorPosition = binding.txtPostText.selectionStart

                val mentionContext = MentionParser.getMentionContext(text, cursorPosition)

                if (mentionContext != null) {
                    val (atIndex, prefix) = mentionContext
                    currentMentionStart = atIndex
                    val suggestions = MentionParser.getAutocompleteSuggestions(prefix, allUsernames)

                    if (suggestions.isNotEmpty()) {
                        mentionSuggestionsAdapter?.updateSuggestions(suggestions)
                        binding.listMentionSuggestions.visibility = View.VISIBLE
                    } else {
                        binding.listMentionSuggestions.visibility = View.GONE
                    }
                } else {
                    currentMentionStart = -1
                    binding.listMentionSuggestions.visibility = View.GONE
                }
            }
        })
    }

    private fun insertMention(binding: FragmentContentBinding, username: String) {
        if (currentMentionStart == -1) return

        val text = binding.txtPostText.text ?: return
        val cursorPosition = binding.txtPostText.selectionStart

        // Replace from @ to cursor with @username + space
        val newText = StringBuilder(text)
            .replace(currentMentionStart, cursorPosition, "@$username ")
            .toString()

        binding.txtPostText.setText(newText)
        binding.txtPostText.setSelection(currentMentionStart + username.length + 2)

        // Hide suggestions
        binding.listMentionSuggestions.visibility = View.GONE
        currentMentionStart = -1
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
                // Post to the selected circle, or default to first circle if none selected
                val circId = filterByCircleId ?: bwModel.network.circles.first().id
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
                    val cipher = EncryptorFactory.forCircle(db.keyDao(), circId)

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
        // Load current user info on background thread, then build adapter
        bwModel.viewModelScope.launch {
            val (userId, userNodeId) = withContext(Dispatchers.Default) {
                val me = bwModel.network.me
                Pair(me.id, me.owner)
            }
            buildAdapter(binding.listContent, userId, userNodeId)

            // Observe LiveData AFTER adapter is built to avoid race condition
            bwModel.postsLive.observe(viewLifecycleOwner) { posts ->
                Log.d(TAG, "LiveData updated: ${posts.size} posts")
                applyFilters(posts)
            }

            // Observe reactions updates to refresh visible posts
            Bailiwick.reactionsUpdated.observe(viewLifecycleOwner) { _ ->
                Log.d(TAG, "Reactions updated, refreshing adapter")
                adapter.ifPresent { it.notifyDataSetChanged() }
            }
        }
    }

    private fun displayAvatar(binding: FragmentContentBinding) {
        bwModel.viewModelScope.launch {
            val avatar = withContext(Dispatchers.Default) {
                val me = bwModel.network.me
                AvatarLoader.loadAvatar(me, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
            }
            binding.imgMyAvatar.setImageBitmap(avatar)

            // Tap on own avatar in post composer to edit identity
            binding.imgMyAvatar.setOnClickListener {
                navigateToEditIdentity()
            }
        }
    }

    private var adapter: Optional<PostAdapter> = Optional.empty()
    private fun buildAdapter(messagesList: ListView, currentUserId: Long, currentUserNodeId: String) {
        adapter = Optional.of(buildListAdapter(currentUserId, currentUserNodeId))
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

    private fun onCircleCreated(circleId: Long) {
        bwModel.viewModelScope.launch {
            val circles = withContext(Dispatchers.Default) {
                bwModel.network.circles
            }
            circleFilterAdapter?.updateCircles(circles)

            // Find and select the new circle
            val newCircle = circles.find { it.id == circleId }
            if (newCircle != null) {
                filterByCircle(newCircle)
                circleFilterAdapter?.setSelectedCircle(circleId)
            }
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

        if (!adapter.isPresent) {
            Log.d(TAG, "Adapter not yet initialized, skipping filter update")
            return
        }
        adapter.get().clear()
        adapter.get().addToEnd(filteredPosts.sortedByDescending { it.timestamp })
    }

    private fun buildListAdapter(currentUserId: Long, currentUserNodeId: String): PostAdapter {
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
            onMentionClick = { username ->
                navigateToUserByUsername(username)
            },
            onCommentClick = { post ->
                navigateToComments(post)
            },
            onReactionAdded = { post, emoji ->
                addReaction(post, emoji)
            },
            onReactionRemoved = { post, emoji ->
                removeReaction(post, emoji)
            },
            onPhotoClick = { photoHashes, startPosition ->
                navigateToPhotoViewer(photoHashes, startPosition)
            },
            currentUserId = currentUserId,
            currentUserNodeId = currentUserNodeId
        )
    }

    private fun navigateToUserByUsername(username: String) {
        bwModel.viewModelScope.launch {
            val userId = withContext(Dispatchers.Default) {
                // Find user by username (case-insensitive)
                bwModel.getUsers()
                    .find { it.name.equals(username, ignoreCase = true) }
                    ?.id
            }

            if (userId != null) {
                navigateToUserProfile(userId)
            } else {
                Toast.makeText(context, getString(R.string.user_not_found, username), Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun navigateToComments(post: Post) {
        val bundle = CommentsFragment.newBundle(post.id, post.blobHash)
        requireView().findNavController().navigate(
            R.id.action_contentFragment_to_commentsFragment,
            bundle
        )
    }

    private fun navigateToPhotoViewer(photoHashes: List<String>, startPosition: Int) {
        val bundle = PhotoViewerFragment.newBundle(photoHashes, startPosition)
        requireView().findNavController().navigate(
            R.id.action_contentFragment_to_photoViewerFragment,
            bundle
        )
    }

    private fun addReaction(post: Post, emoji: String) {
        val postHash = post.blobHash
        if (postHash == null) {
            Toast.makeText(context, "Cannot react to unsaved post", Toast.LENGTH_SHORT).show()
            return
        }

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val me = bwModel.network.me
                val db = bwModel.db

                // Check if already reacted with this emoji
                val existing = db.reactionDao().findReaction(postHash, me.owner, emoji)
                if (existing != null) {
                    Log.d(TAG, "Already reacted with $emoji")
                    return@withContext
                }

                val reaction = Reaction(
                    postHash = postHash,
                    authorNodeId = me.owner,
                    emoji = emoji,
                    timestamp = Calendar.getInstance().timeInMillis,
                    signature = "",  // TODO: Sign reaction
                    blobHash = null
                )

                db.reactionDao().insert(reaction)
                Log.i(TAG, "Added reaction $emoji to post $postHash")

                // Find the post's circle to get the correct cipher
                val circleId = db.circlePostDao().circlesForPost(post.id).firstOrNull()
                    ?: bwModel.network.circles.first().id

                // Publish reaction to Iroh blob storage
                try {
                    val cipher = EncryptorFactory.forCircle(db.keyDao(), circleId)

                    val publisher = ContentPublisher(bwModel.iroh, db)
                    val reactionHash = publisher.publishReaction(reaction, cipher)
                    Log.i(TAG, "Published reaction ${reaction.id} to Iroh with hash: $reactionHash")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish reaction to Iroh: ${e.message}")
                }

                // Trigger manifest sync to notify peers
                GossipService.getInstance()?.publishManifest()
                Log.i(TAG, "Triggered manifest publish for reaction")
            }

            // Refresh the adapter to show new reaction
            bwModel.postsLive.value?.let { posts ->
                applyFilters(posts)
            }
        }
    }

    private fun removeReaction(post: Post, emoji: String) {
        val postHash = post.blobHash
        if (postHash == null) {
            return
        }

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val me = bwModel.network.me
                val db = bwModel.db

                // Find the reaction before deleting
                val reaction = db.reactionDao().findReaction(postHash, me.owner, emoji)
                if (reaction == null) {
                    Log.d(TAG, "Reaction not found to remove")
                    return@withContext
                }

                // Find the post's circle to get the correct cipher
                val circleId = db.circlePostDao().circlesForPost(post.id).firstOrNull()
                    ?: bwModel.network.circles.first().id

                // Publish reaction removal to Iroh blob storage
                try {
                    val cipher = EncryptorFactory.forCircle(db.keyDao(), circleId)

                    val publisher = ContentPublisher(bwModel.iroh, db)
                    publisher.publishReaction(reaction, cipher, isRemoval = true)
                    Log.i(TAG, "Published reaction removal for $emoji on $postHash")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish reaction removal to Iroh: ${e.message}")
                }

                // Delete locally
                db.reactionDao().deleteReaction(postHash, me.owner, emoji)
                Log.i(TAG, "Removed reaction $emoji from post $postHash")

                // Trigger manifest sync to notify peers
                GossipService.getInstance()?.publishManifest()
                Log.i(TAG, "Triggered manifest publish for reaction removal")
            }

            // Refresh the adapter to show updated reactions
            bwModel.postsLive.value?.let { posts ->
                applyFilters(posts)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ContentFragment"
    }
}
