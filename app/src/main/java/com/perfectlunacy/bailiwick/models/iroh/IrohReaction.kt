package com.perfectlunacy.bailiwick.models.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Network serialization model for reactions.
 * Published to Iroh for peer synchronization.
 */
data class IrohReaction(
    val postHash: BlobHash,           // Hash of the post being reacted to
    val authorNodeId: NodeId,         // Node ID of the reactor
    val emoji: String,                // The emoji reaction
    val timestamp: Long,              // When the reaction was created
    val signature: String,            // Cryptographic signature
    val isRemoval: Boolean = false    // True if this is a reaction removal
)
