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
import com.perfectlunacy.bailiwick.models.ipfs.Identity
import com.perfectlunacy.bailiwick.storage.ContentId
import kotlinx.coroutines.Dispatchers
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
                val feeds = network.manifestFor(peerId)?.feeds ?: emptyList()
                feeds.forEach { feed ->
                    _users.add(feed.identity)
                    content.getOrPut("everyone", { mutableSetOf() }).addAll(feed.posts)
                }
            }
        }
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}