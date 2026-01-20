package com.perfectlunacy.bailiwick.testing

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.perfectlunacy.bailiwick.BuildConfig
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import java.io.File

/**
 * Exports account data in the unencrypted test format for E2E testing.
 * Only functional in debug builds.
 *
 * This allows creating test account fixtures that can be imported via
 * TestAccountReceiver without going through the sign-up flow.
 *
 * ## Usage:
 *
 * ```kotlin
 * // Export current account to test format
 * val json = TestAccountExporter.export(context, db, "test123")
 *
 * // Save to file (can then be pushed to device via adb)
 * TestAccountExporter.saveToFile(json, File(externalDir, "alice-test-account.json"))
 * ```
 *
 * ## Security Note:
 *
 * This exports the secret key in plain text! Only use for test accounts
 * that you're willing to expose. Never use for real user accounts.
 */
object TestAccountExporter {
    private const val TAG = "TestAccountExporter"
    private const val IROH_CONFIG_PREFS = "iroh_config"
    private const val IROH_SECRET_KEY = "iroh_secret_key"

    /**
     * Export the current account in test format.
     *
     * @param context Application context
     * @param db Database instance
     * @param password Password to include in export (will be stored as-is, not hashed)
     * @return JSON string in TestAccountData format, or null if export fails
     */
    suspend fun export(
        context: Context,
        db: BailiwickDatabase,
        password: String
    ): String? {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "TestAccountExporter only works in debug builds")
            return null
        }

        Log.i(TAG, "Starting test account export")

        // Get current account
        val account = db.accountDao().activeAccount()
        if (account == null) {
            Log.e(TAG, "No account found to export")
            return null
        }

        // Get identity
        val identity = db.identityDao().identitiesFor(account.peerId).firstOrNull()
        if (identity == null) {
            Log.e(TAG, "No identity found for account")
            return null
        }

        // Get secret key from SharedPreferences
        val prefs = context.getSharedPreferences(IROH_CONFIG_PREFS, Context.MODE_PRIVATE)
        val secretKey = prefs.getString(IROH_SECRET_KEY, null)
        if (secretKey == null) {
            Log.e(TAG, "No secret key found")
            return null
        }

        // Build export data
        val exportData = TestAccountData(
            secretKey = secretKey,
            nodeId = account.peerId,
            username = account.username,
            displayName = identity.name,
            avatarHash = identity.profilePicHash,
            password = password
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(exportData)

        Log.i(TAG, "Export complete for: ${account.username}")
        return json
    }

    /**
     * Save export JSON to a file.
     *
     * @param json Export JSON string
     * @param outputFile File to write to
     */
    fun saveToFile(json: String, outputFile: File) {
        if (!BuildConfig.DEBUG) return

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json)
        Log.i(TAG, "Saved test account to ${outputFile.absolutePath}")
    }

    /**
     * Get recommended filename for export.
     */
    fun getRecommendedFilename(username: String): String {
        val safeName = username.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${safeName}-test-account.json"
    }
}
