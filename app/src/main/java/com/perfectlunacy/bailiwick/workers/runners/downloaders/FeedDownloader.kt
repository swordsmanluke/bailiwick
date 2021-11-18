package com.perfectlunacy.bailiwick.workers.runners.downloaders

import android.util.Log
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.IpfsFeed
import com.perfectlunacy.bailiwick.models.ipfs.IpfsIdentity
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsReader
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import java.nio.file.Path

class FeedDownloader(private val keyDao: KeyDao,
                     private val identityDownloader: IdentityDownloader,
                     private val postDownloader: PostDownloader,
                     private val ipfs: IpfsReader) {
    companion object {
        const val TAG = "FeedDownloader"
    }

    fun download(cid: ContentId, peerId: PeerId, cipher: Encryptor) {
        val feedPair = IpfsDeserializer.fromCid(cipher, ipfs, cid, IpfsFeed::class.java) ?: return
        val feed = feedPair.first

        // Download the identity if we haven't already
        val idCipher = Keyring.encryptorForPeer(
            keyDao,
            peerId,
            ValidatorFactory.jsonValidator(IpfsIdentity::class.java)
        )

        val identity = identityDownloader.download(feed.identity, peerId, idCipher)

        Log.i(DownloadRunner.TAG, "Trying ${feed.posts.count()} posts")
        for (postCid in feed.posts) {
            postDownloader.download(postCid, identity.id, cipher)
        }
    }
}