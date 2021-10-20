package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.models.Feed
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.models.Manifest
import com.perfectlunacy.bailiwick.models.Post
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap

class BailiwickViewModel(val bwNetwork: Bailiwick): ViewModel() {
    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<String, MutableSet<Post>>()
    private var _uid = 0

    val selectedUser: Identity?
        get() {
            return null
        } // We'll fill this in later.

    // Name of the logged in User
    var name: String
        get() = bwNetwork.identity.name
        set(value) {
            bwNetwork.identity = Identity(value, bwNetwork.peerId)
        }

    val activeAccount: Account?
        get() = bwNetwork.account

    fun bootstrap(context: Context) {
        bwNetwork.ipfs.bootstrap(context)
    }

    fun createAccount(username: String, password: String) {
        bwNetwork.newAccount(username, password)
    }

    init {
        GlobalScope.launch { refreshContent() }
    }

    fun refreshContent() {
        if (bwNetwork.account != null) {
            // Pull content from ourselves first
            val enc = bwNetwork.encryptorForKey("${bwNetwork.peerId}:everyone")
            val manifest = bwNetwork.manifest

            // TODO: 'enc' needs to be replaced with per-feed encryption keys
            //       also, will need to try multiple keys until we find a valid one.
            //       it is expected that most peers will have feeds that we cannot
            //       decrypt as we are not part of that circle.
            val feeds = manifest.feeds.mapNotNull { cid -> bwNetwork.retrieve(cid, enc, Feed::class.java) }
            feeds.forEach { feed ->
                val posts =
                    feed.posts.mapNotNull { cid -> bwNetwork.retrieve(cid, enc, Post::class.java) }
                if(content["everyone"] == null) {
                    content["everyone"] = mutableSetOf()
                }
                content["everyone"]!!.addAll(posts)
            }
        }
    }

    fun savePost(newPost: Post, keyName: String) {
        // Store the Post
        val signed = bwNetwork.sign(newPost)
        val aes = bwNetwork.encryptorForKey(keyName)
        val post = bwNetwork.store(signed, aes)

        // Update the feed and manifest
        val manifest = bwNetwork.manifest
        // TODO: Pick feed based on user selection
        val feed = bwNetwork.retrieve(manifest.feeds[0], aes, Feed::class.java)!!
        val newFeed = Feed(Calendar.getInstance().timeInMillis, feed.posts + post, feed.interactions, feed.actions, feed.identity)
        Log.i(TAG, "Adding post $post to feed: ${newFeed.posts} old posts: ${feed.posts}")

        val newFeedCid = bwNetwork.store(newFeed, aes)
        val feedList = manifest.feeds.toMutableList()
        feedList.removeAt(0)
        feedList.add(0, newFeedCid)
        bwNetwork.manifest = Manifest(feedList)
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}