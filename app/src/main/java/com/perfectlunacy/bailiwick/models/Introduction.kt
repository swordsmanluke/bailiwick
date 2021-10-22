package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

data class Introduction(val uuid: UUID, val peerId: PeerId, val name: String, val publicKey: String)