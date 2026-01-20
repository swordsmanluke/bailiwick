package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Identity

/**
 * Adapter for displaying contact search suggestions.
 */
class ContactSearchAdapter(
    private val context: Context,
    private val onContactClick: (Identity) -> Unit
) : RecyclerView.Adapter<ContactSearchAdapter.SuggestionViewHolder>() {

    private val suggestions = mutableListOf<SuggestionItem>()

    data class SuggestionItem(
        val identity: Identity,
        var avatar: Bitmap? = null
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_contact_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val item = suggestions[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = suggestions.size

    fun updateSuggestions(newSuggestions: List<SuggestionItem>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    fun clear() {
        suggestions.clear()
        notifyDataSetChanged()
    }

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.img_avatar)
        private val txtName: TextView = itemView.findViewById(R.id.txt_name)

        fun bind(item: SuggestionItem) {
            txtName.text = item.identity.name

            if (item.avatar != null) {
                imgAvatar.setImageBitmap(item.avatar)
            } else {
                imgAvatar.setImageResource(R.drawable.avatar)
            }

            itemView.setOnClickListener {
                onContactClick(item.identity)
            }
        }
    }
}
