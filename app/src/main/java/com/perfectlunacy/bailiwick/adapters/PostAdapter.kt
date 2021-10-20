package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.PostBinding
import com.perfectlunacy.bailiwick.models.ipfs.Post
import android.os.Looper

class PostAdapter(private val context: Context, private val list: ArrayList<Post>): BaseAdapter() {
    override fun getCount(): Int {
        return list.count()
    }

    override fun getItem(position: Int): Any {
        return list[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, itemView: View?, parent: ViewGroup?): View {
        val view = itemView ?: LayoutInflater.from(context).inflate(R.layout.post, parent, false)
        val post = getItem(position) as Post

        val binding = if (itemView == null) {
            PostBinding.bind(view)
        } else {
            itemView.tag as PostBinding
        }
        binding.post = post
        binding.root.tag = binding

        // TODO: Examine Files and add images if necessary
        // for f in post.files.select{f -> f.is_image}
        //   img_content.setImageDrawable(Drawable.createFromPath(post.imageUrl()))
        val img_content = binding.imgSocialContent
        img_content.visibility = View.GONE

        notifyDataSetChanged()
        return binding.root
    }

    fun clear() {
        list.clear()
    }

    fun addToEnd(posts: List<Post>) {
        Log.i(TAG, "Adding ${posts.count()} posts with text: ${posts.map{it.text}}")
        list.addAll(posts)
        list.sortByDescending { it.timestamp }
        Handler(Looper.getMainLooper()).post(Runnable { notifyDataSetChanged() })
    }

    companion object {
        const val TAG = "PostAdapter"
    }

}