package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsSerializable

/***
 * These files Represent the objects written to and read from the IPFS network.
 *
 * They're all POJO structs intended to be able to serialize to/from JSON easily
 */

data class IpfsIdentity(val name: String, val profilePicCid: String):
    IpfsSerializable()

enum class InteractionType { Reaction, Tag }

data class Interaction(val type: InteractionType,
                       val content: String,
                       val parentCid: String,
                       val signature: String)
