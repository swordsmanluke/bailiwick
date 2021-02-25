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

class BailiwickViewModel(val bwNetwork: BailiwickNetwork, private val db: BailiwickDatabase): ViewModel() {
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
     * TODO: Extract all this to a mock-data-building class
     */
    fun generateFakePost(context: Context) {
        GlobalScope.launch {
            val user = randomUser(context)
            createNewPost(user, context)
        }
    }

    private fun randomUser(context: Context): User {
        val users = db.getUserDao()
        val numFriends = users.all().count()
        val user = if (numFriends == 0 || numFriends < 10 && Random().nextInt(13 - numFriends) >= 3) {
            createFakeUser(context, users)
        } else {
            users.all().shuffled().first()
        }
        Log.i(TAG, "Random User for new post is: ${user.name}:${user.id}")
        return user
    }

    private fun createNewPost(user: User, context: Context) {
        val users = db.getUserDao()
        val posts = db.getPostDao()
        val files = db.getPostFileDao()

        val convert = PostConverter(users, files)
        val attachments = generateAttachments()

        // TODO: This is an ugly way to create these objects.
        val text = Randoms.sentence(context)
        Log.i(TAG, "Generating Post for user ${user.id}. '$text' / ${attachments.count()} attached")

        val post = convert.toDbPost(Post.create(user, text, attachments))
        Log.i(TAG, "after conversion: $post")
        posts.insert(post)
        val post_id = posts.postsForUser(user.id).last().id
        Log.i(TAG,"New post id: $post_id")
        attachments.forEach { files.insert(convert.toDbFile(it, post_id)) }
    }

    private fun generateAttachments(): List<PostFile> {

        val attachments: MutableList<PostFile> = mutableListOf()

        while (Random().nextInt(5) == 1) {
            val filename = UUID.randomUUID().toString()
            val useGif = Randoms.Boolean()
            val mimeType = when (useGif) {
                true -> "image/gif"; false -> "image/bmp"
            }
            val cid = (bwNetwork as MockBailiwickNetwork).basePath + "/$filename"

            if (useGif) {
                Randoms.imageGif(FileSaver(bwNetwork, filename))
            } else {
                Randoms.image(FileSaver(bwNetwork, filename), 300, 400)
            }

            Log.i(TAG, "Creating new attachment and waiting for D/L")
            attachments.add(PostFile(mimeType, cid, cid.hashCode().toString()))
        }
        return attachments
    }

    private fun createFakeUser(
        context: Context,
        users: UserDao
    ): User {
        val filename = UUID.randomUUID().toString()
        val fileSaver = FileSaver(bwNetwork, filename)
        Randoms.imageAvatar(fileSaver, 1000000) // download a profile pic
        return User(UUID.randomUUID().toString(), Randoms.name(context), (bwNetwork as MockBailiwickNetwork).basePath + "/$filename").also { u ->
            users.insert(u)
        }
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