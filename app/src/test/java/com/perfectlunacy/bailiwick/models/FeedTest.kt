package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.MockIPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import io.bloco.faker.Faker
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.KeyGenerator

class FeedTest {

    @Test
    fun when_it_cannot_decrypt_a_feed_it_returns_empty_lists(){
        val cipher = AESEncryptor(KeyGenerator.getInstance("AES").generateKey())
        val ipfsCache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")

        // Create an encrypted Feed with a single post
        val feedCid = feed(ipfs, ipfsCache, cipher)
        // Now, fail to decrypt it
        val feed = Feed(NoopEncryptor(), ipfsCache, feedCid)

        assertEquals(0, feed.posts.size)
        assertEquals(0, feed.postCids.size)
        assertEquals(0, feed.actions.size)
        assertEquals(0, feed.actionCids.size)
    }

    @Test
    fun when_it_decrypts_a_feed_it_returns_posts_and_actions(){
        val cipher = AESEncryptor(KeyGenerator.getInstance("AES").generateKey())
        val ipfsCache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")

        // Create an encrypted Feed with a single post
        val feedCid = feed(ipfs, ipfsCache, cipher)

        // Now, successfully decrypt it
        val feed = Feed(cipher, ipfsCache, feedCid)

        assertEquals(1, feed.posts.size)
        assertEquals(1, feed.actions.size)

    }

    // Create an encrypted Feed with a single post and action
    private fun feed(ipfs: MockIPFS, ipfsCache: IPFSCache, cipher: AESEncryptor): ContentId {
        val text = Faker().lorem.sentence()
        val postCid = Post.create(ipfs, ipfsCache, Sha1Signature(), cipher, null, text, listOf())
        val actionCid = Action.updateKeyAction(ipfs, ipfsCache, cipher, "some key")

        val feedCid = Feed.create(ipfsCache, ipfs, "", listOf(postCid), listOf(actionCid), cipher)

        return feedCid
    }

}