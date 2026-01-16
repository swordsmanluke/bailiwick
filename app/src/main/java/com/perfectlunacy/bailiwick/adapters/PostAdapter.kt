package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.PopupWindow
import android.widget.TextView
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.PostBinding
import com.perfectlunacy.bailiwick.models.db.EmojiCount
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.Reaction
import com.perfectlunacy.bailiwick.storage.BlobCache
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.util.AvatarLoader
import com.perfectlunacy.bailiwick.util.MentionParser
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class PostAdapter(
    private val db: BailiwickDatabase,
    private val bwModel: BailiwickViewModel,
    private val context: Context,
    private val list: ArrayList<Post>,
    private val onAuthorClick: ((Long) -> Unit)? = null,
    private val onDeleteClick: ((Post) -> Unit)? = null,
    private val onMentionClick: ((String) -> Unit)? = null,
    private val onCommentClick: ((Post) -> Unit)? = null,
    private val onReactionAdded: ((Post, String) -> Unit)? = null,
    private val onReactionRemoved: ((Post, String) -> Unit)? = null,
    private val currentUserId: Long = -1,
    private val currentUserNodeId: NodeId = ""
): BaseAdapter() {

    // Cache of known usernames for mention highlighting
    private var knownUsernames: Set<String> = emptySet()

    init {
        // Load known usernames in background
        bwModel.viewModelScope.launch {
            knownUsernames = withContext(Dispatchers.Default) {
                bwModel.getUsers().map { it.name.lowercase() }.toSet()
            }
        }
    }
    override fun getCount(): Int {
        return list.count()
    }

    override fun getItem(position: Int): Any {
        return list[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, itemView: View?, parent: ViewGroup?): View {
        val view = itemView ?: LayoutInflater.from(context).inflate(R.layout.post, parent, false)
        val post = try { getItem(position) } catch(_: IndexOutOfBoundsException) { null } as Post?

        val binding = if (itemView == null) {
            PostBinding.bind(view)
        } else {
            itemView.tag as PostBinding
        }

        binding.post = post
        binding.root.tag = binding

        bwModel.viewModelScope.launch {
            // Don't blow up if we failed to find an item
            if(post == null) { return@launch }

            // Load all data on background thread
            val postData = withContext(Dispatchers.Default) {
                val author = db.identityDao().find(post.authorId)
                val avatar = AvatarLoader.loadAvatar(author, context.filesDir.toPath())
                    ?: BitmapFactory.decodeStream(context.assets.open("avatar.png"))

                // Load post image if available
                val postBitmap = try {
                    val files = db.postFileDao().filesForPost(post.id)
                    Log.d(TAG, "Post ${post.id} has ${files.size} files")
                    val imageFile = files.firstOrNull { it.mimeType.startsWith("image") }
                    if (imageFile != null && Bailiwick.isInitialized()) {
                        Log.d(TAG, "Found image file: ${imageFile.blobHash}, mimeType: ${imageFile.mimeType}")
                        val blobCache = BlobCache(Bailiwick.getInstance().cacheDir)
                        val imageData = blobCache.get(imageFile.blobHash)
                        Log.d(TAG, "Blob cache returned: ${imageData?.size ?: "null"} bytes")
                        imageData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } else {
                        if (imageFile == null) Log.d(TAG, "No image file found for post ${post.id}")
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load post image", e)
                    null
                }

                // Get comment count if the post has a blob hash
                val commentCount = post.blobHash?.let {
                    db.postDao().replyCount(it)
                } ?: 0

                // Get reactions for this post
                val reactions = post.blobHash?.let {
                    db.reactionDao().reactionCountsForPost(it)
                } ?: emptyList()

                // Get user's reactions
                val userReactions = post.blobHash?.let {
                    db.reactionDao().myReactionsForPost(it, currentUserNodeId)
                        .map { r -> r.emoji }.toSet()
                } ?: emptySet()

                PostViewData(author, avatar, postBitmap, commentCount, reactions, userReactions)
            }

            val author = postData.author
            val avatar = postData.avatar
            val postImage = postData.postImage
            val commentCount = postData.commentCount
            val reactions = postData.reactions
            val userReactions = postData.userReactions

            // Back on main thread - update UI
            binding.avatar.setImageBitmap(avatar)
            binding.txtAuthor.text = author.name

            // Set up click listeners for author navigation
            onAuthorClick?.let { clickHandler ->
                val clickListener = View.OnClickListener {
                    clickHandler(author.id)
                }
                binding.avatar.setOnClickListener(clickListener)
                binding.txtAuthor.setOnClickListener(clickListener)
            }

            // Show delete button only for own posts
            val isOwnPost = post.authorId == currentUserId
            binding.btnDelete.visibility = if (isOwnPost && onDeleteClick != null) View.VISIBLE else View.GONE
            if (isOwnPost) {
                binding.btnDelete.setOnClickListener {
                    onDeleteClick?.invoke(post)
                }
            }

            // Display post image if available
            val imgContent = binding.imgSocialContent
            if (postImage != null) {
                imgContent.setImageBitmap(postImage)
                imgContent.visibility = View.VISIBLE
            } else {
                imgContent.visibility = View.GONE
            }

            // Apply mention highlighting to post text
            val postText = post.text
            if (!postText.isNullOrEmpty() && onMentionClick != null) {
                val spannableText = MentionParser.createSpannableWithMentions(
                    postText,
                    knownUsernames
                ) { username ->
                    onMentionClick.invoke(username)
                }
                binding.txtSocialContent.text = spannableText
                binding.txtSocialContent.movementMethod = LinkMovementMethod.getInstance()
            }

            // Set up comment button with count
            val commentText = if (commentCount > 0) {
                context.getString(R.string.comment_count, commentCount)
            } else {
                context.getString(R.string.comment)
            }
            binding.btnComment.text = commentText
            binding.btnComment.setOnClickListener {
                onCommentClick?.invoke(post)
            }

            // Set up reactions display
            if (reactions.isNotEmpty()) {
                binding.listReactions.visibility = View.VISIBLE
                binding.listReactions.layoutManager = LinearLayoutManager(
                    context, LinearLayoutManager.HORIZONTAL, false
                )
                binding.listReactions.adapter = ReactionAdapter(
                    context,
                    reactions,
                    userReactions
                ) { emoji, isCurrentlyReacted ->
                    if (isCurrentlyReacted) {
                        onReactionRemoved?.invoke(post, emoji)
                    } else {
                        onReactionAdded?.invoke(post, emoji)
                    }
                }
            } else {
                binding.listReactions.visibility = View.GONE
            }

            // Set up Emote button to show reaction picker
            binding.btnLike.setOnClickListener { anchorView ->
                showReactionPicker(anchorView, post, userReactions)
            }

            notifyDataSetChanged()
        }

        return binding.root
    }

    fun clear() {
        list.clear()
    }

    fun removePost(post: Post) {
        list.remove(post)
        MainScope().launch { notifyDataSetChanged() }
    }

    fun addToEnd(posts: List<Post>) {
        Log.d(TAG, "Adding ${posts.count()} posts with text: ${posts.map{it.text}}")
        val tempPosts: MutableSet<Post> = mutableSetOf()
        tempPosts.addAll(list)
        tempPosts.addAll(posts)
        list.clear()
        list.addAll(tempPosts)
        list.sortByDescending { it.timestamp ?: System.currentTimeMillis() }
        MainScope().launch { notifyDataSetChanged() }
    }

    /**
     * Shows a popup with common emoji reactions.
     */
    private fun showReactionPicker(anchorView: View, post: Post, userReactions: Set<String>) {
        val popupView = LayoutInflater.from(context)
            .inflate(R.layout.layout_reaction_picker, null)

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popup.elevation = 8f

        // Set up click listeners for each emoji
        val emojis = listOf(
            R.id.emoji_thumbsup to "👍",
            R.id.emoji_heart to "❤️",
            R.id.emoji_laugh to "😂",
            R.id.emoji_wow to "😮",
            R.id.emoji_sad to "😢",
            R.id.emoji_fire to "🔥"
        )

        for ((viewId, emoji) in emojis) {
            popupView.findViewById<TextView>(viewId)?.setOnClickListener {
                popup.dismiss()
                val hasReaction = userReactions.contains(emoji)
                if (hasReaction) {
                    onReactionRemoved?.invoke(post, emoji)
                } else {
                    onReactionAdded?.invoke(post, emoji)
                }
            }
        }

        // Show popup above the button
        popup.showAsDropDown(anchorView, 0, -anchorView.height * 2, Gravity.CENTER_HORIZONTAL)
    }

    companion object {
        const val TAG = "PostAdapter"
    }

    /**
     * Data class to hold post view data loaded on background thread.
     */
    private data class PostViewData(
        val author: com.perfectlunacy.bailiwick.models.db.Identity,
        val avatar: android.graphics.Bitmap,
        val postImage: android.graphics.Bitmap?,
        val commentCount: Int,
        val reactions: List<EmojiCount>,
        val userReactions: Set<String>
    )
}