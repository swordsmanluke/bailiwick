package com.perfectlunacy.bailiwick.workers

import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.storage.iroh.IrohDocKeys
import com.perfectlunacy.bailiwick.storage.iroh.InMemoryIrohNode
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Integration tests for action sync functionality.
 * Tests the publish/download/process cycle for actions.
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

    // ===== IrohDocKeys Tests =====

    @Test
    fun `actionKey creates correct path format`() {
        val targetNode = "abc123def456"
        val timestamp = 1704067200000L

        val key = IrohDocKeys.actionKey(targetNode, timestamp)

        assertEquals("actions/abc123def456/1704067200000", key)
    }

    @Test
    fun `actionKey with different timestamps creates unique keys`() {
        val targetNode = "node123"

        val key1 = IrohDocKeys.actionKey(targetNode, 1000L)
        val key2 = IrohDocKeys.actionKey(targetNode, 2000L)

        assertNotEquals(key1, key2)
        assertTrue(key1.startsWith("actions/node123/"))
        assertTrue(key2.startsWith("actions/node123/"))
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

    // ===== Action Publishing Flow Tests =====

    @Test
    fun `action key is stored in doc at correct path`() = runBlocking {
        val targetNodeId = bobNodeId
        val timestamp = System.currentTimeMillis()
        val actionHash = "action-blob-hash-123"

        // Simulate what ContentPublisher.publishActions does
        val key = IrohDocKeys.actionKey(targetNodeId, timestamp)
        aliceIroh.getMyDoc().set(key, actionHash.toByteArray())

        // Verify the action is stored at the expected path
        val storedHash = aliceIroh.getMyDoc().get(key)
        assertNotNull(storedHash)
        assertEquals(actionHash, String(storedHash!!))
    }

    @Test
    fun `multiple actions for same target are stored separately`() = runBlocking {
        val targetNodeId = bobNodeId
        val timestamp1 = 1000L
        val timestamp2 = 2000L

        val key1 = IrohDocKeys.actionKey(targetNodeId, timestamp1)
        val key2 = IrohDocKeys.actionKey(targetNodeId, timestamp2)

        aliceIroh.getMyDoc().set(key1, "hash1".toByteArray())
        aliceIroh.getMyDoc().set(key2, "hash2".toByteArray())

        // Both actions should be stored
        assertEquals("hash1", String(aliceIroh.getMyDoc().get(key1)!!))
        assertEquals("hash2", String(aliceIroh.getMyDoc().get(key2)!!))
    }

    // ===== Action Discovery Tests =====

    @Test
    fun `actions for target can be found by prefix search`() = runBlocking {
        val target1 = "nodeA"
        val target2 = "nodeB"

        // Store actions for different targets
        aliceIroh.getMyDoc().set(IrohDocKeys.actionKey(target1, 1000L), "hash1".toByteArray())
        aliceIroh.getMyDoc().set(IrohDocKeys.actionKey(target1, 2000L), "hash2".toByteArray())
        aliceIroh.getMyDoc().set(IrohDocKeys.actionKey(target2, 3000L), "hash3".toByteArray())

        // Find actions for target1
        val prefix = "${IrohDocKeys.KEY_ACTIONS_PREFIX}$target1/"
        val allKeys = aliceIroh.getMyDoc().keys()
        val target1Keys = allKeys.filter { it.startsWith(prefix) }

        assertEquals(2, target1Keys.size)
        assertTrue(target1Keys.all { it.startsWith(prefix) })
    }

    @Test
    fun `no actions found for unknown target`() = runBlocking {
        // Store some actions for other targets
        aliceIroh.getMyDoc().set(IrohDocKeys.actionKey("nodeA", 1000L), "hash".toByteArray())

        // Search for actions for unknown target
        val prefix = "${IrohDocKeys.KEY_ACTIONS_PREFIX}unknownNode/"
        val allKeys = aliceIroh.getMyDoc().keys()
        val unknownKeys = allKeys.filter { it.startsWith(prefix) }

        assertTrue(unknownKeys.isEmpty())
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

    // ===== End-to-End Action Sync Simulation =====

    @Test
    fun `simulated action sync between two nodes`() = runBlocking {
        // Data class matching ContentPublisher/ContentDownloader format
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

        // Step 3: Alice stores the action reference in her doc at actions/{bobNodeId}/{timestamp}
        val actionKey = IrohDocKeys.actionKey(bobNodeId, timestamp)
        aliceIroh.getMyDoc().set(actionKey, blobHash.toByteArray())

        // Step 4: Bob opens Alice's doc (simulating join/sync)
        val aliceDocId = aliceIroh.myDocNamespaceId()
        val aliceDocFromBob = bobIroh.joinDoc("ticket:$aliceDocId")
        assertNotNull(aliceDocFromBob)

        // Step 5: Bob discovers actions meant for him
        // In real sync, Alice's doc content would be synced to Bob
        // For this test, we manually copy the data
        val aliceDoc = aliceIroh.getMyDoc()
        val allKeys = aliceDoc.keys()
        val bobActionPrefix = "${IrohDocKeys.KEY_ACTIONS_PREFIX}$bobNodeId/"
        val bobActionKeys = allKeys.filter { it.startsWith(bobActionPrefix) }

        assertEquals(1, bobActionKeys.size)

        // Step 6: Bob downloads the action blob
        val storedHashBytes = aliceDoc.get(bobActionKeys[0])
        val storedHash = String(storedHashBytes!!)
        assertEquals(blobHash, storedHash)

        // In real implementation, Bob would get the blob from the network
        // For this test, we access it directly from Alice's node
        val actionBlob = aliceIroh.getBlob(storedHash)
        assertNotNull(actionBlob)

        // Step 7: Bob parses the action
        val parsedAction = GsonProvider.gson.fromJson(String(actionBlob!!), NetworkAction::class.java)
        assertEquals("UpdateKey", parsedAction.type)
        assertEquals(aesKey, parsedAction.data)

        // Step 8: Bob would create a local Action entity and process it
        val bobAction = Action(
            timestamp = parsedAction.timestamp,
            blobHash = storedHash,
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
}
