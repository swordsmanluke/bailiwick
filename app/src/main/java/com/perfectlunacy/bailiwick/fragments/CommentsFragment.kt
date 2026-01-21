package com.perfectlunacy.bailiwick.fragments

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.CommentAdapter
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.databinding.FragmentCommentsBinding
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.services.GossipService
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.signatures.RsaSignature
import com.perfectlunacy.bailiwick.util.AvatarLoader
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Fragment for displaying and composing comments on a post.
 */
class CommentsFragment : BailiwickFragment() {

    companion object {
        private const val TAG = "CommentsFragment"
        const val ARG_POST_ID = "post_id"
        const val ARG_POST_HASH = "post_hash"

        fun newBundle(postId: Long, postHash: BlobHash?): Bundle {
            return Bundle().apply {
                putLong(ARG_POST_ID, postId)
                postHash?.let { putString(ARG_POST_HASH, it) }
            }
        }
    }

    private var _binding: FragmentCommentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var commentAdapter: CommentAdapter
    private var postId: Long = -1
    private var postHash: BlobHash? = null
    private var replyingTo: Post? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postId = arguments?.getLong(ARG_POST_ID, -1) ?: -1
        postHash = arguments?.getString(ARG_POST_HASH)

        if (postId == -1L) {
            findNavController().popBackStack()
            return
        }

        setupUI()
        loadOriginalPost()
        loadComments()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Comment adapter
        commentAdapter = CommentAdapter(
            context = requireContext(),
            db = Bailiwick.getInstance().db,
            bwModel = bwModel,
            onReplyClick = { post -> setReplyingTo(post) },
            onAuthorClick = { userId -> navigateToUserProfile(userId) }
        )

        binding.listComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
        }

        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadComments()
        }

        // Send comment button
        binding.btnSendComment.setOnClickListener {
            sendComment()
        }

        // Load my avatar
        loadMyAvatar()
    }

    private fun loadMyAvatar() {
        bwModel.viewModelScope.launch {
            val avatar = withContext(Dispatchers.Default) {
                val myIdentity = bwModel.network.me
                AvatarLoader.loadAvatar(myIdentity, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
            }
            binding.imgMyAvatar.setImageBitmap(avatar)
        }
    }

    private fun loadOriginalPost() {
        bwModel.viewModelScope.launch {
            val (post, author, avatar) = withContext(Dispatchers.Default) {
                val db = Bailiwick.getInstance().db
                val post = db.postDao().find(postId)
                val author = db.identityDao().find(post.authorId)
                val avatar = AvatarLoader.loadAvatar(author, requireContext().filesDir.toPath())
                    ?: BitmapFactory.decodeStream(requireContext().assets.open("avatar.png"))
                Triple(post, author, avatar)
            }

            binding.txtAuthorName.text = author.name
            binding.txtPostText.text = post.text
            binding.imgAuthorAvatar.setImageBitmap(avatar)

            // Click on author navigates to profile
            val clickListener = View.OnClickListener { navigateToUserProfile(author.id) }
            binding.imgAuthorAvatar.setOnClickListener(clickListener)
            binding.txtAuthorName.setOnClickListener(clickListener)
        }
    }

    private fun loadComments() {
        bwModel.viewModelScope.launch {
            val comments = withContext(Dispatchers.Default) {
                val db = Bailiwick.getInstance().db
                // Get all posts that are replies to this post or any of its children
                postHash?.let { hash ->
                    val allReplies = mutableListOf<Post>()
                    collectRepliesRecursively(hash, allReplies)
                    allReplies
                } ?: emptyList()
            }

            commentAdapter.setComments(comments, postHash)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun collectRepliesRecursively(parentHash: BlobHash, result: MutableList<Post>) {
        val db = Bailiwick.getInstance().db
        val directReplies = db.postDao().replies(parentHash)
        result.addAll(directReplies)
        for (reply in directReplies) {
            reply.blobHash?.let { collectRepliesRecursively(it, result) }
        }
    }

    private fun setReplyingTo(post: Post) {
        replyingTo = post
        bwModel.viewModelScope.launch {
            val authorName = withContext(Dispatchers.Default) {
                Bailiwick.getInstance().db.identityDao().find(post.authorId).name
            }
            binding.txtCommentInput.hint = getString(R.string.replying_to, authorName)
            binding.txtCommentInput.requestFocus()
        }
    }

    private fun sendComment() {
        val text = binding.txtCommentInput.text.toString().trim()
        if (text.isEmpty()) return

        val parentHash = replyingTo?.blobHash ?: postHash
        if (parentHash == null) {
            Toast.makeText(context, "Cannot add comment to unsaved post", Toast.LENGTH_SHORT).show()
            return
        }

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = Bailiwick.getInstance().db
                val myIdentity = bwModel.network.me

                val comment = Post(
                    authorId = myIdentity.id,
                    blobHash = null,  // Will be set when published
                    timestamp = Calendar.getInstance().timeInMillis,
                    parentHash = parentHash,
                    text = text,
                    signature = ""
                )

                // Sign the comment
                val keyring = Bailiwick.getInstance().keyring
                val signer = RsaSignature(keyring.publicKey, keyring.privateKey)
                comment.sign(signer, emptyList())

                // Find the parent post to get its circles
                val parentPost = db.postDao().findByHash(parentHash)
                val circleId = if (parentPost != null) {
                    // Get circles the parent post belongs to
                    val parentCircles = db.circlePostDao().circlesForPost(parentPost.id)
                    // Use the first non-"everyone" circle, or fall back to first circle
                    val everyoneCircle = bwModel.network.circles.find { it.name == "everyone" }
                    parentCircles.firstOrNull { it != everyoneCircle?.id }
                        ?: parentCircles.firstOrNull()
                        ?: bwModel.network.circles.first().id
                } else {
                    // Fallback: use first circle (shouldn't happen normally)
                    bwModel.network.circles.first().id
                }

                // Use storePost to properly create CirclePost associations
                bwModel.network.storePost(circleId, comment)

                // Publish comment to Iroh blob storage with circle key encryption
                try {
                    val cipher = EncryptorFactory.forCircle(db.keyDao(), circleId)

                    val publisher = ContentPublisher(bwModel.iroh, db)
                    val commentHash = publisher.publishPost(comment, circleId, cipher)
                    Log.i(TAG, "Published comment ${comment.id} to Iroh with hash: $commentHash")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish comment to Iroh: ${e.message}")
                }

                // Trigger manifest sync to notify peers
                GossipService.getInstance()?.publishManifest()
                Log.i(TAG, "Triggered manifest publish for comment")
            }

            // Clear input and refresh
            binding.txtCommentInput.text?.clear()
            binding.txtCommentInput.hint = getString(R.string.write_comment)
            replyingTo = null
            Toast.makeText(context, R.string.comment_added, Toast.LENGTH_SHORT).show()
            loadComments()
        }
    }

    private fun navigateToUserProfile(userId: Long) {
        val bundle = UserProfileFragment.newBundle(userId)
        findNavController().navigate(R.id.action_commentsFragment_to_userProfileFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
