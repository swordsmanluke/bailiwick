package com.perfectlunacy.bailiwick.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.perfectlunacy.bailiwick.storage.DistHashTable

class BailwickViewModelFactory(private val dht: DistHashTable): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return BailiwickViewModel(dht) as T
    }
}