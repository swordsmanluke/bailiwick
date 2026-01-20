package com.perfectlunacy.bailiwick.testing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.perfectlunacy.bailiwick.BuildConfig

/**
 * BroadcastReceiver for importing test accounts in E2E tests.
 * Only registered in debug builds via debug/AndroidManifest.xml.
 *
 * This allows E2E tests to pre-seed known test accounts (Alice, Bob) without
 * going through the sign-up UI flow, making tests faster and more reliable.
 *
 * ## Usage with adb:
 *
 * ```bash
 * # Import a test account
 * adb shell am broadcast -a com.perfectlunacy.bailiwick.TEST_ACCOUNT_IMPORT \
 *     --es account_json '{"secretKey":"base64...","nodeId":"abc123...","username":"alice","displayName":"Alice","password":"test123"}' \
 *     -n com.perfectlunacy.bailiwick/.testing.TestAccountReceiver
 *
 * # Then start/restart the app
 * adb shell am start -n com.perfectlunacy.bailiwick/.BailiwickActivity
 * ```
 *
 * ## Test Account JSON Format:
 *
 * ```json
 * {
 *   "secretKey": "base64-encoded-ed25519-secret-key",
 *   "nodeId": "pre-computed-iroh-node-id",
 *   "username": "alice",
 *   "displayName": "Alice",
 *   "avatarHash": null,
 *   "password": "test123"
 * }
 * ```
 *
 * The `nodeId` must be pre-computed from the secretKey using the Iroh library.
 * This can be done offline when generating test fixtures.
 *
 * ## Security Note:
 *
 * This receiver is ONLY available in debug builds. The BuildConfig.DEBUG check
 * and manifest-level restriction ensure it cannot be exploited in production.
 */
class TestAccountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Safety check - should never be called in release builds
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "TestAccountReceiver called in non-debug build, ignoring")
            return
        }

        if (intent.action != ACTION_TEST_ACCOUNT_IMPORT) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }

        val accountJson = intent.getStringExtra(EXTRA_ACCOUNT_JSON)
        if (accountJson == null) {
            Log.e(TAG, "Missing $EXTRA_ACCOUNT_JSON extra")
            return
        }

        Log.i(TAG, "Received test account import broadcast")

        try {
            val accountData = Gson().fromJson(accountJson, TestAccountData::class.java)
            validateAccountData(accountData)
            setPendingTestAccount(accountData)
            Log.i(TAG, "Test account staged for import: ${accountData.username}")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Invalid account JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid account data: ${e.message}")
        }
    }

    private fun validateAccountData(data: TestAccountData) {
        require(data.secretKey.isNotBlank()) { "secretKey is required" }
        require(data.nodeId.isNotBlank()) { "nodeId is required" }
        require(data.username.isNotBlank()) { "username is required" }
        require(data.displayName.isNotBlank()) { "displayName is required" }
        require(data.password.isNotBlank()) { "password is required" }
    }

    companion object {
        private const val TAG = "TestAccountReceiver"

        /**
         * Intent action for test account import (debug builds only).
         * Allows E2E tests to pre-seed known test accounts.
         */
        const val ACTION_TEST_ACCOUNT_IMPORT = "com.perfectlunacy.bailiwick.TEST_ACCOUNT_IMPORT"

        /**
         * Intent extra key for the test account JSON.
         */
        const val EXTRA_ACCOUNT_JSON = "account_json"

        // Pending test account - stored statically to survive Activity recreation
        @Volatile
        private var pendingTestAccount: TestAccountData? = null

        fun setPendingTestAccount(data: TestAccountData) {
            pendingTestAccount = data
            Log.d(TAG, "Stored pending test account: ${data.username}")
        }

        fun consumePendingTestAccount(): TestAccountData? {
            val result = pendingTestAccount
            pendingTestAccount = null
            if (result != null) {
                Log.d(TAG, "Consumed pending test account: ${result.username}")
            }
            return result
        }

        fun hasPendingTestAccount(): Boolean = pendingTestAccount != null
    }
}

/**
 * Data class for test account import.
 *
 * @property secretKey Base64-encoded Ed25519 secret key (32 bytes)
 * @property nodeId The Iroh NodeId derived from the secret key (must be pre-computed)
 * @property username Account username
 * @property displayName Display name shown in the app
 * @property avatarHash Optional blob hash of avatar image
 * @property password Password for the account (stored as hash)
 */
data class TestAccountData(
    @SerializedName("secretKey") val secretKey: String,
    @SerializedName("nodeId") val nodeId: String,
    @SerializedName("username") val username: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("avatarHash") val avatarHash: String? = null,
    @SerializedName("password") val password: String
)
