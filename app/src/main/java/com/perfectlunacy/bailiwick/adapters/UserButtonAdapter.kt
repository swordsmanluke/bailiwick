package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ListAdapter
import androidx.annotation.NonNull
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.UserButtonBinding
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.UserIdentity
import com.perfectlunacy.bailiwick.models.ipfs.Identity

class UserButtonAdapter(val context: Context, private val items: List<UserIdentity>): BaseAdapter() {
    override fun getCount(): Int {
        return items.count()
    }

    override fun getItem(position: Int): Any {
        return items.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.user_button, parent, false)
        val user = getItem(position) as UserIdentity

        val binding = if(convertView == null) {
            UserButtonBinding.bind(view)
        }  else {
            view.tag as @NonNull UserButtonBinding
        }

        binding.root.tag = binding
        binding.btnAvatar.setImageBitmap(user.avatar)

        return binding.root
    }

}