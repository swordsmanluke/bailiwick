package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.Key
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.models.db.KeyType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.security.KeyPairGenerator
import java.util.*
import kotlin.io.path.Path

class KeyringTest {

    @After
    fun deleteKeyFile() {
        val f = File("/tmp/bwcache/keystore.json")
        if(f.exists()) {
            f.delete()
        }
    }

    @Test
    fun savingAPublicKeyAllowsItToBeLoaded() {
        val cipher = NoopEncryptor()
        val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val filesDir = Path("/tmp")

        Keyring.storePubKey(filesDir, "peerId", publicKey, cipher)
        val key = Keyring.pubKeyFor(filesDir,"peerId", cipher)

        assertNotNull(key)
        assertEquals(key, keyPair.public)
    }
}