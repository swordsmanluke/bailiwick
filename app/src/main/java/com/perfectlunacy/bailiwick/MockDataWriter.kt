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
        return user
    }

    private fun createNewPost(user: User, context: Context) {
        val users = db.getUserDao()
        val posts = db.getPostDao()
        val files = db.getPostFileDao()

        val convert = PostConverter(users, files)
        val attachments = generateAttachments()

        // TODO: This is an ugly way to create these objects.
        val text = "Lorem Ipsum"

        val post = convert.toDbPost(Post.create(user, text, attachments))
        posts.insert(post)
        val post_id = posts.postsForUser(user.id).last().id
        attachments.forEach { files.insert(convert.toDbFile(it, post_id)) }
    }

    private fun generateAttachments(): List<PostFile> {

        val attachments: MutableList<PostFile> = mutableListOf()

        if (Random().nextInt(3) == 1) {
            val filename = UUID.randomUUID().toString()
            val useGif = Random().nextBoolean()
            val mimeType = when (useGif) {
                true -> "image/gif"; false -> "image/bmp"
            }
            val cid = bwNetwork.basePath + "/$filename"

            attachments.add(PostFile(mimeType, cid, cid.hashCode().toString()))
        }
        return attachments
    }


    private fun createFakeUser(
        context: Context,
        users: UserDao
    ): User {
        val filename = UUID.randomUUID().toString()

        return User(UUID.randomUUID().toString(), "Some user", bwNetwork.basePath + "/$filename").also { u ->
            users.insert(u)
        }
    }

}