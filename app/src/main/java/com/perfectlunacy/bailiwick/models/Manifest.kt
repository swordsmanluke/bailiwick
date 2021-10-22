package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.models.ipfs.Manifest as IpfsManifest
import com.perfectlunacy.bailiwick.models.ipfs.Feed as IpfsFeed

class Manifest(val feeds: List<Feed>) {
    companion object {
        @JvmStatic
        fun fromIPFS(bw: Bailiwick, peerId: PeerId, manifest: IpfsManifest): Manifest {
            val cipher = bw.encryptorForPeer(peerId)
            val feeds = manifest.feeds.map { feedCid ->
                val feed = bw.retrieve(feedCid, cipher, IpfsFeed::class.java)!!
                val author = User.fromIPFS(bw, cipher, feed.identity)
                // TODO: Retrieve feed names from Bailiwick account
                Feed.fromIPFS(bw, peerId, author, feed)
            }
            return Manifest(feeds)
        }
    }
}