package com.perfectlunacy.bailiwick.storage

import com.perfectlunacy.bailiwick.models.ipfs.Action
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CirclePost
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Post
import com.perfectlunacy.bailiwick.models.ipfs.Interaction
import com.perfectlunacy.bailiwick.storage.bailiwick.BailiwickStoreReader
import com.perfectlunacy.bailiwick.storage.bailiwick.BailiwickStoreWriter
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import java.io.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.pathString

typealias PeerId=String
typealias ContentId=String

interface Bailiwick : BailiwickStoreReader, BailiwickStoreWriter

class BWick(val db: BailiwickDatabase, override val peerId: PeerId, private val filesDir: Path): Bailiwick {
    override val me: Identity
        get() = db.identityDao().identitiesFor(peerId).first()

    override val peers: List<PeerId>
        get() = db.subscriptionDao().all().map { it.peerId }

    override val users: List<Identity>
        get() = db.identityDao().all()

    override val circles: List<Circle>
        get() = db.circleDao().all()

    override val posts: List<Post>
        get() = db.postDao().all()

    override fun accountExists(): Boolean {
        return db.identityDao().identitiesFor(peerId).count() > 0
    }

    override fun circlePosts(circleId: Long): List<Post> {
        val myIdentities = db.identityDao().identitiesFor(peerId).map { it.id }

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

    override fun actions(peerId: PeerId): List<Action> {
        TODO("Not yet implemented")
    }

    override fun interactions(peerId: PeerId): List<Interaction> {
        TODO("Not yet implemented")
    }

    override fun fileData(cid: ContentId): BufferedInputStream {
        val f = Path(filesDir.pathString, "bwcache", cid).toFile()
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
        circles.find { it.name == "everyone"}?.also {
            if(it.id == circleId) { return@also } // don't double insert

            circlePostDao.insert(CirclePost(it.id, db.postDao().insert(post)))
        }
    }

    override fun storeFile(filename: String, input: InputStream) {
        val f = Path(filesDir.pathString, "bwcache", filename).toFile()
        f.parentFile?.mkdirs() // Just in case
        val out = BufferedOutputStream(FileOutputStream(f))
        input.copyTo(out)
    }

}