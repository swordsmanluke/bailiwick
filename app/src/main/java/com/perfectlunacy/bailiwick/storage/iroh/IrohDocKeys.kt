package com.perfectlunacy.bailiwick.storage.iroh

object IrohDocKeys {
    const val KEY_IDENTITY = "identity"
    const val KEY_FEED_LATEST = "feed/latest"
    const val KEY_CIRCLE_PREFIX = "circles/"
    const val KEY_ACTIONS_PREFIX = "actions/"

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
}
