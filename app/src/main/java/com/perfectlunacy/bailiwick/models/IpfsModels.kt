package com.perfectlunacy.bailiwick.storage.ipfs

import com.perfectlunacy.bailiwick.storage.PeerId

data class BailiwickAccount(val peerId: String,
                            val keyFileCid: String,
                            val subscriptionsCid: String)

data class User(val name: String, val peerId: String, val profilePicCid: String)
data class Identity(val name: String, val profilePicCid: String)

data class KeyFile(val keys: Map<String, List<String>>)

data class Subscriptions(val peers: List<String>, val circles: Map<String, List<PeerId>>)

enum class InteractionType { Reaction, Tag }

data class Interaction(val type: InteractionType,
                       val content: String,
                       val parentCid: String,
                       val signature: String)

data class FileDef(val mimeType: String, val cid: String)

enum class ActionType { Delete, UpdateKey, Introduce }
data class Action(val type: ActionType, val data: Map<String, String>)

data class Post(val version: String,
                val timestamp: Long,
                val parentCid: String?,
                val text: String,
                val files: List<FileDef>,
                val signature: String)

data class Feed(val updatedAt: Long,
                val posts: List<Post>,
                val interactions: List<Interaction>,
                val actions: List<Action>,
                val identity: String)

data class Manifest(val feeds: List<Feed>, val interactions: List<Interaction>)
