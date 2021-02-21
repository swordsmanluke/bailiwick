package com.perfectlunacy.bailiwick.viewmodels

import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.PostFile
import com.perfectlunacy.bailiwick.models.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BailiwickViewModel(val bwNetwork: BailiwickNetwork, private val db: BailiwickDatabase): ViewModel() {
    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<User, List<Post>>()

    // Active User
    var name: String
        get() = bwNetwork.identity.name
        set(value) { bwNetwork.identity = Identity(value) }

    init {
        GlobalScope.launch { refreshContent() }
    }

    /***
     * Pulls currently known content from DB and stores it in the `content` map.
     */
    private fun refreshContent() {
        val users = db.getUserDao()
        val posts = db.getPostDao()
        val files = db.getPostFileDao()
        users.all().forEach { u ->
            content.put(u, posts.postsForUser(u.id).map { p ->
                val postFiles = files.filesForPost(p.id).map { f -> PostFile.fromDbPostFile(f) }
                Post.fromDbPost(p, postFiles)
            })
        }
    }


}