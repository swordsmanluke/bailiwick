package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

data class SubscriptionRequest(val peerId: PeerId, val identityCid: ContentId, val publicKey: String)