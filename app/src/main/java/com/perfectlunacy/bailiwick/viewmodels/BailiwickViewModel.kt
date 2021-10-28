package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.perfectlunacy.bailiwick.fragments.AcceptIntroductionFragment
import com.perfectlunacy.bailiwick.models.Action
import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.UserIdentity
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.workers.RefreshWorker
import com.perfectlunacy.bailiwick.workers.runners.RefreshRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.coroutineContext

class BailiwickViewModel(context: Context, val network: Bailiwick): ViewModel() {

    init {
        // Store the Bailiwick network for other's access
        BailiwickViewModel.network = network

        val id = RefreshWorker.enqueue(context)
        WorkManager.getInstance(context).getWorkInfoById(id).addListener(
            { // Runnable
                viewModelScope.launch {
                    withContext(Dispatchers.Default) { refreshContent() }
                }
            },
            { it.run() }  // Executable
        )
    }

    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<String, MutableSet<Post>>()
    private var _uid = 0

    val acceptViewModel = AcceptIntroductionFragment.AcceptViewModel(
        MutableLiveData(AcceptIntroductionFragment.AcceptMode.CaptureUser),
        null)

    val selectedUser: UserIdentity?
        get() {
            return null
        } // We'll fill this in later.

    // Name of the logged in User
    var name: String
        get() = network.identity.name
        set(value) {
            network.identity.name = value
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
            val allPeers = network.peers

            Log.i(TAG, "Peers: ${allPeers.count()}: ${allPeers.joinToString(",")}")

            allPeers.forEach { peerId ->
                Log.i(TAG, "Data for peer ${peerId}")
                val feeds = network.manifestFor(peerId)?.feeds ?: emptyList()
                Log.i(TAG, "Retrieved ${feeds.count()} feeds")
                feeds.forEach { feed ->
                    _users.add(feed.identity)

                    Log.i(TAG, "Retrieved ${feed.actions.count()} Actions")
                    feed.actions.forEach {
                        processAction(peerId, it)
                    }

                    Log.i(TAG, "Retrieved ${feed.posts.count()} posts")
                    content.getOrPut("everyone", { mutableSetOf() }).addAll(feed.posts)
                }
            }
        }
    }

    private fun processAction(peerId: PeerId, action: Action) {
        when (action.type) {
            Action.ActionType.UpdateKey -> {
                Log.i(TAG, "Received new key from $peerId")
                val newKey = action.get("key")!!
                network.keyring.addSecretKey(peerId, newKey)
                Log.i(TAG, "Imported key successfully")
            }
            Action.ActionType.Delete -> TODO()
            Action.ActionType.Introduce -> TODO()
        }
    }

    companion object {
        const val TAG = "BailiwickViewModel"
        private lateinit var network: Bailiwick

        @JvmStatic
        fun bailiwick(): Bailiwick {
            return network
        }
    }
}