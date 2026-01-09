package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.DeviceKeyring
import com.perfectlunacy.bailiwick.fragments.AcceptIntroductionFragment
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.EVERYONE_CIRCLE
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

class BailiwickViewModel(
    context: Context,
    val network: BailiwickNetwork,
    val iroh: IrohNode,
    val keyring: DeviceKeyring,
    val db: BailiwickDatabase
) : ViewModel() {

    // Reactive posts from database - automatically updates when posts change
    val postsLive: LiveData<List<Post>> = network.postsLive

    // Legacy content map - kept for backward compatibility during transition
    val content = HashMap<String, MutableSet<Post>>()

    val acceptViewModel = AcceptIntroductionFragment.AcceptViewModel(
        MutableLiveData(AcceptIntroductionFragment.AcceptMode.CaptureUser),
        null
    )

    // Name of the logged in User
    var name: String
        get() = network.me.name
        set(value) {
            network.me.name = value
            // TODO: me.save(filesDir)
        }

    fun getUsers(): List<Identity> {
        return network.users
    }

    suspend fun refreshContent() {
        val posts = network.posts
        Log.i(TAG, "Retrieved ${posts.size} posts from database")
        posts.forEach { post ->
            Log.d(TAG, "  Post ${post.id}: authorId=${post.authorId}, text=${post.text?.take(30)}...")
        }
        content.getOrPut(EVERYONE_CIRCLE) { mutableSetOf() }.addAll(posts)

//        Log.i(TAG, "Retrieved ${sub.actions.count()} Actions")
//        sub.actions.forEach { processAction(sub.peerId, it) }
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}
