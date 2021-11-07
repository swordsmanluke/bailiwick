package com.perfectlunacy.bailiwick.storage.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

interface IPNS {
    fun publishRoot(root: ContentId)
    fun sequenceFor(peerId: PeerId): Long
    fun cidForPath(peerId: PeerId, path: String, minSequence: Int): ContentId?
}