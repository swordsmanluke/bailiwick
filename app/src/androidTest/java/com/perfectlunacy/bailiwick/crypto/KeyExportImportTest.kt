package com.perfectlunacy.bailiwick.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.db.Identity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator

/**
 * Tests for KeyExporter and KeyImporter.
 */
@RunWith(AndroidJUnit4::class)
class KeyExportImportTest {

    private lateinit var context: Context
    private lateinit var exporter: KeyExporter
    private lateinit var importer: KeyImporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exporter = KeyExporter()
        importer = KeyImporter()
    }

    @Test
    fun exportAndImportRoundTrip() {
        // Generate test keys
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        val identity = Identity(
            blobHash = null,
            owner = "test-node-id-abc123",
            name = "Test User",
            profilePicHash = "avatar-blob-hash"
        )

        val password = "securePassword123!"

        // Export
        val exportStream = ByteArrayOutputStream()
        exporter.export(identity, keyPair.private, keyPair.public, password, exportStream)

        val exportedData = exportStream.toByteArray()
        assertTrue(exportedData.isNotEmpty())

        // Import
        val importStream = ByteArrayInputStream(exportedData)
        val result = importer.import(importStream, password)

        assertTrue(result is KeyImporter.ImportResult.Success)
        val success = result as KeyImporter.ImportResult.Success

        assertEquals(identity.name, success.identity.name)
        assertEquals(identity.owner, success.identity.owner)
        assertEquals(identity.profilePicHash, success.identity.profilePicHash)

        // Verify keys are usable
        assertNotNull(success.privateKey)
        assertNotNull(success.publicKey)
        assertEquals("RSA", success.privateKey.algorithm)
        assertEquals("RSA", success.publicKey.algorithm)
    }

    @Test
    fun importWithWrongPasswordFails() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        val identity = Identity(null, "node-id", "Test", "hash")
        val correctPassword = "correctPassword123!"
        val wrongPassword = "wrongPassword456!"

        // Export with correct password
        val exportStream = ByteArrayOutputStream()
        exporter.export(identity, keyPair.private, keyPair.public, correctPassword, exportStream)

        // Import with wrong password
        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        val result = importer.import(importStream, wrongPassword)

        assertTrue(result is KeyImporter.ImportResult.Error)
        val error = result as KeyImporter.ImportResult.Error
        assertTrue(error.message.contains("Invalid password") || error.message.contains("corrupted"))
    }

    @Test
    fun exportRequiresMinimumPasswordLength() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        val identity = Identity(null, "node-id", "Test", "hash")
        val shortPassword = "short"  // Less than 8 characters

        val exportStream = ByteArrayOutputStream()

        try {
            exporter.export(identity, keyPair.private, keyPair.public, shortPassword, exportStream)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("8 characters"))
        }
    }

    @Test
    fun importCorruptedFileFails() {
        val corruptedData = "{ invalid json content }".toByteArray()
        val importStream = ByteArrayInputStream(corruptedData)

        val result = importer.import(importStream, "anyPassword")

        assertTrue(result is KeyImporter.ImportResult.Error)
    }

    @Test
    fun exportedFileContainsVersion() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        val identity = Identity(null, "node-id", "Test", "hash")

        val exportStream = ByteArrayOutputStream()
        exporter.export(identity, keyPair.private, keyPair.public, "password123!", exportStream)

        val exportedJson = String(exportStream.toByteArray())
        assertTrue(exportedJson.contains("\"version\""))
        assertTrue(exportedJson.contains("\"salt\""))
        assertTrue(exportedJson.contains("\"iv\""))
        assertTrue(exportedJson.contains("\"encryptedData\""))
    }

    @Test
    fun importPreservesTimestamps() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        val identity = Identity(null, "node-id", "Test", "hash")
        val password = "password123!"

        val beforeExport = System.currentTimeMillis()

        val exportStream = ByteArrayOutputStream()
        exporter.export(identity, keyPair.private, keyPair.public, password, exportStream)

        val afterExport = System.currentTimeMillis()

        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        val result = importer.import(importStream, password) as KeyImporter.ImportResult.Success

        assertTrue(result.exportedAt >= beforeExport)
        assertTrue(result.exportedAt <= afterExport)
    }
}
