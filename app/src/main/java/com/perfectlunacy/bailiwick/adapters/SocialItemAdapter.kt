package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.fragments.views.PostMessage

class SocialItemAdapter(private val context: Context, private val list: ArrayList<PostMessage>): BaseAdapter() {

    fun clear(): Unit {
        list.clear()
    }

    override fun getCount(): Int {
        return list.count()
    }

    override fun getItem(idx: Int): Any? {
        if (idx < list.count()) {
            return list[idx]
        } else {
            return null
        }
    }

    override fun getItemId(idx: Int): Long {
        return idx.toLong()
    }

    override fun getView(idx: Int, social_item_view: View?, parent: ViewGroup?): View {
        var view = social_item_view
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.social_item, parent, false)
        }
        val post = getItem(idx)!! as PostMessage

        val img_content = view!!.findViewById<ImageView>(R.id.img_social_content)
        if (post.imageUrl() == null) {
            img_content.visibility = View.GONE
        } else {
            img_content.setImageDrawable(Drawable.createFromPath(post.imageUrl()))
        }

        val txt_content = view.findViewById<TextView>(R.id.txt_social_content)
        if (post.text().isBlank()) {
            txt_content.visibility = View.GONE
        } else {
            txt_content.text = post.text()
        }

        return view
    }

    fun addToEnd(posts: List<PostMessage>) {
        list.addAll(posts)
    }
}