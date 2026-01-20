package com.perfectlunacy.bailiwick.testing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.perfectlunacy.bailiwick.BuildConfig

/**
 * BroadcastReceiver for test introduction intents.
 * Only registered in debug builds via debug/AndroidManifest.xml.
 *
 * Usage with adb:
 * ```
 * adb shell am broadcast -a com.perfectlunacy.bailiwick.TEST_INTRODUCTION \
 *     --es introduction_json '{"encrypted":"data"}' \
 *     -n com.perfectlunacy.bailiwick/.testing.TestIntroductionReceiver
 * ```
 *
 * The introduction JSON should be the encrypted Introduction payload
 * (same format as QR code content).
 */
class TestIntroductionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Safety check - should never be called in release builds since
        // the receiver is only registered in the debug manifest
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "TestIntroductionReceiver called in non-debug build, ignoring")
            return
        }

        if (intent.action != ACTION_TEST_INTRODUCTION) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }

        val introductionJson = intent.getStringExtra(EXTRA_INTRODUCTION_JSON)
        if (introductionJson == null) {
            Log.e(TAG, "Missing $EXTRA_INTRODUCTION_JSON extra")
            return
        }

        Log.i(TAG, "Received test introduction broadcast")
        setPendingTestIntroduction(introductionJson)
    }

    companion object {
        private const val TAG = "TestIntroReceiver"

        /**
         * Intent action for test introductions (debug builds only).
         * Allows E2E tests to bypass QR code scanning.
         */
        const val ACTION_TEST_INTRODUCTION = "com.perfectlunacy.bailiwick.TEST_INTRODUCTION"

        /**
         * Intent extra key for the Introduction JSON payload.
         * The JSON should be the encrypted Introduction data (same format as QR code content).
         */
        const val EXTRA_INTRODUCTION_JSON = "introduction_json"

        // Pending test introduction - stored statically to survive Activity recreation
        @Volatile
        private var pendingTestIntroduction: String? = null

        fun setPendingTestIntroduction(introductionJson: String) {
            pendingTestIntroduction = introductionJson
            Log.d(TAG, "Stored pending test introduction (${introductionJson.length} chars)")
        }

        fun consumePendingTestIntroduction(): String? {
            val result = pendingTestIntroduction
            pendingTestIntroduction = null
            if (result != null) {
                Log.d(TAG, "Consumed pending test introduction")
            }
            return result
        }

        fun hasPendingTestIntroduction(): Boolean = pendingTestIntroduction != null
    }
}
