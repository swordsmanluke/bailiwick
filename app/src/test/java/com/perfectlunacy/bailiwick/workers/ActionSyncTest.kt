package com.perfectlunacy.bailiwick.workers

import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.storage.iroh.InMemoryIrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Integration tests for action sync functionality.
 * Tests the serialization and blob storage for actions.
 */
class ActionSyncTest {

    private lateinit var aliceIroh: InMemoryIrohNode
    private lateinit var bobIroh: InMemoryIrohNode
    private lateinit var aliceNodeId: String
    private lateinit var bobNodeId: String

    @Before
    fun setUp() = runBlocking {
        aliceIroh = InMemoryIrohNode()
        bobIroh = InMemoryIrohNode()
        aliceNodeId = aliceIroh.nodeId()
        bobNodeId = bobIroh.nodeId()
    }

    // ===== Action Entity Tests =====

    @Test
    fun `updateKeyAction creates action with correct fields`() {
        val targetNodeId = "targetNode123"
        val aesKey = Base64.getEncoder().encodeToString("test-aes-key-bytes".toByteArray())

        val action = Action.updateKeyAction(targetNodeId, aesKey)

        assertEquals(targetNodeId, action.toPeerId)
        assertEquals(ActionType.UpdateKey, action.actionType)
        assertEquals(aesKey, action.data)
        assertNull(action.blobHash)  // Not yet published
        assertNull(action.fromPeerId)  // Our own action
        assertTrue(action.processed)  // We don't need to process our own action
    }

    @Test
    fun `action fromPeerId is null for locally created actions`() {
        val action = Action.updateKeyAction("target", "key")

        assertNull(action.fromPeerId)
    }

    // ===== NetworkAction Serialization Tests =====

    @Test
    fun `NetworkAction serializes and deserializes correctly`() {
        // Create a network action like ContentPublisher does
        data class NetworkAction(
            val type: String,
            val data: String,
            val timestamp: Long
        )

        val original = NetworkAction(
            type = "UpdateKey",
            data = "base64encodedkey==",
            timestamp = System.currentTimeMillis()
        )

        val json = GsonProvider.gson.toJson(original)
        val parsed = GsonProvider.gson.fromJson(json, NetworkAction::class.java)

        assertEquals(original.type, parsed.type)
        assertEquals(original.data, parsed.data)
        assertEquals(original.timestamp, parsed.timestamp)
    }

    // ===== Action Blob Storage Tests =====

    @Test
    fun `action blob can be stored and retrieved`() = runBlocking {
        data class NetworkAction(
            val type: String,
            val data: String,
            val timestamp: Long
        )

        val networkAction = NetworkAction(
            type = "UpdateKey",
            data = "aes-key-base64",
            timestamp = System.currentTimeMillis()
        )

        val json = GsonProvider.gson.toJson(networkAction)
        val hash = aliceIroh.storeBlob(json.toByteArray())

        // Retrieve and verify
        val retrieved = aliceIroh.getBlob(hash)
        assertNotNull(retrieved)

        val parsed = GsonProvider.gson.fromJson(String(retrieved!!), NetworkAction::class.java)
        assertEquals(networkAction.type, parsed.type)
        assertEquals(networkAction.data, parsed.data)
    }

    // ===== Key Extraction Tests =====

    @Test
    fun `base64 key can be decoded correctly`() {
        val originalKey = "my-secret-aes-key-32-bytes!!"
        val encoded = Base64.getEncoder().encodeToString(originalKey.toByteArray())

        // Simulate what processUpdateKeyAction does
        val decoded = Base64.getDecoder().decode(encoded)

        assertEquals(originalKey, String(decoded))
    }

    @Test
    fun `ActionType enum parses correctly from string`() {
        val typeString = "UpdateKey"
        val actionType = ActionType.valueOf(typeString)

        assertEquals(ActionType.UpdateKey, actionType)
    }

    @Test
    fun `unknown ActionType throws exception`() {
        val unknownType = "UnknownAction"

        assertThrows(IllegalArgumentException::class.java) {
            ActionType.valueOf(unknownType)
        }
    }

    // ===== Simulated Action Sync =====

    @Test
    fun `action can be serialized, stored, and parsed`() = runBlocking {
        data class NetworkAction(
            val type: String,
            val data: String,
            val timestamp: Long
        )

        // Step 1: Alice creates an UpdateKey action for Bob
        val aesKey = Base64.getEncoder().encodeToString("secret-key-123".toByteArray())
        val timestamp = System.currentTimeMillis()

        val networkAction = NetworkAction(
            type = "UpdateKey",
            data = aesKey,
            timestamp = timestamp
        )

        // Step 2: Alice stores the action as a blob
        val json = GsonProvider.gson.toJson(networkAction)
        val blobHash = aliceIroh.storeBlob(json.toByteArray())

        // Step 3: Bob downloads the action blob
        val actionBlob = aliceIroh.getBlob(blobHash)
        assertNotNull(actionBlob)

        // Step 4: Bob parses the action
        val parsedAction = GsonProvider.gson.fromJson(String(actionBlob!!), NetworkAction::class.java)
        assertEquals("UpdateKey", parsedAction.type)
        assertEquals(aesKey, parsedAction.data)

        // Step 5: Bob would create a local Action entity and process it
        val bobAction = Action(
            timestamp = parsedAction.timestamp,
            blobHash = blobHash,
            fromPeerId = aliceNodeId,  // Alice sent this
            toPeerId = bobNodeId,       // It's for Bob
            actionType = ActionType.UpdateKey,
            data = parsedAction.data,
            processed = false
        )

        assertEquals(aliceNodeId, bobAction.fromPeerId)
        assertEquals(bobNodeId, bobAction.toPeerId)
        assertEquals(ActionType.UpdateKey, bobAction.actionType)
        assertFalse(bobAction.processed)
    }
}
