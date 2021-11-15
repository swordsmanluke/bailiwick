package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.lifecycle.viewModelScope
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.databinding.PostBinding
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PostAdapter(private val db: BailiwickDatabase, private val bwModel: BailiwickViewModel, private val context: Context, private val list: ArrayList<Post>): BaseAdapter() {
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

        bwModel.viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val author = db.identityDao().find(post.authorId)
                val avatar = author.avatar(context.filesDir.toPath()) ?: BitmapFactory.decodeStream(
                    context.assets.open("avatar.png")
                )

                Handler(context.mainLooper).post {
                    binding.avatar.setImageBitmap(avatar)
                    binding.txtAuthor.text = author.name
                    // TODO: Examine Files and add images if necessary
                    // for f in post.files.select{f -> f.is_image}
                    //   img_content.setImageDrawable(Drawable.createFromPath(post.imageUrl()))
                    val img_content = binding.imgSocialContent
                    img_content.visibility = View.GONE

                    notifyDataSetChanged()
                }
            }
        }

        return binding.root
    }

    fun clear() {
        list.clear()
    }

    fun addToEnd(posts: List<Post>) {
        Log.i(TAG, "Adding ${posts.count()} posts with text: ${posts.map{it.text}}")
        val tempPosts: MutableSet<Post> = mutableSetOf()
        tempPosts.addAll(list)
        tempPosts.addAll(posts)
        list.clear()
        list.addAll(tempPosts)
        list.sortByDescending { it.timestamp }
        Handler(Looper.getMainLooper()).post(Runnable { notifyDataSetChanged() })
    }

    companion object {
        const val TAG = "PostAdapter"
    }

}