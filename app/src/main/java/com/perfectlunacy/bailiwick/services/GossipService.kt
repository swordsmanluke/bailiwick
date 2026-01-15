package com.perfectlunacy.bailiwick.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.ciphers.Ed25519Keyring
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.crypto.EncryptorFactory
import com.perfectlunacy.bailiwick.crypto.KeyEncryption
import com.perfectlunacy.bailiwick.crypto.KeyStorage
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.models.db.PeerTopic
import com.perfectlunacy.bailiwick.models.iroh.*
import com.perfectlunacy.bailiwick.storage.gossip.GossipMessageHandler
import com.perfectlunacy.bailiwick.storage.gossip.GossipWrapper
import com.perfectlunacy.bailiwick.storage.gossip.TopicSubscription
import com.perfectlunacy.bailiwick.util.GsonProvider
import com.perfectlunacy.bailiwick.workers.ContentDownloader
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Background service for Gossip-based manifest synchronization.
 *
 * Replaces IrohService with real-time Gossip announcements:
 * - Subscribes to own topic to publish manifest announcements
 * - Subscribes to peer topics to receive their announcements
 * - Downloads and processes manifests when announcements arrive
 *
 * Key behaviors:
 * - Real-time: Announcements broadcast immediately on content change
 * - Version-based: Only downloads manifest if version > lastKnownVersion
 * - Swarm delivery: Messages propagate through any connected peer
 */
class GossipService : Service() {
    companion object {
        private const val TAG = "GossipService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "bailiwick_gossip"

        @Volatile
        private var instance: GossipService? = null

        fun start(context: Context) {
            val intent = Intent(context, GossipService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GossipService::class.java)
            context.stopService(intent)
        }

        /**
         * Get the running instance (if available) to trigger manifest publish.
         */
        fun getInstance(): GossipService? = instance
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var gossipWrapper: GossipWrapper? = null
    private var myTopicSubscription: TopicSubscription? = null
    private val peerSubscriptions = ConcurrentHashMap<String, TopicSubscription>()
    private var initJob: Job? = null
    private var contentDownloader: ContentDownloader? = null

    // Track connected peers for status updates
    private val connectedPeers = ConcurrentHashMap.newKeySet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting GossipService")
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        initializeGossip()
        return START_STICKY
    }

    private fun initializeGossip() {
        initJob?.cancel()
        initJob = scope.launch {
            try {
                val bw = try {
                    Bailiwick.getInstance()
                } catch (e: Exception) {
                    Log.w(TAG, "Bailiwick not initialized, retrying in 2 seconds")
                    updateNotification("Waiting for initialization...")
                    delay(2000)
                    initializeGossip()
                    return@launch
                }

                val iroh = bw.iroh
                val db = bw.db

                Log.i(TAG, "Initializing Gossip subscriptions")

                // Create ContentDownloader for processing received manifests
                contentDownloader = ContentDownloader(iroh, db, bw.cacheDir)

                // Create GossipWrapper from Iroh's Gossip
                val gossip = iroh.getGossip()
                gossipWrapper = GossipWrapper.fromGossip(gossip, scope)
                Log.i(TAG, "GossipWrapper created")

                // Subscribe to own topic for publishing
                val myTopicKey = Ed25519Keyring.getOrCreateTopicKey(applicationContext)
                Log.i(TAG, "My topic key: ${Base64.getEncoder().encodeToString(myTopicKey).take(16)}...")

                // Subscribe to own topic - no bootstrap peers needed for our own topic
                // Note: Peers will join via direct connection or relay discovery
                try {
                    myTopicSubscription = gossipWrapper!!.subscribe(
                        myTopicKey,
                        emptyList(), // No bootstrap needed for own topic
                        OwnTopicHandler()
                    )
                    Log.i(TAG, "Subscribed to own topic")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to subscribe to own topic: ${e.message}", e)
                    updateNotification("Topic subscription failed")
                    return@launch
                }

                // Subscribe to peer topics
                val peers = db.peerTopicDao().subscribedPeers()
                Log.i(TAG, "Found ${peers.size} peers to subscribe to")

                for (peer in peers) {
                    subscribeToTopic(peer)
                }

                updateNotification("Connected to ${peers.size} peers")

                // Publish initial manifest announcement
                delay(1000) // Give subscriptions time to establish
                publishManifest()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Gossip", e)
                updateNotification("Initialization failed: ${e.message}")
            }
        }
    }

    /**
     * Subscribe to a peer's Gossip topic.
     * We try to use the peer's nodeId as a bootstrap address.
     */
    private suspend fun subscribeToTopic(peer: PeerTopic) {
        try {
            // Log peer info for debugging
            Log.d(TAG, "Subscribing to peer ${peer.nodeId}")
            Log.d(TAG, "  Stored addresses: ${peer.addresses}")
            Log.d(TAG, "  Topic key: ${Base64.getEncoder().encodeToString(peer.topicKey).take(16)}...")

            // Try using the peer's nodeId directly as bootstrap
            // Iroh should be able to resolve this via relay
            val bootstrapPeers = listOf(peer.nodeId)

            val subscription = gossipWrapper?.subscribe(
                peer.topicKey,
                bootstrapPeers,
                PeerTopicHandler(peer.nodeId)
            )

            if (subscription != null) {
                peerSubscriptions[peer.nodeId] = subscription
                Log.i(TAG, "Subscribed to peer ${peer.nodeId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to peer ${peer.nodeId}: ${e.message}")
        }
    }

    /**
     * Handler for messages on our own topic.
     * We only publish on our own topic, so this is a no-op handler.
     */
    private inner class OwnTopicHandler : GossipMessageHandler {
        override suspend fun onMessage(senderId: String, content: ByteArray) {
            // Ignore messages on our own topic (we only publish)
            Log.d(TAG, "Received message on own topic from $senderId (ignoring)")
        }

        override fun onPeerJoined(peerId: String) {
            Log.i(TAG, "Peer joined our topic: $peerId")
            connectedPeers.add(peerId)
            updateNotificationWithPeerCount()
            
            // Publish our manifest so the new peer gets our latest state
            publishManifest()
        }

        override fun onPeerLeft(peerId: String) {
            Log.i(TAG, "Peer left our topic: $peerId")
            connectedPeers.remove(peerId)
            updateNotificationWithPeerCount()
        }
    }

    /**
     * Handler for messages on a peer's topic.
     * Processes manifest announcements from that peer.
     */
    private inner class PeerTopicHandler(private val peerNodeId: String) : GossipMessageHandler {
        override suspend fun onMessage(senderId: String, content: ByteArray) {
            Log.d(TAG, "Received announcement from peer $peerNodeId (delivered by $senderId)")
            processAnnouncement(peerNodeId, content)
        }

        override fun onPeerJoined(peerId: String) {
            Log.d(TAG, "Peer $peerId joined topic for $peerNodeId")
        }

        override fun onPeerLeft(peerId: String) {
            Log.d(TAG, "Peer $peerId left topic for $peerNodeId")
        }

        override fun onError(error: String) {
            Log.e(TAG, "Error on peer topic $peerNodeId: $error")
        }
    }

    /**
     * Process a manifest announcement from a peer.
     */
    private suspend fun processAnnouncement(peerNodeId: String, content: ByteArray) {
        try {
            val bw = Bailiwick.getInstance()
            val db = bw.db
            val iroh = bw.iroh

            // Parse the announcement
            val json = String(content)
            val announcement = GsonProvider.gson.fromJson(json, ManifestAnnouncement::class.java)

            Log.i(TAG, "Received announcement from $peerNodeId: version=${announcement.version}, hash=${announcement.manifestHash.take(16)}...")

            // Get peer info to check version
            val peerTopic = db.peerTopicDao().findByNodeId(peerNodeId)
            if (peerTopic == null) {
                Log.w(TAG, "Received announcement from unknown peer: $peerNodeId")
                return
            }

            // Check version - skip if we already have this or newer
            if (announcement.version <= peerTopic.lastKnownVersion) {
                Log.d(TAG, "Already have version ${announcement.version} for $peerNodeId (have ${peerTopic.lastKnownVersion})")
                return
            }

            // TODO: Verify Ed25519 signature
            // val verifier = Ed25519Signer.verifierFromPublicKey(peerTopic.ed25519PublicKey)
            // val signatureData = (announcement.manifestHash + announcement.version.toString()).toByteArray()
            // if (!verifier.verifyFromString(signatureData, announcement.signature)) {
            //     Log.w(TAG, "Invalid signature on announcement from $peerNodeId")
            //     return
            // }

            // Download the manifest blob
            Log.i(TAG, "Downloading manifest ${announcement.manifestHash} from $peerNodeId")
            val manifestData = iroh.downloadBlob(announcement.manifestHash, peerNodeId)
            if (manifestData == null) {
                Log.w(TAG, "Failed to download manifest from $peerNodeId")
                return
            }

            // Parse unencrypted manifest
            // TODO: Implement proper decryption with shared keys
            val userManifest = try {
                GsonProvider.gson.fromJson(String(manifestData), UserManifest::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse manifest from $peerNodeId: ${e.message}")
                return
            }

            Log.i(TAG, "Manifest from $peerNodeId: ${userManifest.circleManifests.size} circles, version ${userManifest.version}")

            // Download identity if changed
            contentDownloader?.downloadIdentity(userManifest.identityHash, peerNodeId)

            // Process actions addressed to us FIRST (to get keys before decrypting posts)
            val myNodeId = iroh.nodeId()
            val myActions = userManifest.actions[myNodeId] ?: emptyList()
            if (myActions.isNotEmpty()) {
                Log.i(TAG, "Found ${myActions.size} actions addressed to us from $peerNodeId")
                for (actionHash in myActions) {
                    processAction(actionHash, peerNodeId, peerTopic)
                }
            }

            // Process circle manifests (after keys are stored)
            for ((circleId, circleManifestHash) in userManifest.circleManifests) {
                processCircleManifest(circleManifestHash, peerNodeId, peerTopic)
            }

            // Update tracking
            db.peerTopicDao().updateManifestInfo(
                peerNodeId,
                announcement.version,
                announcement.manifestHash,
                System.currentTimeMillis()
            )

            Log.i(TAG, "Processed manifest from $peerNodeId, updated to version ${announcement.version}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process announcement from $peerNodeId", e)
        }
    }

    /**
     * Process a circle manifest and download its posts.
     */
    private suspend fun processCircleManifest(
        circleManifestHash: String,
        peerNodeId: String,
        peerTopic: PeerTopic
    ) {
        try {
            val bw = Bailiwick.getInstance()
            val iroh = bw.iroh

            // Download circle manifest
            val manifestData = iroh.downloadBlob(circleManifestHash, peerNodeId)
            if (manifestData == null) {
                Log.w(TAG, "Failed to download circle manifest $circleManifestHash")
                return
            }

            // Parse unencrypted circle manifest
            // TODO: Implement proper decryption with shared keys
            val circleManifest = try {
                GsonProvider.gson.fromJson(String(manifestData), CircleManifest::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse circle manifest: ${e.message}")
                return
            }

            Log.i(TAG, "Circle ${circleManifest.name}: ${circleManifest.posts.size} posts")

            // Download posts using keys received from peer
            val postCipher = EncryptorFactory.forPeer(bw.db.keyDao(), peerNodeId) { decrypted ->
                // Validate that decryption produced valid JSON
                try {
                    GsonProvider.gson.fromJson(String(decrypted), IrohPost::class.java)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            for (post in circleManifest.posts) {
                contentDownloader?.downloadPost(post.hash, peerNodeId, postCipher)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process circle manifest", e)
        }
    }


    /**
     * Process an action addressed to us from a peer.
     */
    private suspend fun processAction(
        actionHash: String,
        peerNodeId: String,
        peerTopic: PeerTopic
    ) {
        try {
            val bw = Bailiwick.getInstance()
            val iroh = bw.iroh
            val db = bw.db

            // Download action blob
            val actionData = iroh.downloadBlob(actionHash, peerNodeId)
            if (actionData == null) {
                Log.w(TAG, "Failed to download action $actionHash from $peerNodeId")
                return
            }

            // Parse the network action
            val networkAction = try {
                GsonProvider.gson.fromJson(String(actionData), NetworkAction::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse action from $peerNodeId: ${e.message}")
                return
            }

            Log.i(TAG, "Received action type=${networkAction.type} from $peerNodeId")

            // Route to appropriate handler
            when (networkAction.type) {
                ActionType.UpdateKey.name -> {
                    processUpdateKeyAction(networkAction.data, peerNodeId, peerTopic)
                }
                else -> {
                    Log.w(TAG, "Unknown action type: ${networkAction.type}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process action from $peerNodeId", e)
        }
    }

    /**
     * Process an UpdateKey action - decrypt and store the circle key.
     */
    private fun processUpdateKeyAction(
        encryptedKeyB64: String,
        peerNodeId: String,
        peerTopic: PeerTopic
    ) {
        try {
            val bw = Bailiwick.getInstance()
            val db = bw.db
            val context = applicationContext

            // Try X25519 decryption first (v2 format)
            var decryptedKey: ByteArray? = null
            
            try {
                val ed25519Keyring = Ed25519Keyring.create(context)
                decryptedKey = KeyEncryption.decryptKeyFromPeer(
                    encryptedKeyB64,
                    ed25519Keyring,
                    peerTopic.ed25519PublicKey
                )
            } catch (e: Exception) {
                Log.d(TAG, "X25519 decryption failed, trying plain Base64: ${e.message}")
            }

            // Fallback to plain Base64 (v1 format)
            if (decryptedKey == null) {
                try {
                    decryptedKey = Base64.getDecoder().decode(encryptedKeyB64)
                    // Validate it looks like an AES key (16 or 32 bytes)
                    if (decryptedKey.size != 16 && decryptedKey.size != 32) {
                        Log.w(TAG, "Plain Base64 decoded to invalid key size: ${decryptedKey.size}")
                        decryptedKey = null
                    } else {
                        Log.i(TAG, "Successfully decoded plain Base64 key (v1 format)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Plain Base64 decoding also failed: ${e.message}")
                }
            }

            if (decryptedKey == null) {
                Log.e(TAG, "Failed to decrypt/decode circle key from $peerNodeId")
                return
            }

            // Store the key - convert back to Base64 for KeyStorage
            val keyB64 = Base64.getEncoder().encodeToString(decryptedKey)
            KeyStorage.storeAesKey(db.keyDao(), peerNodeId, keyB64)

            Log.i(TAG, "Stored circle key from $peerNodeId (${decryptedKey.size} bytes)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process UpdateKey action from $peerNodeId", e)
        }
    }

    /**
     * Data class for deserializing actions from Iroh.
     */
    private data class NetworkAction(
        val type: String,
        val data: String,
        val timestamp: Long
    )

    /**
     * Publish a manifest announcement to our topic.
     * Called when local content changes (new post, updated identity, etc.)
     */
    fun publishManifest() {
        scope.launch {
            try {
                val bw = Bailiwick.getInstance()
                val db = bw.db
                val iroh = bw.iroh
                val ed25519Keyring = Ed25519Keyring.create(applicationContext)

                // Build UserManifest
                val nodeId = iroh.nodeId()
                val identity = db.identityDao().findByOwner(nodeId)
                if (identity == null) {
                    Log.w(TAG, "No identity found, skipping manifest publish")
                    return@launch
                }

                // Ensure identity is published - publish if missing
                var identityHash = identity.blobHash
                if (identityHash == null) {
                    Log.i(TAG, "Identity not published, publishing now...")
                    val publisher = ContentPublisher(iroh, db)
                    identityHash = publisher.publishIdentity(identity)
                    Log.i(TAG, "Identity published: $identityHash")
                }

                // Get version from SharedPreferences and increment
                val prefs = applicationContext.getSharedPreferences("gossip_state", Context.MODE_PRIVATE)
                val currentVersion = prefs.getLong("manifest_version", 0)
                val newVersion = currentVersion + 1

                // Publish any pending actions to blob storage
                val publisher = ContentPublisher(iroh, db)
                publisher.publishActions()

                // Build circle manifests
                val circleManifests = buildCircleManifests(nodeId, ed25519Keyring)

                // Build pending actions map
                val actions = buildActionsMap()

                val userManifest = UserManifest(
                    version = newVersion,
                    identityHash = identityHash,
                    circleManifests = circleManifests,
                    actions = actions
                )

                // Store manifest as unencrypted JSON for now
                // TODO: Implement proper encryption with shared keys
                val manifestJson = GsonProvider.gson.toJson(userManifest)
                val manifestHash = iroh.storeBlob(manifestJson.toByteArray())

                Log.i(TAG, "Stored manifest blob: $manifestHash")

                // Create signed announcement
                val signer = ed25519Keyring.signer()
                val signatureData = (manifestHash + newVersion.toString()).toByteArray()
                val signature = signer.signToString(signatureData)

                val announcement = ManifestAnnouncement(
                    manifestHash = manifestHash,
                    version = newVersion,
                    signature = signature
                )

                val announcementJson = GsonProvider.gson.toJson(announcement)

                // Broadcast to own topic
                myTopicSubscription?.broadcast(announcementJson.toByteArray())

                // Update local version in SharedPreferences
                prefs.edit().putLong("manifest_version", newVersion).apply()

                Log.i(TAG, "Published manifest version $newVersion, hash ${manifestHash.take(16)}...")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish manifest", e)
            }
        }
    }

    /**
     * Build circle manifest blobs and return their hashes.
     */
    private suspend fun buildCircleManifests(
        nodeId: String,
        keyring: Ed25519Keyring
    ): Map<Int, String> {
        val bw = Bailiwick.getInstance()
        val db = bw.db
        val iroh = bw.iroh

        val result = mutableMapOf<Int, String>()
        val circles = db.circleDao().all()

        for (circle in circles) {
            try {
                // Get posts in this circle
                val postIds = db.circlePostDao().postsIn(circle.id)
                val posts = db.postDao().findAll(postIds)
                val identity = db.identityDao().find(circle.identityId)

                // Log post status for debugging
                Log.d(TAG, "Circle ${circle.name}: ${posts.size} posts total")
                for (post in posts) {
                    Log.d(TAG, "  Post ${post.id}: blobHash=${post.blobHash?.take(16) ?: "null"}")
                }

                // Build post entries
                val postEntries = posts.mapNotNull { post ->
                    val hash = post.blobHash ?: return@mapNotNull null
                    PostEntry(
                        hash = hash,
                        timestamp = post.timestamp ?: System.currentTimeMillis(),
                        authorNodeId = identity?.owner ?: nodeId
                    )
                }

                // Get members
                val memberIds = db.circleMemberDao().membersFor(circle.id)
                val members = memberIds.mapNotNull { memberId ->
                    db.identityDao().find(memberId)?.owner
                }

                // Build manifest
                val circleManifest = ManifestUtils.buildCircleManifest(
                    circleId = circle.id.toInt(),
                    name = circle.name,
                    posts = postEntries,
                    members = members
                )

                // Store unencrypted circle manifest for now
                // TODO: Implement proper encryption with shared keys
                val manifestJson = GsonProvider.gson.toJson(circleManifest)
                val hash = iroh.storeBlob(manifestJson.toByteArray())

                result[circle.id.toInt()] = hash
                Log.d(TAG, "Built circle manifest for ${circle.name}: ${postEntries.size} posts")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to build manifest for circle ${circle.name}", e)
            }
        }

        return result
    }

    /**
     * Build pending actions map for manifest.
     */
    private fun buildActionsMap(): Map<String, List<String>> {
        val db = Bailiwick.getInstance().db
        val actions = db.actionDao().all()

        val result = mutableMapOf<String, MutableList<String>>()

        for (action in actions) {
            val hash = action.blobHash ?: continue
            val toPeerId = action.toPeerId ?: continue

            result.getOrPut(toPeerId) { mutableListOf() }.add(hash)
        }

        return result
    }

    /**
     * Subscribe to a new peer's topic.
     * Called when a new peer is introduced.
     */
    fun subscribeToNewPeer(peerNodeId: String, topicKey: ByteArray) {
        scope.launch {
            try {
                val db = Bailiwick.getInstance().db
                val peerTopic = db.peerTopicDao().findByNodeId(peerNodeId)
                if (peerTopic != null) {
                    subscribeToTopic(peerTopic)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to peer $peerNodeId", e)
            }
        }
    }

    private fun updateNotificationWithPeerCount() {
        updateNotification("Connected to ${connectedPeers.size} peers")
    }

    override fun onDestroy() {
        Log.i(TAG, "Stopping GossipService")
        instance = null
        initJob?.cancel()
        scope.launch {
            try {
                gossipWrapper?.unsubscribeAll()
            } catch (e: Exception) {
                Log.w(TAG, "Error during cleanup: ${e.message}")
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bailiwick Gossip",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time synchronization with Bailiwick network"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BailiwickActivity::class.java),
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bailiwick")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
