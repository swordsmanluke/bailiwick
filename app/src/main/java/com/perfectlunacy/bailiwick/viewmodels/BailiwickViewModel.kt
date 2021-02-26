package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.PostFile
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.MockBailiwickNetwork
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel.FileSaver.Companion.TAG
import com.slmyldz.random.BitmapListener
import com.slmyldz.random.GifListener
import com.slmyldz.random.Randoms
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap

class BailiwickViewModel(val bwNetwork: BailiwickNetwork, val db: BailiwickDatabase): ViewModel() {
    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<User, List<Post>>()
    private var _uid = 0

    val selectedUser: Optional<User>
        get() {
            if (content.keys.isEmpty()) {
                return Optional.empty()
            } else {
                return Optional.of(content.keys.toTypedArray().get(_uid))
            }
        } // We'll fill this in later.

    fun selectNextUser() {
        _uid = (_uid + 1) % content.keys.count()
        Log.i(TAG, "Selecting User ${selectedUser.get().name} ${selectedUser.get().id}")
    }

    fun selectPrevUser() {
        _uid = when(_uid) {
            0 -> content.keys.count() - 1
            else -> _uid - 1
        }
        Log.i(TAG, "Selecting User ${selectedUser.get().name} ${selectedUser.get().id}")
    }

    // The User
    var name: String
        get() = bwNetwork.identity.name
        set(value) { bwNetwork.identity = Identity(value) }

    init {
        GlobalScope.launch { refreshContent() }
    }

    /***
     * Pulls currently known content from DB and stores it in the `content` map.
     */
    fun refreshContent() {
        val users = db.getUserDao()
        val posts = db.getPostDao()
        val files = db.getPostFileDao()
        val convert = PostConverter(users, files)
        // FIXME: Add pagination to avoid loading _everything_ into memory
        users.all().forEach { u ->
            content.put(u, posts.postsForUser(u.id).map { p ->
                convert.toPostModel(p)
            })
        }
    }

    class FileSaver(bwNetwork: BailiwickNetwork, val filename: String): GifListener, BitmapListener {

        private val mockBailiwickNetwork: MockBailiwickNetwork = bwNetwork as MockBailiwickNetwork

        override fun onSuccess(bitmap: ByteArray?) {
            val f = File(mockBailiwickNetwork.basePath + "/$filename")
            Log.i(TAG, "Gif download succeeded. Storing at ${f.path}")

            val fs = FileOutputStream(f, true)
            try {
                bitmap ?: fs.write(bitmap)
            } finally {
                fs.close()
            }
        }

        override fun onSuccess(bitmap: Bitmap?) {
            val f = File(mockBailiwickNetwork.basePath + "/$filename")
            Log.i(TAG, "BMP download succeeded. Storing at ${f.path}")

            val fs = FileOutputStream(f, false)
            try {
                bitmap?.compress(Bitmap.CompressFormat.PNG,10, fs)
            } finally {
                fs.close()
            }
        }

        override fun onFailure(e: Exception?) {
            Log.e(TAG, "Image download failed with", e)
        }

        companion object {
            const val TAG = "BailiwickViewModel"
        }
    }


}