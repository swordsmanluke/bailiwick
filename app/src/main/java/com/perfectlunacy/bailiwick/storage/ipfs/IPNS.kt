package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.perfectlunacy.bailiwick.models.Link
import com.perfectlunacy.bailiwick.models.db.AccountDao
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

interface IPNS {
    fun publishRoot(root: ContentId)
    fun sequenceFor(peerId: PeerId): Long
    fun cidForPath(peerId: PeerId, path: String, minSequence: Int): ContentId?
}