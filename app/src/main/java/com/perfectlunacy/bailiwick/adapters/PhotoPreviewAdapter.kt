package com.perfectlunacy.bailiwick.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R

/**
 * Adapter for displaying photo thumbnails in a horizontal preview row.
 * Used in the post composer to show selected photos before posting.
 */
class PhotoPreviewAdapter(
    private val photos: List<Bitmap>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PhotoPreviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.img_preview)
        val removeButton: View = view.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageBitmap(photos[position])
        holder.removeButton.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount() = photos.size
}
