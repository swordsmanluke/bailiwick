package com.perfectlunacy.bailiwick.storage.gossip

import android.util.Log
import computer.iroh.Gossip
import computer.iroh.GossipMessageCallback
import computer.iroh.Iroh
import computer.iroh.Message
import computer.iroh.MessageType
import computer.iroh.Sender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Callback interface for receiving Gossip messages.
 */
interface GossipMessageHandler {
    /** Called when a message is received on a subscribed topic */
    suspend fun onMessage(senderId: String, content: ByteArray)

    /** Called when a peer joins the topic swarm */
    fun onPeerJoined(peerId: String) {}

    /** Called when a peer leaves the topic swarm */
    fun onPeerLeft(peerId: String) {}

    /** Called when an error occurs */
    fun onError(error: String) {}
}

/**
 * Represents an active subscription to a Gossip topic.
 */
class TopicSubscription(
    val topicKey: ByteArray,
    private val sender: Sender,
    private val handler: GossipMessageHandler
) {
    /**
     * Broadcast a message to all peers in the swarm.
     */
    suspend fun broadcast(message: ByteArray) {
        sender.broadcast(message)
    }

    /**
     * Broadcast a message to direct neighbors only.
     */
    suspend fun broadcastNeighbors(message: ByteArray) {
        sender.broadcastNeighbors(message)
    }

    /**
     * Cancel this subscription.
     */
    suspend fun cancel() {
        sender.cancel()
    }
}

/**
 * Wrapper around Iroh's Gossip API for pub/sub messaging.
 *
 * Gossip enables real-time message broadcasting to all peers subscribed to a topic.
 * Messages propagate through the swarm, so peers can receive updates from any connected node.
 *
 * Usage:
 * 1. Subscribe to a topic with a message handler
 * 2. Broadcast messages to all subscribers
 * 3. Receive messages via the handler callback
 */
class GossipWrapper private constructor(
    private val gossip: Gossip,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GossipWrapper"

        /**
         * Create a GossipWrapper from an Iroh node.
         */
        fun create(iroh: Iroh, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): GossipWrapper {
            return GossipWrapper(iroh.gossip(), scope)
        }

        /**
         * Create a GossipWrapper from a Gossip instance.
         */
        fun fromGossip(gossip: Gossip, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): GossipWrapper {
            return GossipWrapper(gossip, scope)
        }
    }

    private val subscriptions = ConcurrentHashMap<String, TopicSubscription>()

    /**
     * Subscribe to a Gossip topic.
     *
     * @param topicKey The 32-byte topic key
     * @param bootstrapPeers List of peer addresses for initial connectivity
     * @param handler Callback for received messages
     * @return TopicSubscription for broadcasting and managing the subscription
     */
    suspend fun subscribe(
        topicKey: ByteArray,
        bootstrapPeers: List<String>,
        handler: GossipMessageHandler
    ): TopicSubscription {
        require(topicKey.size == 32) { "Topic key must be 32 bytes" }

        val topicId = topicKey.toHexString()

        // Check if already subscribed
        subscriptions[topicId]?.let { existing ->
            Log.w(TAG, "Already subscribed to topic $topicId")
            return existing
        }

        Log.i(TAG, "Subscribing to topic $topicId with ${bootstrapPeers.size} bootstrap peers")
        if (bootstrapPeers.isNotEmpty()) {
            Log.d(TAG, "  Bootstrap peers: ${bootstrapPeers.joinToString(", ")}")
        }

        val callback = object : GossipMessageCallback {
            override suspend fun onMessage(msg: Message) {
                handleMessage(msg, handler)
            }
        }

        val sender = try {
            gossip.subscribe(topicKey, bootstrapPeers, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Gossip subscribe failed for topic $topicId: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
        val subscription = TopicSubscription(topicKey, sender, handler)

        subscriptions[topicId] = subscription
        Log.i(TAG, "Successfully subscribed to topic $topicId")

        return subscription
    }

    /**
     * Unsubscribe from a topic.
     */
    suspend fun unsubscribe(topicKey: ByteArray) {
        val topicId = topicKey.toHexString()
        subscriptions.remove(topicId)?.let { subscription ->
            try {
                subscription.cancel()
                Log.i(TAG, "Unsubscribed from topic $topicId")
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing from topic $topicId: ${e.message}")
            }
        }
    }

    /**
     * Check if subscribed to a topic.
     */
    fun isSubscribed(topicKey: ByteArray): Boolean {
        return subscriptions.containsKey(topicKey.toHexString())
    }

    /**
     * Get all active subscriptions.
     */
    fun getActiveSubscriptions(): List<TopicSubscription> {
        return subscriptions.values.toList()
    }

    /**
     * Unsubscribe from all topics.
     */
    suspend fun unsubscribeAll() {
        val topics = subscriptions.keys.toList()
        for (topicId in topics) {
            subscriptions.remove(topicId)?.let { subscription ->
                try {
                    subscription.cancel()
                } catch (e: Exception) {
                    Log.e(TAG, "Error unsubscribing from $topicId: ${e.message}")
                }
            }
        }
        Log.i(TAG, "Unsubscribed from all ${topics.size} topics")
    }

    private suspend fun handleMessage(msg: Message, handler: GossipMessageHandler) {
        try {
            when (msg.type()) {
                MessageType.RECEIVED -> {
                    val content = msg.asReceived()
                    handler.onMessage(content.deliveredFrom, content.content)
                }
                MessageType.NEIGHBOR_UP -> {
                    handler.onPeerJoined(msg.asNeighborUp())
                }
                MessageType.NEIGHBOR_DOWN -> {
                    handler.onPeerLeft(msg.asNeighborDown())
                }
                MessageType.JOINED -> {
                    val peers = msg.asJoined()
                    Log.i(TAG, "Peers joined: ${peers.joinToString()}")
                    peers.forEach { handler.onPeerJoined(it) }
                }
                MessageType.LAGGED -> {
                    Log.w(TAG, "Gossip lagged - some messages may have been missed")
                }
                MessageType.ERROR -> {
                    val error = msg.asError()
                    Log.e(TAG, "Gossip error: $error")
                    handler.onError(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Gossip message: ${e.message}", e)
            handler.onError(e.message ?: "Unknown error")
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

/**
 * Generate a random 32-byte topic key.
 */
fun generateTopicKey(): ByteArray {
    return java.security.SecureRandom().generateSeed(32)
}
