package com.perfectlunacy.bailiwick.models.ipfs

import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

/***
 * These files Represent the objects written to and read from the IPFS network.
 *
 * They're all POJO structs intended to be able to serialize to/from JSON easily
 */

data class BailiwickAccount(val peerId: String,
                            val keyFileCid: String,
                            val subscriptionsCid: String)

data class User(val name: String, val peerId: String, val profilePicCid: String)
data class Identity(val name: String, val profilePicCid: String)

data class KeyFile(val keys: Map<String, List<String>>)

data class Subscriber(val peerId: PeerId, val identity: ContentId, val publicKey: String)
data class Subscriptions(val peers: MutableList<String>, val circles: MutableMap<String, MutableList<Subscriber>>)

enum class InteractionType { Reaction, Tag }

data class Interaction(val type: InteractionType,
                       val content: String,
                       val parentCid: String,
                       val signature: String)

data class FileDef(val mimeType: String, val cid: String)

enum class ActionType { Delete, UpdateKey, Introduce }
data class Action(val type: ActionType, val data: Map<String, String>)

data class Post(val timestamp: Long,
                val parentCid: String?,
                val text: String,
                val files: List<FileDef>,
                val signature: String)

data class Feed(val updatedAt: Long,
                val posts: List<ContentId>,
                val interactions: List<ContentId>,
                val actions: List<ContentId>,
                val identity: ContentId)

data class Manifest(val feeds: List<ContentId>)
