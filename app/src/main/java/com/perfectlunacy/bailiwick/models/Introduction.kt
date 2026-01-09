package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Data class for exchanging introduction information via QR codes.
 * Used when two users want to connect and share their identity information.
 */
data class Introduction(
    val isResponse: Boolean,
    val peerId: NodeId,
    val name: String,
    val publicKey: String,  // Base64-encoded RSA public key
    val docTicket: String   // Iroh Doc ticket for joining and syncing content
)
