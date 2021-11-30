package com.perfectlunacy.bailiwick.workers.runners.publishers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.IpfsFeed
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner
import java.util.*

class CirclePublisher(
    private val circleDao: CircleDao,
    private val identityDao: IdentityDao,
    private val identityPublisher: IdentityPublisher,
    private val postPublisher: PostPublisher,
    private val ipfs: IPFS
) {

    fun publish(circle: Circle, syncPosts: List<Post>, cipher: Encryptor) {
            Log.i(PublishRunner.TAG, "Syncing circle: '${circle.name}'")

            publishIdentity(circle, cipher)
            val postCids = publishPosts(syncPosts, circle, cipher)
            publishCircle(circle, postCids, cipher)
    }

    private fun publishIdentity(circle: Circle, cipher: Encryptor) {
        identityPublisher.publish(circle.identityId, cipher)
    }

    private fun publishPosts(posts: List<Post>, circle: Circle, cipher: Encryptor): List<ContentId> {
        if(posts.isEmpty()) { return emptyList() } // Nothing to publish

        Log.i(PublishRunner.TAG, "Uploading ${posts.count()} new post(s)!")
        circleDao.clearCid(circle.id)
        return posts.map { post ->
            postPublisher.publish(post, cipher)
        }
    }

    private fun publishCircle(circle: Circle, postCids: List<ContentId>, cipher: Encryptor) {
        // If we're up to date - don't publish
        if(circleDao.find(circle.id).cid != null) { return }

        val identityCid = identityDao.find(circle.identityId).cid!!
        val now = Calendar.getInstance().timeInMillis
        val actions: List<ContentId> = listOf() // TODO

        // Feeds are Circles from the subscriber perspective.
        val feed = IpfsFeed(now, postCids, listOf(), actions, identityCid)
        val cid = feed.toIpfs(cipher, ipfs)
        circleDao.storeCid(circle.id, cid)
    }
}