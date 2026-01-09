package com.perfectlunacy.bailiwick.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Singleton Gson instance provider.
 *
 * Provides a single, consistently-configured Gson instance for the entire app,
 * avoiding the overhead of creating multiple instances and ensuring consistent
 * serialization behavior.
 */
object GsonProvider {
    /**
     * The shared Gson instance.
     * Configure with any necessary type adapters here.
     */
    val gson: Gson by lazy {
        GsonBuilder()
            .create()
    }
}
