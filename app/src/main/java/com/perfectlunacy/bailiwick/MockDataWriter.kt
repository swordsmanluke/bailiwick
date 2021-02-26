    package com.perfectlunacy.bailiwick

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.PostFile
import com.perfectlunacy.bailiwick.models.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.models.db.PostConverter
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.MockBailiwickNetwork
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.slmyldz.random.Randoms
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        Log.i(BailiwickViewModel.FileSaver.TAG, "Random User for new post is: ${user.name}:${user.id}")
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
        Log.i(BailiwickViewModel.FileSaver.TAG, "Generating Post for user ${user.id}. '$text' / ${attachments.count()} attached")

        val post = convert.toDbPost(Post.create(user, text, attachments))
        Log.i(BailiwickViewModel.FileSaver.TAG, "after conversion: $post")
        posts.insert(post)
        val post_id = posts.postsForUser(user.id).last().id
        Log.i(BailiwickViewModel.FileSaver.TAG,"New post id: $post_id")
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
            val cid = bwNetwork.basePath + "/$filename"

            if (useGif) {
                Randoms.imageGif(BailiwickViewModel.FileSaver(bwNetwork, filename))
            } else {
                Randoms.image(BailiwickViewModel.FileSaver(bwNetwork, filename), 300, 400)
            }

            Log.i(BailiwickViewModel.FileSaver.TAG, "Creating new attachment and waiting for D/L")
            attachments.add(PostFile(mimeType, cid, cid.hashCode().toString()))
        }
        return attachments
    }

    private fun createFakeUser(
        context: Context,
        users: UserDao
    ): User {
        val filename = UUID.randomUUID().toString()
        val fileSaver = BailiwickViewModel.FileSaver(bwNetwork, filename)
        Randoms.imageAvatar(fileSaver, 1000000) // download a profile pic
        return User(UUID.randomUUID().toString(), Randoms.name(context), bwNetwork.basePath + "/$filename").also { u ->
            users.insert(u)
        }
    }
}