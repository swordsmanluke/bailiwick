package com.perfectlunacy.bailiwick.workers.runners.publishers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.CircleDao
import com.perfectlunacy.bailiwick.models.ipfs.IpfsFeed
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner
import java.util.*

class CirclePublisher(
    private val circleDao: CircleDao,
    private val ipfs: IPFS
) {

    fun publish(circle: Circle,
                idCid: ContentId,
                postCids: List<ContentId>,
                interactionCids: List<ContentId>,
                actionCids: List<ContentId>,
                cipher: Encryptor) {
        Log.i(PublishRunner.TAG, "Syncing circle: '${circle.name}'")

        // If we're up to date - don't publish
        if(circleDao.find(circle.id).cid != null) { return }

        val now = Calendar.getInstance().timeInMillis

        // Feeds are Circles from the subscriber perspective.
        val feed = IpfsFeed(now, postCids, interactionCids, actionCids, idCid)
        val cid = feed.toIpfs(cipher, ipfs)
        circleDao.storeCid(circle.id, cid)
    }
}