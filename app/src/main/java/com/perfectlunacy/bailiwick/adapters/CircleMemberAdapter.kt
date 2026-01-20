package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Identity

/**
 * Adapter for displaying circle members with remove button.
 */
class CircleMemberAdapter(
    private val context: Context,
    private val onRemoveClick: (Identity) -> Unit,
    private val onMemberClick: ((Identity) -> Unit)? = null
) : RecyclerView.Adapter<CircleMemberAdapter.MemberViewHolder>() {

    private val members = mutableListOf<MemberItem>()

    data class MemberItem(
        val identity: Identity,
        var avatar: Bitmap? = null,
        var isRemoved: Boolean = false
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_circle_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val item = members[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = members.size

    fun setMembers(newMembers: List<MemberItem>) {
        members.clear()
        members.addAll(newMembers)
        notifyDataSetChanged()
    }

    fun addMember(item: MemberItem) {
        members.add(item)
        notifyItemInserted(members.size - 1)
    }

    fun markRemoved(identityId: Long) {
        val index = members.indexOfFirst { it.identity.id == identityId }
        if (index >= 0) {
            members[index].isRemoved = true
            notifyItemChanged(index)
        }
    }

    fun getActiveMembers(): List<Identity> = members.filter { !it.isRemoved }.map { it.identity }

    fun getRemovedMembers(): List<Identity> = members.filter { it.isRemoved }.map { it.identity }

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.img_avatar)
        private val txtName: TextView = itemView.findViewById(R.id.txt_name)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btn_remove)

        fun bind(item: MemberItem) {
            txtName.text = item.identity.name

            if (item.avatar != null) {
                imgAvatar.setImageBitmap(item.avatar)
            } else {
                imgAvatar.setImageResource(R.drawable.avatar)
            }

            // Visual indicator for removed state
            itemView.alpha = if (item.isRemoved) 0.5f else 1.0f

            btnRemove.setOnClickListener {
                onRemoveClick(item.identity)
            }

            // Click on row to view contact details
            itemView.setOnClickListener {
                onMemberClick?.invoke(item.identity)
            }
        }
    }
}
