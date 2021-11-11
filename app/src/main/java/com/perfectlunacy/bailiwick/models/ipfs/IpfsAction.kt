package com.perfectlunacy.bailiwick.models.ipfs

import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCacheReader
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsSerializable

data class IpfsAction(val type: String, val metadata: Map<String, String>):
    IpfsSerializable()