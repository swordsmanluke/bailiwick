package com.perfectlunacy.bailiwick.testing

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.BuildConfig
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.Subscription
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.ALL_CIRCLE
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import java.security.MessageDigest

/**
 * Helper class for importing test accounts in E2E tests.
 * Only functional in debug builds.
 *
 * ## Workflow:
 *
 * 1. E2E test sends broadcast with test account JSON
 * 2. TestAccountReceiver stores the pending account data
 * 3. App startup calls `applyPendingTestAccountKey()` BEFORE Iroh init
 * 4. Iroh initializes with the imported secret key
 * 5. App startup calls `createTestAccountRecords()` AFTER DB init
 * 6. Account and Identity records are created with pre-computed NodeId
 *
 * The pre-computed NodeId in the test account data MUST match what Iroh derives
 * from the secret key. If they don't match, the import fails.
 */
object TestAccountHelper {
    private const val TAG = "TestAccountHelper"
    private const val IROH_CONFIG_PREFS = "iroh_config"
    private const val IROH_SECRET_KEY = "iroh_secret_key"

    /**
     * Apply the pending test account's secret key to SharedPreferences.
     * This MUST be called BEFORE IrohWrapper.create() to ensure Iroh uses
     * the imported key for node identity.
     *
     * @param context Application context
     * @return true if a test account key was applied, false otherwise
     */
    fun applyPendingTestAccountKey(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false

        val testAccount = TestAccountReceiver.consumePendingTestAccount() ?: return false

        Log.i(TAG, "Applying test account secret key for: ${testAccount.username}")

        // Store the secret key to SharedPreferences
        // IrohWrapper.create() will read this to create the node identity
        val prefs = context.getSharedPreferences(IROH_CONFIG_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(IROH_SECRET_KEY, testAccount.secretKey)
            .apply()

        // Store the test account data for later use in createTestAccountRecords()
        pendingTestAccountData = testAccount

        Log.i(TAG, "Test account secret key stored, expected NodeId: ${testAccount.nodeId}")
        return true
    }

    /**
     * Create Account and Identity records for the pending test account.
     * This MUST be called AFTER database initialization and ideally after
     * Iroh initialization to verify the NodeId matches.
     *
     * @param db Database instance
     * @param actualNodeId The NodeId that Iroh derived from the secret key
     * @return true if account was created, false otherwise
     */
    suspend fun createTestAccountRecords(
        db: BailiwickDatabase,
        actualNodeId: String
    ): Boolean {
        if (!BuildConfig.DEBUG) return false

        val testAccount = pendingTestAccountData ?: return false
        pendingTestAccountData = null

        // Verify NodeId matches what was pre-computed
        if (actualNodeId != testAccount.nodeId) {
            Log.e(TAG, "NodeId mismatch! Expected: ${testAccount.nodeId}, Actual: $actualNodeId")
            Log.e(TAG, "Test account import failed - NodeId was incorrectly pre-computed")
            return false
        }

        Log.i(TAG, "Creating test account records for: ${testAccount.username}")

        // Check if account already exists
        val existingAccount = db.accountDao().activeAccount()
        if (existingAccount != null) {
            Log.w(TAG, "Account already exists, skipping test account creation")
            return false
        }

        // Hash the password
        val passwordHash = hashPassword(testAccount.password)

        // Create Account record
        val account = Account(
            username = testAccount.username,
            passwordHash = passwordHash,
            peerId = actualNodeId,
            rootCid = "",
            sequence = 0,
            loggedIn = true
        )
        db.accountDao().insert(account)
        Log.d(TAG, "Created Account: ${testAccount.username}")

        // Create Identity record
        val identity = Identity(
            blobHash = null,
            owner = actualNodeId,
            name = testAccount.displayName,
            profilePicHash = testAccount.avatarHash
        )
        val identityId = db.identityDao().insert(identity)
        Log.d(TAG, "Created Identity: ${testAccount.displayName} (id=$identityId)")

        // Create subscription to self (always subscribed to ourselves)
        db.subscriptionDao().insert(Subscription(actualNodeId, 0))
        Log.d(TAG, "Created self-subscription")

        // Create default "everyone" circle
        val circle = Circle(
            name = ALL_CIRCLE,
            identityId = identityId,
            blobHash = null
        )
        db.circleDao().insert(circle)
        Log.d(TAG, "Created default circle")

        Log.i(TAG, "Test account creation complete: ${testAccount.username}")
        return true
    }

    /**
     * Check if there's a pending test account that needs to be processed.
     */
    fun hasPendingTestAccount(): Boolean {
        return BuildConfig.DEBUG && TestAccountReceiver.hasPendingTestAccount()
    }

    /**
     * Check if a test account key was applied and is waiting for record creation.
     */
    fun hasPendingTestAccountRecords(): Boolean {
        return BuildConfig.DEBUG && pendingTestAccountData != null
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Temporarily stores test account data between key application and record creation
    @Volatile
    private var pendingTestAccountData: TestAccountData? = null
}
