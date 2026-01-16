package com.perfectlunacy.bailiwick.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.storage.BlobCache
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.views.ZoomableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen photo viewer with pinch-to-zoom and swipe navigation.
 *
 * Arguments:
 * - ARG_PHOTO_HASHES: Array of blob hashes for the photos to display
 * - ARG_START_POSITION: Initial position (0-indexed)
 */
class PhotoViewerFragment : Fragment() {

    private lateinit var pagerPhotos: ViewPager2
    private lateinit var txtPhotoCounter: android.widget.TextView
    private lateinit var progressLoading: ProgressBar

    private var photoHashes: List<String> = emptyList()
    private var startPosition: Int = 0
    private var photoBitmaps: MutableMap<Int, Bitmap> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoHashes = arguments?.getStringArrayList(ARG_PHOTO_HASHES) ?: emptyList()
        startPosition = arguments?.getInt(ARG_START_POSITION, 0) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_photo_viewer, container, false)

        pagerPhotos = view.findViewById(R.id.pager_photos)
        txtPhotoCounter = view.findViewById(R.id.txt_photo_counter)
        progressLoading = view.findViewById(R.id.progress_loading)
        val btnClose: View = view.findViewById(R.id.btn_close)

        btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        setupViewPager()
        updateCounter(startPosition)

        return view
    }

    private fun setupViewPager() {
        pagerPhotos.adapter = PhotoPagerAdapter()
        pagerPhotos.setCurrentItem(startPosition, false)

        pagerPhotos.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCounter(position)
            }
        })
    }

    private fun updateCounter(position: Int) {
        if (photoHashes.size > 1) {
            txtPhotoCounter.text = "${position + 1} / ${photoHashes.size}"
            txtPhotoCounter.visibility = View.VISIBLE
        } else {
            txtPhotoCounter.visibility = View.GONE
        }
    }

    private inner class PhotoPagerAdapter : RecyclerView.Adapter<PhotoPagerAdapter.PhotoPageViewHolder>() {

        inner class PhotoPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgPhoto: ZoomableImageView = view.findViewById(R.id.img_photo)
            val progressLoading: ProgressBar = view.findViewById(R.id.progress_loading)
            val imgError: ImageView = view.findViewById(R.id.img_error)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoPageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo_page, parent, false)
            return PhotoPageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoPageViewHolder, position: Int) {
            val hash = photoHashes[position]

            // Check if already loaded
            val cachedBitmap = photoBitmaps[position]
            if (cachedBitmap != null) {
                holder.imgPhoto.setImageBitmap(cachedBitmap)
                holder.progressLoading.visibility = View.GONE
                holder.imgError.visibility = View.GONE
                return
            }

            // Show loading state
            holder.progressLoading.visibility = View.VISIBLE
            holder.imgError.visibility = View.GONE

            // Load image in background
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadPhoto(hash)
                }

                if (bitmap != null) {
                    photoBitmaps[position] = bitmap
                    holder.imgPhoto.setImageBitmap(bitmap)
                    holder.progressLoading.visibility = View.GONE
                } else {
                    holder.progressLoading.visibility = View.GONE
                    holder.imgError.visibility = View.VISIBLE
                }
            }
        }

        override fun getItemCount(): Int = photoHashes.size
    }

    private fun loadPhoto(hash: BlobHash): Bitmap? {
        return try {
            if (!Bailiwick.isInitialized()) return null

            val blobCache = BlobCache(Bailiwick.getInstance().cacheDir)
            val data = blobCache.get(hash) ?: return null
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val ARG_PHOTO_HASHES = "photo_hashes"
        const val ARG_START_POSITION = "start_position"

        fun newBundle(photoHashes: List<String>, startPosition: Int = 0): Bundle {
            return Bundle().apply {
                putStringArrayList(ARG_PHOTO_HASHES, ArrayList(photoHashes))
                putInt(ARG_START_POSITION, startPosition)
            }
        }
    }
}
