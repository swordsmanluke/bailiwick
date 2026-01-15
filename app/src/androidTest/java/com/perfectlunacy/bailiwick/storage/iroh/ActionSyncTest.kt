package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.models.db.PeerTopic
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.util.GsonProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Integration tests for the action publish/download/process cycle.
 * Tests the complete flow of UpdateKey actions between peers.
 */
@RunWith(AndroidJUnit4::class)
class ActionSyncTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var iroh: InMemoryIrohNode
    private lateinit var nodeId: String

    // Simulated peer
    private lateinit var peerIroh: InMemoryIrohNode
    private lateinit var peerDb: BailiwickDatabase
    private lateinit var peerNodeId: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory databases for testing
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        peerDb = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create Iroh nodes
        iroh = InMemoryIrohNode()
        peerIroh = InMemoryIrohNode()
        
        // Cache node IDs
        nodeId = runBlocking { iroh.nodeId() }
        peerNodeId = runBlocking { peerIroh.nodeId() }
    }

    @After
    fun tearDown() {
        db.close()
        peerDb.close()
    }

    @Test
    fun actionCanBeCreatedAndStored() {
        val action = Action.updateKeyAction("peer-node-id-123", "encrypted-aes-key-base64")

        val id = db.actionDao().insert(action)

        assertTrue(id > 0)

        val retrieved = db.actionDao().find(id)
        assertEquals(ActionType.UpdateKey, retrieved.actionType)
        assertEquals("peer-node-id-123", retrieved.toPeerId)
        assertEquals("encrypted-aes-key-base64", retrieved.data)
        assertTrue(retrieved.processed)  // Our own actions are pre-processed
    }

    @Test
    fun actionsInNeedOfSyncHaveNullBlobHash() {
        // Create action without blob hash
        val action = Action.updateKeyAction("peer-1", "key-1")
        db.actionDao().insert(action)

        val needsSync = db.actionDao().inNeedOfSync()

        assertEquals(1, needsSync.size)
        assertNull(needsSync[0].blobHash)
    }

    @Test
    fun actionHashCanBeUpdated() {
        val action = Action.updateKeyAction("peer-1", "key-1")
        val id = db.actionDao().insert(action)

        db.actionDao().updateHash(id, "test-blob-hash-123")

        val retrieved = db.actionDao().find(id)
        assertEquals("test-blob-hash-123", retrieved.blobHash)
    }

    @Test
    fun actionCanBeMarkedAsProcessed() {
        // Simulate receiving an action from a peer
        val action = Action(
            timestamp = Calendar.getInstance().timeInMillis,
            blobHash = "some-hash",
            fromPeerId = "remote-peer",
            toPeerId = nodeId,
            actionType = ActionType.UpdateKey,
            data = "encrypted-key",
            processed = false
        )
        val id = db.actionDao().insert(action)

        // Verify it's unprocessed
        val unprocessed = db.actionDao().inNeedOfProcessing()
        assertEquals(1, unprocessed.size)

        // Mark as processed
        db.actionDao().markProcessed(id)

        // Verify it's no longer in need of processing
        val stillUnprocessed = db.actionDao().inNeedOfProcessing()
        assertEquals(0, stillUnprocessed.size)
    }

    @Test
    fun actionExistsCheckWorksCorrectly() {
        val action = Action.updateKeyAction("peer-1", "key-1")
        val id = db.actionDao().insert(action)
        db.actionDao().updateHash(id, "unique-action-hash")

        assertTrue(db.actionDao().actionExists("unique-action-hash"))
        assertFalse(db.actionDao().actionExists("nonexistent-hash"))
    }

    @Test
    fun actionsForPeerReturnsCorrectActions() {
        // Create actions for different peers
        val action1 = Action.updateKeyAction("peer-1", "key-1")
        val action2 = Action.updateKeyAction("peer-2", "key-2")
        val action3 = Action.updateKeyAction("peer-1", "key-3")

        db.actionDao().insert(action1)
        db.actionDao().insert(action2)
        db.actionDao().insert(action3)

        val peer1Actions = db.actionDao().actionsFor("peer-1")
        val peer2Actions = db.actionDao().actionsFor("peer-2")

        assertEquals(2, peer1Actions.size)
        assertEquals(1, peer2Actions.size)
    }

    @Test
    fun actionSerializationRoundTrip() {
        val action = Action.updateKeyAction("target-peer", "base64-encrypted-key")
        action.id = 42

        // Simulate serialization for network
        val json = GsonProvider.gson.toJson(action)
        val deserialized = GsonProvider.gson.fromJson(json, Action::class.java)

        assertEquals(action.toPeerId, deserialized.toPeerId)
        assertEquals(action.actionType, deserialized.actionType)
        assertEquals(action.data, deserialized.data)
    }

    @Test
    fun multipleActionsCanBeBatchQueried() {
        val nodeIds = listOf("peer-1", "peer-2", "peer-3")

        // Create actions for each peer
        nodeIds.forEach { nodeId ->
            db.actionDao().insert(Action.updateKeyAction(nodeId, "key-for-$nodeId"))
        }

        // Batch query
        val actions = db.actionDao().actionsFor(nodeIds)

        assertEquals(3, actions.size)
    }

    @Test
    fun downloadedActionHasFromPeerIdSet() {
        // Simulate receiving an action from a peer
        val receivedAction = Action(
            timestamp = Calendar.getInstance().timeInMillis,
            blobHash = "action-blob-hash",
            fromPeerId = peerNodeId,  // Set by receiver
            toPeerId = nodeId,         // Target is us
            actionType = ActionType.UpdateKey,
            data = "encrypted-key-from-peer",
            processed = false
        )

        val id = db.actionDao().insert(receivedAction)
        val retrieved = db.actionDao().find(id)

        assertEquals(peerNodeId, retrieved.fromPeerId)
        assertFalse(retrieved.processed)
    }

    @Test
    fun peerTopicCanBeUsedForActionDiscovery() = runBlocking {
        // Register a peer with topic key
        val topicKey = ByteArray(32) { it.toByte() }
        val ed25519Key = ByteArray(32) { (it + 100).toByte() }
        
        val peerTopic = PeerTopic(
            nodeId = peerNodeId,
            ed25519PublicKey = ed25519Key,
            topicKey = topicKey,
            addresses = listOf("mock://address"),
            displayName = "Test Peer",
            isSubscribed = true,
            lastSyncedAt = 0
        )
        db.peerTopicDao().upsert(peerTopic)

        // Verify we can find the peer's topic
        val retrieved = db.peerTopicDao().findByNodeId(peerNodeId)
        assertNotNull(retrieved)
        assertEquals(peerNodeId, retrieved!!.nodeId)
        assertArrayEquals(topicKey, retrieved.topicKey)
    }
}
