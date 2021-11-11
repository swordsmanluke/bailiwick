package com.perfectlunacy.bailiwick

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeyringAndroidTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java).build()

    @After
    fun deleteKeyFile() {
        val f = File("/tmp/bwcache/keystore.json")
        if(f.exists()) {
            f.delete()
        }
    }

    @Test
    fun generatingACircleKeyAllowsItToBeLoaded() {
        val cipher = NoopEncryptor()

        Keyring.generateAesKey(db.keyDao(), context.filesDir.toPath(), 0, cipher)
        val k = Keyring.keyForCircle(db.keyDao(), context.filesDir.toPath(), 0, cipher)

        assertNotNull(k)
    }

}