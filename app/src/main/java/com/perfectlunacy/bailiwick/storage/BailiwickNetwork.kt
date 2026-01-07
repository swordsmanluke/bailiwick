package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.Interaction
import com.perfectlunacy.bailiwick.storage.bailiwick.BailiwickStoreReader
import com.perfectlunacy.bailiwick.storage.bailiwick.BailiwickStoreWriter
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import java.io.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

interface BailiwickNetwork : BailiwickStoreReader, BailiwickStoreWriter

class BailiwickNetworkImpl(
    val db: BailiwickDatabase,
    override val nodeId: NodeId,
    private val filesDir: Path
) : BailiwickNetwork {

    override val me: Identity
        get() = myIdentities.first()

    override val myIdentities: List<Identity>
        get() = db.identityDao().identitiesFor(nodeId)

    override val peers: List<NodeId>
        get() = db.peerDocDao().subscribedPeers().map { it.nodeId }

    override val users: List<Identity>
        get() = db.identityDao().all()

    override val circles: List<Circle>
        get() = db.circleDao().all()

    override val posts: List<Post>
        get() = db.postDao().all()

    override fun accountExists(): Boolean {
        return db.identityDao().identitiesFor(nodeId).isNotEmpty()
    }

    override fun circlePosts(circleId: Long): List<Post> {
        val myIdentities = db.identityDao().identitiesFor(nodeId).map { it.id }

        // friends authors' but not mine - we'll get my posts separately
        val authors = db.circleMemberDao()
            .membersFor(circleId)
            .filterNot { myIdentities.contains(it) }

        val posts = db.postDao().postsFor(authors).toMutableList()

        // TODO: AAAAHHHH! N+1 QUERY
        val myPosts = db.circlePostDao().postsIn(circleId).map {
            db.postDao().find(it)
        }

        posts.addAll(myPosts)

        posts.sortByDescending { it.timestamp } // newest posts on top, plz

        return posts
    }

    override fun actions(nodeId: NodeId): List<Action> {
        TODO("Not yet implemented")
    }

    override fun interactions(nodeId: NodeId): List<Interaction> {
        TODO("Not yet implemented")
    }

    override fun fileData(hash: BlobHash): BufferedInputStream {
        val f = Path(filesDir.pathString, "blobs", hash).toFile()
        return BufferedInputStream(FileInputStream(f))
    }

    override fun createCircle(name: String, identity: Identity): Circle {
        val circle = Circle(name, identity.id, null)
        val id = db.circleDao().insert(circle)
        return db.circleDao().find(id)
    }

    override fun storePost(circleId: Long, post: Post) {
        val circlePostDao = db.circlePostDao()
        circlePostDao.insert(CirclePost(circleId, db.postDao().insert(post)))

        // Always add things to the 'everyone' circle - if it still exists
        circles.find { it.name == "everyone" }?.also {
            if (it.id == circleId) { return@also } // don't double insert

            circlePostDao.insert(CirclePost(it.id, db.postDao().insert(post)))
        }
    }

    override fun storeFile(hash: BlobHash, input: InputStream) {
        val f = Path(filesDir.pathString, "blobs", hash).toFile()
        f.parentFile?.mkdirs() // Just in case
        val out = BufferedOutputStream(FileOutputStream(f))
        input.copyTo(out)
        out.close()
    }

    override fun storeAction(action: Action) {
        db.actionDao().insert(action)
    }
}
