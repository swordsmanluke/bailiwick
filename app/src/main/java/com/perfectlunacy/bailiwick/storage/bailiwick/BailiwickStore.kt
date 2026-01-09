package com.perfectlunacy.bailiwick.storage.bailiwick

import androidx.lifecycle.LiveData
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.Interaction
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import java.io.BufferedInputStream
import java.io.InputStream

interface BailiwickStoreReader {
    val me: Identity
    val myIdentities: List<Identity>
    val nodeId: NodeId
    val peers: List<NodeId>
    val users: List<Identity>
    val circles: List<Circle>
    val posts: List<Post>
    val postsLive: LiveData<List<Post>>
    fun accountExists(): Boolean
    fun circlePosts(circleId: Long): List<Post>
    fun actions(nodeId: NodeId): List<Action>      // Was: peerId
    fun interactions(nodeId: NodeId): List<Interaction>  // Was: peerId
    fun fileData(hash: BlobHash): BufferedInputStream    // Was: cid: ContentId
}

interface BailiwickStoreWriter {
    fun createCircle(name: String, identity: Identity): Circle
    fun storePost(circleId: Long, post: Post)
    fun storeFile(hash: BlobHash, input: InputStream)    // Was: filename: String
    fun storeAction(action: Action)
}
