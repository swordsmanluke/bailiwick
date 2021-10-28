package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.perfectlunacy.bailiwick.storage.Bailiwick

@Suppress("UNCHECKED_CAST") // It's fine.
class BailwickViewModelFactory(private val context: Context, private val dht: Bailiwick): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return BailiwickViewModel(context, dht) as T
    }
}