package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Identity

/**
 * Adapter for displaying a horizontal carousel of contact identities (avatars).
 * Allows selection of an identity to view its associated name.
 */
class ContactIdentityAdapter(
    private val context: Context,
    private var identities: List<Pair<Identity, Bitmap>>,
    private var selectedIndex: Int = 0,
    private val onIdentitySelected: (Identity, Int) -> Unit
) : RecyclerView.Adapter<ContactIdentityAdapter.IdentityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IdentityViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_contact_identity, parent, false)
        return IdentityViewHolder(view)
    }

    override fun onBindViewHolder(holder: IdentityViewHolder, position: Int) {
        val (identity, avatar) = identities[position]
        val isSelected = position == selectedIndex
        holder.bind(avatar, isSelected) {
            val oldSelected = selectedIndex
            selectedIndex = position
            notifyItemChanged(oldSelected)
            notifyItemChanged(position)
            onIdentitySelected(identity, position)
        }
    }

    override fun getItemCount(): Int = identities.size

    fun setSelectedIndex(index: Int) {
        if (index != selectedIndex && index in identities.indices) {
            val oldSelected = selectedIndex
            selectedIndex = index
            notifyItemChanged(oldSelected)
            notifyItemChanged(index)
        }
    }

    inner class IdentityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.img_avatar)
        private val viewSelectionRing: View = itemView.findViewById(R.id.view_selection_ring)

        fun bind(avatar: Bitmap, isSelected: Boolean, onClick: () -> Unit) {
            imgAvatar.setImageBitmap(avatar)
            viewSelectionRing.visibility = if (isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onClick()
            }
        }
    }
}
