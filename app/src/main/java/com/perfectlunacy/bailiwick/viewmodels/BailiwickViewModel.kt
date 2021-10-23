package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perfectlunacy.bailiwick.fragments.AcceptIntroductionFragment
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.UserIdentity
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.models.ipfs.Feed
import com.perfectlunacy.bailiwick.models.ipfs.Identity
import com.perfectlunacy.bailiwick.models.ipfs.Manifest
import com.perfectlunacy.bailiwick.storage.ContentId
import kotlinx.coroutines.Dispatchers
import com.perfectlunacy.bailiwick.models.ipfs.Post as IpfsPost
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.min

class BailiwickViewModel(val network: Bailiwick): ViewModel() {

    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<String, MutableSet<Post>>()
    private var _uid = 0

    val acceptViewModel = AcceptIntroductionFragment.AcceptViewModel(
        MutableLiveData(AcceptIntroductionFragment.AcceptMode.CaptureUser),
        null)

    val selectedUser: Identity?
        get() {
            return null
        } // We'll fill this in later.

    // Name of the logged in User
    var name: String
        get() = network.identity.name
        set(value) {
            network.identity = Identity(value, network.peerId)
        }

    val activeAccount: Account?
        get() = network.account

    fun bootstrap(context: Context) {
        network.ipfs.bootstrap(context)
    }

    fun createAccount(name: String, username: String, password: String, avatarCid: ContentId) {
        network.newAccount(name, username, password, avatarCid)
    }

    init {
        viewModelScope.launch { withContext(Dispatchers.Default) { refreshContent() } }
    }

    private var _users = mutableSetOf<UserIdentity>()
    val users: List<UserIdentity>
        get() = _users.toList()

    suspend fun refreshContent() {

        if (network.account != null) {
            val allPeers = network.circles.all().flatMap { c ->
                c.peers
            }.toSet() // kill duplicates

            Log.i(TAG, "Peers: ${allPeers.count()}: ${allPeers.joinToString(",")}")

            allPeers.forEach { peerId ->
                val enc = network.encryptorForPeer(peerId)
                val minSeq = if(peerId == network.peerId) { activeAccount?.sequence ?: 0 } else { 0 }
                val feeds = network.manifestFor(peerId, enc, minSeq)?.feeds?.mapNotNull { cid -> network.retrieve(cid, enc, Feed::class.java) }?: emptyList()
                feeds.forEach { feed ->
                    val user = UserIdentity.fromIPFS(network, enc, feed.identity)
                    _users.add(user)
                    val posts = feed.posts.mapNotNull { cid ->
                        val ipfsPost = network.retrieve(cid, enc, IpfsPost::class.java)
                        if (ipfsPost != null) {
                            Post.fromIPFS(network, user, cid, ipfsPost)
                        } else {
                            null
                        }
                    }

                    content.getOrPut("everyone", { mutableSetOf() }).addAll(posts)
                }
            }
        }
    }

    fun savePost(newPost: IpfsPost, keyName: String) {
        // Store the Post
        val signed = network.sign(newPost)
        val aes = network.encryptorForKey(keyName)
        val post = network.store(signed, aes)

        // Update the feed and manifest
        val manifest = network.ipfsManifest
        // TODO: Pick feed based on user selection
        val feed = network.retrieve(manifest.feeds[0], aes, Feed::class.java)!!
        val newFeed = Feed(Calendar.getInstance().timeInMillis, feed.posts + post, feed.interactions, feed.actions, feed.identity)
        Log.i(TAG, "Adding post $post to feed: ${newFeed.posts} old posts: ${feed.posts}")

        val newFeedCid = network.store(newFeed, aes)
        val feedList = manifest.feeds.toMutableList()
        feedList.removeAt(0)
        feedList.add(0, newFeedCid)
        network.ipfsManifest = Manifest(feedList)
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}