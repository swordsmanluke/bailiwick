package com.perfectlunacy.bailiwick.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.perfectlunacy.bailiwick.DeviceKeyring
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

@Suppress("UNCHECKED_CAST") // It's fine.
class BailwickViewModelFactory(
    private val context: Context,
    private val network: BailiwickNetwork,
    private val iroh: IrohNode,
    private val keyring: DeviceKeyring,
    private val db: BailiwickDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BailiwickViewModel(context, network, iroh, keyring, db) as T
    }
}
