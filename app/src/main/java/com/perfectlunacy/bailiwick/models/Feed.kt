package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.models.ipfs.Feed as IpfsFeed
import com.perfectlunacy.bailiwick.models.ipfs.Post as IpfsPost

// TODO: Add remaining Feed types
class Feed(val identity: User, val posts: MutableList<Post>) {
    companion object {
        @JvmStatic
        fun fromIPFS(bw: Bailiwick, peerId: PeerId, author: User, feed: IpfsFeed): Feed {
            val cipher = bw.encryptorForPeer(peerId)

            val posts = feed.posts.map { postCid ->
                Post.fromIPFS(bw, author, postCid, bw.retrieve(postCid, cipher, IpfsPost::class.java)!!)
            }

            return Feed(author, posts.toMutableList())
        }
    }
}