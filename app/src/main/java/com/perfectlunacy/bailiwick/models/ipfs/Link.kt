package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId

enum class LinkType {
    File,
    Dir
}
data class Link(val name: String, val cid: ContentId, val type: LinkType)