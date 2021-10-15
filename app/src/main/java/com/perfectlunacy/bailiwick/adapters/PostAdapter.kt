package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.storage.ipfs.Post

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

        // TODO: Examine Files and add images if necessary
        // for f in post.files.select{f -> f.is_image}
        //   img_content.setImageDrawable(Drawable.createFromPath(post.imageUrl()))
        val img_content = view!!.findViewById<ImageView>(R.id.img_social_content)
        img_content.visibility = View.GONE

        val txt_content = view.findViewById<TextView>(R.id.txt_social_content)
        if (post.text.isBlank()) {
            txt_content.visibility = View.GONE
        } else {
            txt_content.text = post.text
        }

        return view
    }

    fun clear() {
        list.clear()
    }

    fun addToEnd(posts: List<Post>) {
        list.addAll(posts)
    }

}