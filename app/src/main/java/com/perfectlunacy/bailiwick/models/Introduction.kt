package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Data class for exchanging introduction information via QR codes.
 * Used when two users want to connect and share their identity information.
 *
 * Version 2 uses Ed25519 keys (smaller QR codes) and Gossip-based sync.
 * Version 1 (legacy) used RSA keys and Iroh Docs.
 */
data class Introduction(
    val version: Int = 2,            // Protocol version (1 = legacy RSA/Docs, 2 = Ed25519/Gossip)
    val isResponse: Boolean,
    val peerId: NodeId,              // Iroh node ID (for network routing)
    val name: String,
    val publicKey: String,           // Ed25519 public key (32 bytes, Base64 = 44 chars)
    val topicKey: String,            // Topic key for Gossip subscription (32 bytes, Base64 = 44 chars)
    val addresses: List<String> = emptyList(),  // Peer addresses for bootstrap
    // Legacy field for version 1 compatibility (optional for parsing old QR codes)
    val docTicket: String? = null
)
