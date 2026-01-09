package com.perfectlunacy.bailiwick.storage

import android.util.Log
import androidx.lifecycle.LiveData
import com.perfectlunacy.bailiwick.models.Interaction
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CirclePost
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.storage.bailiwick.BailiwickStoreReader
import com.perfectlunacy.bailiwick.storage.bailiwick.BailiwickStoreWriter
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

interface BailiwickNetwork : BailiwickStoreReader, BailiwickStoreWriter

class BailiwickNetworkImpl(
    val db: BailiwickDatabase,
    override val nodeId: NodeId,
    private val filesDir: Path
) : BailiwickNetwork {

    companion object {
        private const val TAG = "BailiwickNetworkImpl"
        /** Name of the default circle that all posts are added to. */
        const val EVERYONE_CIRCLE = "everyone"
    }

    override val me: Identity
        get() = db.identityDao().findByOwner(nodeId)
            ?: throw IllegalStateException("No identity found for current node. Has the account been created?")

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

    override val postsLive: LiveData<List<Post>>
        get() = db.postDao().allLive()

    override fun accountExists(): Boolean {
        val identities = db.identityDao().identitiesFor(nodeId)
        val allIdentities = db.identityDao().all()
        Log.d(TAG, "accountExists check: nodeId=$nodeId, found ${identities.size} identities for this node, ${allIdentities.size} total identities in DB")
        allIdentities.forEach { identity ->
            Log.d(TAG, "  Identity id=${identity.id}, owner=${identity.owner}, name=${identity.name}")
        }
        return identities.isNotEmpty()
    }

    override fun circlePosts(circleId: Long): List<Post> {
        val myIdentityIds = db.identityDao().identitiesFor(nodeId).map { it.id }

        // friends authors' but not mine - we'll get my posts separately
        val authors = db.circleMemberDao()
            .membersFor(circleId)
            .filterNot { myIdentityIds.contains(it) }

        val posts = db.postDao().postsFor(authors).toMutableList()

        // Batch query to avoid N+1
        val myPostIds = db.circlePostDao().postsIn(circleId)
        val myPosts = db.postDao().findAll(myPostIds)

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
        val postId = db.postDao().insert(post)
        circlePostDao.insert(CirclePost(circleId, postId))

        // Always add things to the 'everyone' circle - if it still exists
        circles.find { it.name == EVERYONE_CIRCLE }?.also {
            if (it.id == circleId) { return@also } // don't double insert

            circlePostDao.insert(CirclePost(it.id, postId))
        }
    }

    override fun storeFile(hash: BlobHash, input: InputStream) {
        val f = Path(filesDir.pathString, "blobs", hash).toFile()
        f.parentFile?.mkdirs() // Just in case
        BufferedOutputStream(FileOutputStream(f)).use { out ->
            input.copyTo(out)
        }
    }

    override fun storeAction(action: Action) {
        db.actionDao().insert(action)
    }
}
