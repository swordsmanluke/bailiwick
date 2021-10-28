package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.MockIPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import org.junit.Assert
import org.junit.Test

class ManifestTest {

    @Test
    fun create_works(){
        val cache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")
        val m = Manifest.create(cache, ipfs, "", NoopEncryptor())

        Assert.assertNotNull(m)
    }

    @Test
    fun cache_retrieval_works(){
        val cache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")
        val mCid = Manifest.create(cache, ipfs, "feedCid", NoopEncryptor())

        val m = Manifest(NoopEncryptor(), cache, mCid)
        Assert.assertEquals(listOf("feedCid"), m.feedCids)
    }

    @Test
    fun feed_retrieval_works() {
        val cache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")
        val picCid = "app/src/main/assets/avatar.png"
        val cipher = NoopEncryptor()
        val identityCid = UserIdentity.create(ipfs, cache, cipher, "My Name", picCid)
        val feed = Feed.create(cache, ipfs, identityCid, emptyList(), emptyList(), cipher)
        val mCid = Manifest.create(cache, ipfs, feed, cipher)

        val m = Manifest(cipher, cache, mCid)
        Assert.assertEquals(1, m.feeds.size)
    }

    @Test
    fun post_retrieval_works() {
        val cache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")
        val picCid = "app/src/main/assets/avatar.png"
        val cipher = NoopEncryptor()
        val signer = Sha1Signature()

        val post = Post.create(ipfs, cache, signer, cipher, null, "Post Text", emptyList())
        val identityCid = UserIdentity.create(ipfs, cache, cipher, "My Name", picCid)
        val feed = Feed.create(cache, ipfs, identityCid, listOf(post), emptyList(), cipher)
        val mCid = Manifest.create(cache, ipfs, feed, cipher)

        val m = Manifest(cipher, cache, mCid)
        Assert.assertEquals(1, m.feeds.first().posts.size)
        Assert.assertEquals("Post Text", m.feeds.first().posts.first().text)
    }

    @Test
    fun action_retrieval_works() {
        val cache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")
        val picCid = "app/src/main/assets/avatar.png"
        val cipher = NoopEncryptor()

        val action = Action.updateKeyAction(ipfs, cache, cipher, "new key")
        val identityCid = UserIdentity.create(ipfs, cache, cipher, "My Name", picCid)
        val feed = Feed.create(cache, ipfs, identityCid, listOf(), listOf(action), cipher)
        val mCid = Manifest.create(cache, ipfs, feed, cipher)

        val m = Manifest(cipher, cache, mCid)
        Assert.assertEquals(1, m.feeds.first().actions.size)
        Assert.assertEquals("new key", m.feeds.first().actions.first().get("key"))
    }

}