package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.perfectlunacy.bailiwick.databinding.UserButtonBinding
import com.perfectlunacy.bailiwick.models.db.Identity

import android.R




class UserButtonAdapter(val context: Context, private val items: List<Identity>): Adapter<UserButtonViewHolder>() {
    private fun getItem(position: Int): Any {
        return items.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserButtonViewHolder {
        val itemView: View = LayoutInflater.from(context)
            .inflate(com.perfectlunacy.bailiwick.R.layout.user_button, parent, false)

        val binding = if(itemView.tag is String) {
            UserButtonBinding.bind(itemView)
        }  else {
            itemView.tag as UserButtonBinding
        }

        binding.root.tag = binding

        return UserButtonViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: UserButtonViewHolder, position: Int) {
        val user = getItem(position) as Identity
        val binding = holder.itemView.tag as UserButtonBinding
        binding.btnAvatar.setImageBitmap(user.avatar(context.filesDir.toPath()))
    }

    override fun getItemCount(): Int {
        return items.size
    }
}

class UserButtonViewHolder(view: View): RecyclerView.ViewHolder(view) {

}