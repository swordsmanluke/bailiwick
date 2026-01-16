package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.util.AvatarLoader
import com.perfectlunacy.bailiwick.util.PostFormatter
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RecyclerView adapter for displaying comments on a post.
 * Supports nested replies with visual indentation.
 */
class CommentAdapter(
    private val context: Context,
    private val db: BailiwickDatabase,
    private val bwModel: BailiwickViewModel,
    private val onReplyClick: ((Post) -> Unit)? = null,
    private val onAuthorClick: ((Long) -> Unit)? = null
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val comments = mutableListOf<CommentItem>()
    private val indentWidth = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
    private val maxIndentLevel = 4  // Maximum visual nesting depth

    /**
     * Wrapper class to hold comment post with its nesting depth.
     */
    data class CommentItem(
        val post: Post,
        val depth: Int
    )

    fun setComments(posts: List<Post>, parentHash: String?) {
        comments.clear()
        // Build a flat list with depth for display
        addCommentsRecursively(posts, parentHash, 0)
        notifyDataSetChanged()
    }

    private fun addCommentsRecursively(allPosts: List<Post>, parentHash: String?, depth: Int) {
        val directReplies = allPosts.filter { it.parentHash == parentHash }
            .sortedBy { it.timestamp }

        for (reply in directReplies) {
            comments.add(CommentItem(reply, minOf(depth, maxIndentLevel)))
            // Add nested replies
            reply.blobHash?.let { hash ->
                addCommentsRecursively(allPosts, hash, depth + 1)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val item = comments[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.img_avatar)
        private val txtAuthor: TextView = itemView.findViewById(R.id.txt_author)
        private val txtTimestamp: TextView = itemView.findViewById(R.id.txt_timestamp)
        private val txtComment: TextView = itemView.findViewById(R.id.txt_comment)
        private val btnReply: TextView = itemView.findViewById(R.id.btn_reply)
        private val txtReplyCount: TextView = itemView.findViewById(R.id.txt_reply_count)
        private val indentSpacer: View = itemView.findViewById(R.id.indent_spacer)

        fun bind(item: CommentItem) {
            val post = item.post

            // Set indentation
            if (item.depth > 0) {
                indentSpacer.visibility = View.VISIBLE
                val params = indentSpacer.layoutParams
                params.width = indentWidth * item.depth
                indentSpacer.layoutParams = params
            } else {
                indentSpacer.visibility = View.GONE
            }

            // Set comment text
            txtComment.text = post.text

            // Set timestamp
            txtTimestamp.text = PostFormatter.formatRelativeTime(post.timestamp)

            // Load author info asynchronously
            bwModel.viewModelScope.launch {
                val (author, avatar, replyCount) = withContext(Dispatchers.Default) {
                    val author = db.identityDao().find(post.authorId)
                    val avatar = AvatarLoader.loadAvatar(author, context.filesDir.toPath())
                        ?: BitmapFactory.decodeStream(context.assets.open("avatar.png"))
                    val replyCount = post.blobHash?.let { db.postDao().replyCount(it) } ?: 0
                    Triple(author, avatar, replyCount)
                }

                txtAuthor.text = author.name
                imgAvatar.setImageBitmap(avatar)

                // Show reply count if there are replies
                if (replyCount > 0) {
                    txtReplyCount.visibility = View.VISIBLE
                    txtReplyCount.text = if (replyCount == 1) {
                        context.getString(R.string.reply_count_one)
                    } else {
                        context.getString(R.string.replies_count, replyCount)
                    }
                } else {
                    txtReplyCount.visibility = View.GONE
                }

                // Set click listeners
                onAuthorClick?.let { handler ->
                    val clickListener = View.OnClickListener { handler(author.id) }
                    imgAvatar.setOnClickListener(clickListener)
                    txtAuthor.setOnClickListener(clickListener)
                }
            }

            // Reply button
            btnReply.setOnClickListener {
                onReplyClick?.invoke(post)
            }
        }
    }
}
