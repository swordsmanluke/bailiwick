package com.perfectlunacy.bailiwick.workers.runners

import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.models.*
import com.perfectlunacy.bailiwick.models.db.IpnsCache
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao
import com.perfectlunacy.bailiwick.signatures.Sha1Signature
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.MockIPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSCache
import io.bloco.faker.Faker
import org.junit.Assert.*

import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.*
import java.util.*
import javax.crypto.KeyGenerator

class RefreshRunnerTest {

    @Test
    fun run_downloads_all_files() {
        val key = KeyGenerator.getInstance("AES").generateKey()
        val cipher = AESEncryptor(key)
        var cache = IPFSCache("/tmp")
        val ipfs = MockIPFS("/tmp")
        val mCid = manifest(ipfs, cache, cipher)

        // Create a new cache that won't have files in it
        cache = IPFSCache("/tmp/bw2")

        val ipns = Mockito.mock(IpnsCacheDao::class.java)
        `when`(ipns.getPath("myPeerId", "")).thenReturn(IpnsCache(0, "myPeerId", "", "/tmp", 1))
        `when`(ipns.getPath("myPeerId", "bw")).thenReturn(IpnsCache(0, "myPeerId", "", "/tmp/bw", 1))
        `when`(ipns.getPath("myPeerId", "bw/0.2")).thenReturn(IpnsCache(0, "myPeerId", "", "/tmp/bw/0.2", 1))
        `when`(ipns.getPath("myPeerId", "bw/0.2/manifest.json")).thenReturn(IpnsCache(0, "myPeerId", "", mCid, 1))

        val keys = mock(Keyring::class.java)
        `when`(keys.secretKeys(anyString())).thenReturn(listOf(Base64.getEncoder().encodeToString(key.encoded)))
        `when`(keys.encryptorForPeer(anyString())).thenReturn(cipher)

        val runner = RefreshRunner(listOf("myPeerId"), keys, cache, ipns, ipfs)

        runner.run() // download files

        val manifest = Manifest(cipher, cache, mCid)

        assertEquals(1, manifest.feeds.first().posts.size)
        assertEquals(1, manifest.feeds.first().actions.size)

    }

    // Create an encrypted manifest with a Feed with a single post and action
    private fun manifest(ipfs: MockIPFS, ipfsCache: IPFSCache, cipher: AESEncryptor): ContentId {
        val text = Faker().lorem.sentence()
        val idCid = UserIdentity.create(ipfs, ipfsCache, cipher, "Starbuck", "")
        val postCid = Post.create(ipfs, ipfsCache, Sha1Signature(), cipher, null, text, listOf())
        val actionCid = Action.updateKeyAction(ipfs, ipfsCache, cipher, "some key")
        val feedCid = Feed.create(ipfsCache, ipfs, idCid, listOf(postCid), listOf(actionCid), cipher)
        val manifestCid = Manifest.create(ipfsCache, ipfs, feedCid, cipher)

        return manifestCid
    }
}