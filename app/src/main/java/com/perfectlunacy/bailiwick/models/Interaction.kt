package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Represents an interaction with content (like, comment, share, etc.).
 * Used for tracking user engagement with posts.
 */
data class Interaction(
    val type: InteractionType,
    val nodeId: NodeId,
    val postBlobHash: String?,
    val content: String?
)

enum class InteractionType {
    LIKE,
    COMMENT,
    SHARE
}
