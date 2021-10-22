package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.PeerId

data class Introduction(val isResponse: Boolean, val peerId: PeerId, val name: String, val publicKey: String)