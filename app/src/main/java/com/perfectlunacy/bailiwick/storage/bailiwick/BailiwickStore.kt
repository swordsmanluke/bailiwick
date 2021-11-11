package com.perfectlunacy.bailiwick.storage.bailiwick

import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.ipfs.Interaction
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.io.BufferedInputStream
import java.io.InputStream

interface BailiwickStoreReader {
    val me: Identity
    val myIdentities: List<Identity>
    val peerId: PeerId
    val peers: List<PeerId>
    val users: List<Identity>
    val circles: List<Circle>
    val posts: List<Post>
    fun accountExists(): Boolean
    fun circlePosts(circleId: Long): List<Post>
    fun actions(peerId: PeerId): List<Action>
    fun interactions(peerId: PeerId): List<Interaction>
    fun fileData(cid: ContentId): BufferedInputStream
}

interface BailiwickStoreWriter {
    fun createCircle(name: String, identity: Identity): Circle
    fun storePost(circleId: Long, post: Post)
    fun storeFile(filename: String, input: InputStream)
    fun storeAction(action: Action)
}