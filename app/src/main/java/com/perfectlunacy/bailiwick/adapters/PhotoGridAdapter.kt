package com.perfectlunacy.bailiwick.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.storage.BlobHash

/**
 * Adapter for displaying photos in a grid layout.
 * Supports showing up to MAX_VISIBLE photos with a "+N" overlay for additional ones.
 */
class PhotoGridAdapter(
    private val photos: List<PhotoItem>,
    private val onPhotoClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder>() {

    companion object {
        private const val MAX_VISIBLE = 4
    }

    /**
     * Represents a photo item with loading state.
     */
    data class PhotoItem(
        val hash: BlobHash,
        val bitmap: Bitmap? = null,
        val isLoading: Boolean = false,
        val hasError: Boolean = false
    )

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgPhoto: ImageView = view.findViewById(R.id.img_photo)
        val progressPhoto: ProgressBar = view.findViewById(R.id.progress_photo)
        val imgError: ImageView = view.findViewById(R.id.img_error)
        val txtMoreCount: TextView = view.findViewById(R.id.txt_more_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_grid, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val visibleCount = minOf(photos.size, MAX_VISIBLE)
        val photo = photos[position]
        val isLastVisible = position == visibleCount - 1
        val remainingCount = photos.size - MAX_VISIBLE

        // Set the photo
        if (photo.bitmap != null) {
            holder.imgPhoto.setImageBitmap(photo.bitmap)
            holder.progressPhoto.visibility = View.GONE
            holder.imgError.visibility = View.GONE
        } else if (photo.hasError) {
            holder.imgPhoto.setImageResource(R.color.colorPrimaryLight)
            holder.progressPhoto.visibility = View.GONE
            holder.imgError.visibility = View.VISIBLE
        } else if (photo.isLoading) {
            holder.imgPhoto.setImageResource(R.color.colorPrimaryLight)
            holder.progressPhoto.visibility = View.VISIBLE
            holder.imgError.visibility = View.GONE
        } else {
            // Not loaded yet, show placeholder
            holder.imgPhoto.setImageResource(R.color.colorPrimaryLight)
            holder.progressPhoto.visibility = View.GONE
            holder.imgError.visibility = View.GONE
        }

        // Show "+N" overlay if this is the last visible photo and there are more
        if (isLastVisible && remainingCount > 0) {
            holder.txtMoreCount.visibility = View.VISIBLE
            holder.txtMoreCount.text = "+$remainingCount"
        } else {
            holder.txtMoreCount.visibility = View.GONE
        }

        // Click handler
        holder.itemView.setOnClickListener {
            onPhotoClick(position)
        }
    }

    override fun getItemCount(): Int = minOf(photos.size, MAX_VISIBLE)
}
