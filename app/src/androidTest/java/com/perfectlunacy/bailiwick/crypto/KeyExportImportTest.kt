package com.perfectlunacy.bailiwick.crypto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * Tests for KeyExporter and KeyImporter.
 */
@RunWith(AndroidJUnit4::class)
class KeyExportImportTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase

    private val testSecretKey = "dGVzdC1zZWNyZXQta2V5LWJhc2U2NC1lbmNvZGVk" // Base64 test key
    private val testUsername = "testuser"
    private val testDisplayName = "Test User"
    private val testAvatarHash = "avatar-blob-hash-12345"
    private val testNodeId = "test-node-id-abc123"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory database
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Set up test account and identity
        val account = Account(
            username = testUsername,
            passwordHash = "password-hash",
            peerId = testNodeId,
            rootCid = "",
            sequence = 0,
            loggedIn = true
        )
        db.accountDao().insert(account)

        val identity = Identity(
            blobHash = null,
            owner = testNodeId,
            name = testDisplayName,
            profilePicHash = testAvatarHash
        )
        db.identityDao().insert(identity)

        // Set up SharedPreferences with test secret key
        val prefs = context.getSharedPreferences("iroh_config", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("iroh_secret_key", testSecretKey)
            .commit()
    }

    @After
    fun tearDown() {
        db.close()

        // Clean up SharedPreferences
        val prefs = context.getSharedPreferences("iroh_config", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun exportAndImportRoundTrip() = runBlocking {
        val password = "securePassword123!"

        // Export
        val exportedData = KeyExporter.export(context, db, password)
        assertTrue("Exported data should not be empty", exportedData.isNotEmpty())

        // Import
        val importStream = ByteArrayInputStream(exportedData)
        val result = KeyImporter.import(importStream, password)

        assertTrue("Import should succeed", result is KeyImporter.ImportResult.Success)
        val success = result as KeyImporter.ImportResult.Success

        assertEquals(testDisplayName, success.data.displayName)
        assertEquals(testUsername, success.data.username)
        assertEquals(testAvatarHash, success.data.avatarHash)
        assertEquals(testSecretKey, success.data.secretKey)
        assertTrue("createdAt should be recent", success.data.createdAt > 0)
    }

    @Test
    fun importWithWrongPasswordFails() = runBlocking {
        val correctPassword = "correctPassword123!"
        val wrongPassword = "wrongPassword456!"

        // Export with correct password
        val exportedData = KeyExporter.export(context, db, correctPassword)

        // Import with wrong password
        val importStream = ByteArrayInputStream(exportedData)
        val result = KeyImporter.import(importStream, wrongPassword)

        assertTrue(
            "Import should fail with wrong password",
            result is KeyImporter.ImportResult.WrongPassword
        )
    }

    @Test
    fun importCorruptedFileFails() {
        val corruptedData = "{ invalid json content }".toByteArray()
        val importStream = ByteArrayInputStream(corruptedData)

        val result = KeyImporter.import(importStream, "anyPassword123!")

        assertTrue(
            "Import should fail with invalid format",
            result is KeyImporter.ImportResult.InvalidFormat ||
                    result is KeyImporter.ImportResult.WrongPassword ||
                    result is KeyImporter.ImportResult.Error
        )
    }

    @Test
    fun exportedDataHasCorrectFormat() = runBlocking {
        val password = "password12345678!"

        val exportedData = KeyExporter.export(context, db, password)

        // Export format: salt (16 bytes) + iv (12 bytes) + encrypted data
        assertTrue("Exported data should have at least salt + iv", exportedData.size >= 28)

        // First 16 bytes are salt
        val salt = exportedData.copyOfRange(0, 16)
        assertFalse("Salt should not be all zeros", salt.all { it == 0.toByte() })

        // Next 12 bytes are IV
        val iv = exportedData.copyOfRange(16, 28)
        assertFalse("IV should not be all zeros", iv.all { it == 0.toByte() })

        // Remaining bytes are encrypted data
        val ciphertext = exportedData.copyOfRange(28, exportedData.size)
        assertTrue("Ciphertext should not be empty", ciphertext.isNotEmpty())
    }

    @Test
    fun importPreservesTimestamps() = runBlocking {
        val password = "password123!"

        val beforeExport = System.currentTimeMillis()
        val exportedData = KeyExporter.export(context, db, password)
        val afterExport = System.currentTimeMillis()

        val importStream = ByteArrayInputStream(exportedData)
        val result = KeyImporter.import(importStream, password) as KeyImporter.ImportResult.Success

        assertTrue(
            "createdAt should be at or after export start",
            result.data.createdAt >= beforeExport
        )
        assertTrue(
            "createdAt should be at or before export end",
            result.data.createdAt <= afterExport
        )
    }

    @Test
    fun getRecommendedFilenameFormatsCorrectly() {
        val filename = KeyExporter.getRecommendedFilename("Test User")

        assertTrue("Filename should start with bailiwick-", filename.startsWith("bailiwick-"))
        assertTrue("Filename should end with .bwkey", filename.endsWith(".bwkey"))
        assertTrue("Filename should contain sanitized name", filename.contains("Test_User"))
    }

    @Test
    fun exportFailsWithoutAccount() = runBlocking {
        // Clear the account
        db.clearAllTables()

        try {
            KeyExporter.export(context, db, "password123!")
            fail("Export should throw ExportException when no account exists")
        } catch (e: KeyExporter.ExportException) {
            assertTrue(e.message!!.contains("No account"))
        }
    }

    @Test
    fun exportFailsWithoutSecretKey() = runBlocking {
        // Clear the secret key from SharedPreferences
        val prefs = context.getSharedPreferences("iroh_config", Context.MODE_PRIVATE)
        prefs.edit().remove("iroh_secret_key").commit()

        try {
            KeyExporter.export(context, db, "password123!")
            fail("Export should throw ExportException when no secret key exists")
        } catch (e: KeyExporter.ExportException) {
            assertTrue(e.message!!.contains("secret key"))
        }
    }
}
