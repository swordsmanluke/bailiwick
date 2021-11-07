package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsSerializable

data class IpfsFeed(val updated_at: Long,
                    val posts: List<ContentId>,
                    val interactions: List<ContentId>,
                    val actions: List<ContentId>,
                    val identity: ContentId):
    IpfsSerializable()