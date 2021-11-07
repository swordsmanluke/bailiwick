package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsSerializable

data class IpfsFileDef(val mimeType: String, val cid: ContentId)

data class IpfsPost(val timestamp: Long,
                    val parent_cid: ContentId?,
                    val text: String,
                    val files: List<IpfsFileDef>,
                    val signature: String):
    IpfsSerializable()