package com.perfectlunacy.bailiwick

import android.app.Application
import android.util.Log

/**
 * Application class for Bailiwick.
 * Handles early application-wide initialization.
 */
class BailiwickApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Bailiwick application starting")
        // Keep this lightweight - heavy initialization happens async in BailiwickActivity
    }

    companion object {
        private const val TAG = "BailiwickApplication"
    }
}
