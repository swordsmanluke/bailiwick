package com.perfectlunacy.bailiwick.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork

@Suppress("UNCHECKED_CAST") // It's fine.
class BailwickViewModelFactory(private val dht: BailiwickNetwork): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return BailiwickViewModel(dht) as T
    }
}