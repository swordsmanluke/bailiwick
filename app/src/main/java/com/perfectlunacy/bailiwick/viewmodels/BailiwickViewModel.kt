package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perfectlunacy.bailiwick.fragments.AcceptIntroductionFragment
import com.perfectlunacy.bailiwick.models.Action
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.PeerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BailiwickViewModel(context: Context, val network: Bailiwick): ViewModel() {

    init {
        // Store the Bailiwick network for other's access
        BailiwickViewModel.network = network

//        val id = RefreshWorker.enqueue(context)
//        WorkManager.getInstance(context).getWorkInfoById(id).addListener(
//            { // Runnable
//                viewModelScope.launch {
//                    withContext(Dispatchers.Default) { refreshContent() }
//                }
//            },
//            { it.run() }  // Executable
//        )
    }

    // Currently visible content from the network
    // TODO: LiveData?
    val content = HashMap<String, MutableSet<Post>>()

    val acceptViewModel = AcceptIntroductionFragment.AcceptViewModel(
        MutableLiveData(AcceptIntroductionFragment.AcceptMode.CaptureUser),
        null)

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

        Log.i(TAG, "Retrieved ${network.posts.size} posts")
        content.getOrPut("everyone", { mutableSetOf() }).addAll(network.posts)

//        Log.i(TAG, "Retrieved ${sub.actions.count()} Actions")
//        sub.actions.forEach { processAction(sub.peerId, it) }
    }

    private fun processAction(peerId: PeerId, action: Action) {
        when (action.type) {
            Action.ActionType.UpdateKey -> {
                Log.i(TAG, "Received new key from $peerId")
                val newKey = action.get("key")!!
                Log.i(TAG, "TODO: Import key!")
//                network.keyring.addSecretKey(peerId, newKey)
//                Log.i(TAG, "Imported key successfully")
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