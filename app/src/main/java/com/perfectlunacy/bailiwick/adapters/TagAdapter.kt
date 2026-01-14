package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R

/**
 * Adapter for displaying tag chips below posts.
 */
class TagAdapter(
    private val context: Context,
    private var tags: List<String>,
    private val onTagClicked: (String) -> Unit
) : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(tags[position])
    }

    override fun getItemCount(): Int = tags.size

    fun updateTags(newTags: List<String>) {
        tags = newTags
        notifyDataSetChanged()
    }

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTag: TextView = itemView.findViewById(R.id.textTag)

        fun bind(tag: String) {
            textTag.text = "#$tag"

            itemView.setOnClickListener {
                onTagClicked(tag)
            }
        }
    }
}
