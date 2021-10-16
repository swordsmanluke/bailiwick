package com.perfectlunacy.bailiwick.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.ipfs.Identity
import com.perfectlunacy.bailiwick.storage.ipfs.Post
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BailiwickViewModel(val bwNetwork: BailiwickNetwork): ViewModel() {
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
        get() = bwNetwork.identity!!.name
        set(value) {
            bwNetwork.identity = Identity(value, bwNetwork.myId())
        }

    init {
        GlobalScope.launch { refreshContent() }
    }

    fun refreshContent() {
        // TODO: Pull data from IPFS - should be quick since its local
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}