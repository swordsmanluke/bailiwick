package com.perfectlunacy.bailiwick.storage.iroh

object IrohDocKeys {
    const val KEY_IDENTITY = "identity"
    
    // DEPRECATED: feed/latest doesn't sync reliably in Iroh Docs when overwritten.
    // Use posts/{circleId}/{timestamp} entries instead.
    const val KEY_FEED_LATEST = "feed/latest"
    
    const val KEY_CIRCLE_PREFIX = "circles/"
    const val KEY_ACTIONS_PREFIX = "actions/"
    const val KEY_POSTS_PREFIX = "posts/"

    /**
     * Constructs the key for a specific circle.
     */
    fun circleKey(circleId: Long): String = "$KEY_CIRCLE_PREFIX$circleId"

    /**
     * Constructs the key for an action.
     * Actions are stored at: actions/{targetNodeId}/{timestamp}
     * This allows peers to efficiently find all actions meant for them.
     */
    fun actionKey(targetNodeId: String, timestamp: Long): String = 
        "$KEY_ACTIONS_PREFIX$targetNodeId/$timestamp"

    /**
     * Constructs the key for a post entry.
     * Posts are stored at: posts/{circleId}/{timestamp}
     * Each post is its own entry, allowing reliable sync via Iroh Docs.
     */
    fun postKey(circleId: Long, timestamp: Long): String = 
        "$KEY_POSTS_PREFIX$circleId/$timestamp"

    /**
     * Returns the prefix for all posts in a specific circle.
     * Use with keysWithPrefix() to list all posts for a circle.
     */
    fun postsForCirclePrefix(circleId: Long): String = "$KEY_POSTS_PREFIX$circleId/"
}
