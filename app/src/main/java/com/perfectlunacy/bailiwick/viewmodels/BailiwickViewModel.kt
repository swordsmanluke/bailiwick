package com.perfectlunacy.bailiwick.viewmodels

import androidx.lifecycle.ViewModel
import com.perfectlunacy.bailiwick.storage.DistHashTable

class BailiwickViewModel(private val dht: DistHashTable): ViewModel() {
    var name: String
        get() = dht.identity.name
        set(value) { dht.updateIdentity(value) }
}