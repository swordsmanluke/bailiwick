package com.perfectlunacy.bailiwick.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.models.Identity
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.models.db.PostConverter
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import threads.lite.cid.Cid

class BailiwickViewModel(val bwNetwork: BailiwickNetwork, val db: BailiwickDatabase): ViewModel() {
    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<Identity, List<Post>>()
    private var _uid = 0

    val selectedUser: Identity?
        get() {
            if (content.keys.isEmpty()) {
                return null
            } else {
                return content.keys.toTypedArray().get(_uid)
            }
        } // We'll fill this in later.

    fun selectNextUser() {
        if (content.keys.count() > 0) _uid = (_uid + 1) % content.keys.count()
        Log.i(TAG, "Selecting User ${selectedUser?.name} ${selectedUser?.name}")
    }

    fun selectPrevUser() {
        _uid = when(_uid) {
            0 -> content.keys.count() - 1
            else -> _uid - 1
        }
        Log.i(TAG, "Selecting User ${selectedUser?.name} ${selectedUser?.name}")
    }

    // The User
    var name: String
        get() = bwNetwork.identity.name
        set(value) {
            bwNetwork.identity = Identity(value, bwNetwork.identity.cid, bwNetwork.identity.profilePicFile)
        }

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
            val id = Identity(u.name, u.uid, bwNetwork.retrieve_file(Cid(u.profilePicCid.toByteArray())))
            content.put(id, posts.postsForUser(u.id).map { p ->
                convert.toPostModel(p)
            })
        }
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}