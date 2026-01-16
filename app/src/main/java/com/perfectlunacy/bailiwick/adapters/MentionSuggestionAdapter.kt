package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.util.MentionParser

/**
 * Adapter for displaying @mention autocomplete suggestions.
 */
class MentionSuggestionAdapter(
    private val context: Context,
    private var allUsernames: List<String>
) : BaseAdapter(), Filterable {

    private var filteredUsernames: List<String> = emptyList()

    override fun getCount(): Int = filteredUsernames.size

    override fun getItem(position: Int): String = filteredUsernames[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = "@${filteredUsernames[position]}"

        return view
    }

    fun updateUsernames(usernames: List<String>) {
        allUsernames = usernames
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()

                val prefix = constraint?.toString() ?: ""
                val suggestions = MentionParser.getAutocompleteSuggestions(
                    prefix,
                    allUsernames,
                    limit = 5
                )

                results.values = suggestions
                results.count = suggestions.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredUsernames = (results?.values as? List<String>) ?: emptyList()
                if (filteredUsernames.isNotEmpty()) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
        }
    }
}
