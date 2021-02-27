    package com.perfectlunacy.bailiwick

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.PostFile
import com.perfectlunacy.bailiwick.models.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.models.db.PostConverter
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.MockBailiwickNetwork
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.slmyldz.random.BitmapListener
import com.slmyldz.random.GifListener
import com.slmyldz.random.Randoms
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

class MockDataWriter(val db: BailiwickDatabase, val bwNetwork: MockBailiwickNetwork) {
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
        Log.i(FileSaver.TAG, "Random User for new post is: ${user.name}:${user.id}")
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
        Log.i(FileSaver.TAG, "Generating Post for user ${user.id}. '$text' / ${attachments.count()} attached")

        val post = convert.toDbPost(Post.create(user, text, attachments))
        Log.i(FileSaver.TAG, "after conversion: $post")
        posts.insert(post)
        val post_id = posts.postsForUser(user.id).last().id
        Log.i(FileSaver.TAG,"New post id: $post_id")
        attachments.forEach { files.insert(convert.toDbFile(it, post_id)) }
    }

    private fun generateAttachments(): List<PostFile> {

        val attachments: MutableList<PostFile> = mutableListOf()

        if (Random().nextInt(3) == 1) {
            val filename = UUID.randomUUID().toString()
            val useGif = Randoms.Boolean()
            val mimeType = when (useGif) {
                true -> "image/gif"; false -> "image/bmp"
            }
            val cid = bwNetwork.basePath + "/$filename"

            if (useGif) {
                Randoms.imageGif(FileSaver(bwNetwork, filename))
            } else {
                Randoms.image(FileSaver(bwNetwork, filename), 300, 400)
            }

            Log.i(FileSaver.TAG, "Creating new attachment and waiting for D/L")
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
        Randoms.imageAvatar(fileSaver, 200) // download a profile pic
        return User(UUID.randomUUID().toString(), Randoms.name(context), bwNetwork.basePath + "/$filename").also { u ->
            users.insert(u)
        }
    }

    class FileSaver(bwNetwork: BailiwickNetwork, val filename: String): GifListener,
        BitmapListener {

        private val mockBailiwickNetwork: MockBailiwickNetwork = bwNetwork as MockBailiwickNetwork

        override fun onSuccess(bitmap: ByteArray?) {
            val f = File(mockBailiwickNetwork.basePath + filename)
            Log.i(TAG, "Gif download succeeded. Storing at ${f.path}. bytes: ${bitmap?.size ?: 0}")

            val fs = FileOutputStream(f, false)
            try {
                bitmap ?: fs.write(bitmap)
            } finally {
                fs.close()
            }
            Log.i(TAG, "File at ${f.path} is ${f.length()} Bytes")
        }

        override fun onSuccess(bitmap: Bitmap?) {
            val f = File(mockBailiwickNetwork.basePath + filename)
            Log.i(TAG, "BMP download succeeded. Storing at ${f.path}")

            val fs = FileOutputStream(f, false)
            try {
                bitmap?.compress(Bitmap.CompressFormat.PNG,100, fs)
            } finally {
                fs.close()
            }
            Log.i(TAG, "File at ${f.path} is ${f.length()} Bytes")
        }

        override fun onFailure(e: Exception?) {
            Log.e(TAG, "Image download failed with", e)
        }

        companion object {
            const val TAG = "MockDataWriter"
        }
    }

}