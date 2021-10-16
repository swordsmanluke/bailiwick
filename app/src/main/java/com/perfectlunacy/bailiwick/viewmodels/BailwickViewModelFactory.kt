package com.perfectlunacy.bailiwick.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase

@Suppress("UNCHECKED_CAST") // It's fine.
class BailwickViewModelFactory(private val dht: BailiwickNetwork, private val bwDb: BailiwickDatabase): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return BailiwickViewModel(dht, bwDb) as T
    }
}