package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.EmojiCount

/**
 * Adapter for displaying reaction chips below posts.
 */
class ReactionAdapter(
    private val context: Context,
    private var reactions: List<EmojiCount>,
    private val userReactions: Set<String>,  // Emojis the current user has reacted with
    private val onReactionClicked: (String, Boolean) -> Unit  // emoji, isCurrentlyReacted
) : RecyclerView.Adapter<ReactionAdapter.ReactionViewHolder>() {

    companion object {
        // Common reactions to show in picker
        val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReactionViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_reaction, parent, false)
        return ReactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReactionViewHolder, position: Int) {
        val reaction = reactions[position]
        val userReacted = userReactions.contains(reaction.emoji)
        holder.bind(reaction, userReacted)
    }

    override fun getItemCount(): Int = reactions.size

    fun updateReactions(newReactions: List<EmojiCount>, newUserReactions: Set<String>) {
        reactions = newReactions
        notifyDataSetChanged()
    }

    inner class ReactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textEmoji: TextView = itemView.findViewById(R.id.textEmoji)
        private val textCount: TextView = itemView.findViewById(R.id.textCount)

        fun bind(reaction: EmojiCount, userReacted: Boolean) {
            textEmoji.text = reaction.emoji
            textCount.text = reaction.count.toString()

            // Highlight if user has reacted
            val backgroundColor = if (userReacted) {
                ContextCompat.getColor(context, R.color.reactionBackgroundSelected)
            } else {
                ContextCompat.getColor(context, R.color.reactionBackground)
            }
            itemView.setBackgroundColor(backgroundColor)

            itemView.setOnClickListener {
                onReactionClicked(reaction.emoji, userReacted)
            }
        }
    }
}
