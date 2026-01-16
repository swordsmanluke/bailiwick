package com.perfectlunacy.bailiwick.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R

/**
 * RecyclerView adapter for displaying @mention autocomplete suggestions.
 */
class MentionSuggestionsAdapter(
    private val onSuggestionClick: (String) -> Unit
) : RecyclerView.Adapter<MentionSuggestionsAdapter.SuggestionViewHolder>() {

    private var suggestions: List<String> = emptyList()

    fun updateSuggestions(newSuggestions: List<String>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mention_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.txt_suggestion)

        fun bind(username: String) {
            textView.text = "@$username"
            itemView.setOnClickListener {
                onSuggestionClick(username)
            }
        }
    }
}
