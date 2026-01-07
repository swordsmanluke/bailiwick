package com.perfectlunacy.bailiwick.models.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash

/**
 * Data classes for Iroh network serialization.
 * These are serialized to JSON and stored in Iroh blobs.
 */

/**
 * Identity as stored in Iroh.
 * Contains public profile information.
 */
data class IrohIdentity(
    val name: String,
    val profilePicHash: BlobHash?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Post as stored in Iroh.
 * Encrypted before storage.
 */
data class IrohPost(
    val timestamp: Long,
    val parentHash: BlobHash?,      // For threading
    val text: String,
    val files: List<IrohFileDef>,
    val signature: String
)

/**
 * File reference in a post.
 */
data class IrohFileDef(
    val mimeType: String,
    val blobHash: BlobHash
)

/**
 * Feed snapshot - list of post hashes for a circle.
 * Encrypted before storage.
 */
data class IrohFeed(
    val updatedAt: Long,
    val posts: List<BlobHash>
)

/**
 * Circle definition as stored in Iroh.
 * Contains the list of member node IDs and the encryption key.
 */
data class IrohCircle(
    val name: String,
    val keyHash: BlobHash,          // Hash of the symmetric key blob
    val members: List<String>,      // NodeIds of members
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Action for introducing peers or other operations.
 * Stored in Iroh docs to propagate actions.
 */
data class IrohAction(
    val type: ActionType,
    val payload: String,            // JSON payload specific to action type
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String
)

enum class ActionType {
    INTRODUCE,      // Introduce two peers
    INVITE,         // Invite to circle
    ACCEPT,         // Accept invitation
    REJECT          // Reject invitation
}
