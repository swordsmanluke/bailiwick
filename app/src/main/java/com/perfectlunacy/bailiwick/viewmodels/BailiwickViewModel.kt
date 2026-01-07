package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.DeviceKeyring
import com.perfectlunacy.bailiwick.fragments.AcceptIntroductionFragment
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

class BailiwickViewModel(
    context: Context,
    val network: BailiwickNetwork,
    val iroh: IrohNode,
    val keyring: DeviceKeyring,
    val db: BailiwickDatabase
) : ViewModel() {

    // Currently visible content from the network
    // TODO: LiveData?
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
        Log.i(TAG, "Retrieved ${network.posts.size} posts")
        content.getOrPut("everyone") { mutableSetOf() }.addAll(network.posts)

//        Log.i(TAG, "Retrieved ${sub.actions.count()} Actions")
//        sub.actions.forEach { processAction(sub.peerId, it) }
    }

    companion object {
        const val TAG = "BailiwickViewModel"
    }
}
