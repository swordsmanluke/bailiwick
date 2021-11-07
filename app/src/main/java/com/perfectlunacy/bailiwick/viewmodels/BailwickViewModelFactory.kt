package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS

@Suppress("UNCHECKED_CAST") // It's fine.
class BailwickViewModelFactory(private val context: Context, private val dht: Bailiwick, private val ipfs: IPFS, private val ipns: IpnsCacheDao): ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return BailiwickViewModel(context, dht, ipfs, ipns) as T
    }
}