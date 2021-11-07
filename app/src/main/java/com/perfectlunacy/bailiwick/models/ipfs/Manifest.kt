package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsSerializable

data class Manifest(val feeds: List<ContentId>, val actions: List<ContentId>):
    IpfsSerializable()