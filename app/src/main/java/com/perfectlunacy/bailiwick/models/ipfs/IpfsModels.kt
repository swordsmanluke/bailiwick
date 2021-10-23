package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

/***
 * These files Represent the objects written to and read from the IPFS network.
 *
 * They're all POJO structs intended to be able to serialize to/from JSON easily
 */

data class User(val name: String, val peerId: String, val profilePicCid: String)
data class Identity(val name: String, val profilePicCid: String)

enum class InteractionType { Reaction, Tag }

data class Interaction(val type: InteractionType,
                       val content: String,
                       val parentCid: String,
                       val signature: String)

data class FileDef(val mimeType: String, val cid: String)
