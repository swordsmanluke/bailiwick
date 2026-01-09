package com.perfectlunacy.bailiwick

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.perfectlunacy.bailiwick.services.IrohService
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import com.perfectlunacy.bailiwick.storage.iroh.IrohWrapper
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel
import com.perfectlunacy.bailiwick.viewmodels.BailwickViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents the initialization state of the app.
 */
sealed class InitState {
    object Loading : InitState()
    data class Ready(val accountExists: Boolean) : InitState()
    data class Error(val message: String, val cause: Throwable? = null) : InitState()
}

class BailiwickActivity : AppCompatActivity() {
    private var _bwModel: BailiwickViewModel? = null
    val bwModel: BailiwickViewModel
        get() = _bwModel ?: throw IllegalStateException(
            "ViewModel accessed before initialization completed. Check initState first."
        )

    private val _initState = MutableStateFlow<InitState>(InitState.Loading)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore saved state to preserve navigation and registerForActivityResult contracts
        super.onCreate(savedInstanceState)

        // Show UI immediately - splash screen will display while we initialize
        showDisplay()

        // Initialize asynchronously
        lifecycleScope.launch {
            initBailiwick()
        }
    }

    private suspend fun initBailiwick() {
        try {
            val accountExists = withContext(Dispatchers.Default) {
                // Check if already initialized (e.g., Activity recreation after QR scan)
                if (Bailiwick.isInitialized()) {
                    Log.i(TAG, "Bailiwick already initialized, reusing existing instance")
                    val bw = Bailiwick.getInstance()

                    // Reinitialize ViewModel with existing components
                    withContext(Dispatchers.Main) {
                        _bwModel = (viewModels<BailiwickViewModel> {
                            BailwickViewModelFactory(
                                applicationContext,
                                bw.network,
                                bw.iroh,
                                bw.keyring,
                                bw.db
                            )
                        }).value
                    }

                    return@withContext bw.network.accountExists()
                }

                Log.i(TAG, "Starting Bailiwick initialization...")

                // Initialize Iroh node
                val iroh = IrohWrapper.create(applicationContext)
                Log.d(TAG, "Iroh initialized")

                // Initialize device keyring (RSA keypair)
                val keyring = DeviceKeyring.create(applicationContext)
                Log.d(TAG, "Keyring initialized")

                // Initialize database with fallback to destructive migration (no users to migrate)
                val bwDb = getBailiwickDb(applicationContext)
                Log.d(TAG, "Database initialized")

                // Initialize network layer
                val bwNetwork = BailiwickNetworkImpl(
                    bwDb,
                    iroh.nodeId(),
                    applicationContext.filesDir.toPath()
                )
                Log.d(TAG, "Network layer initialized")

                // Check if account exists (DB access is fine here - on Default dispatcher)
                val hasAccount = bwNetwork.accountExists()
                Log.d(TAG, "Account exists: $hasAccount")

                // Initialize global singleton
                val cacheDir = applicationContext.filesDir.resolve("blobs").also { it.mkdirs() }
                Bailiwick.init(bwNetwork, iroh, bwDb, keyring, cacheDir)
                Log.d(TAG, "Bailiwick singleton initialized")

                // Initialize ViewModel (must access on main thread due to viewModels delegate)
                withContext(Dispatchers.Main) {
                    _bwModel = (viewModels<BailiwickViewModel> {
                        BailwickViewModelFactory(applicationContext, bwNetwork, iroh, keyring, bwDb)
                    }).value
                }
                Log.i(TAG, "Bailiwick initialization complete")

                hasAccount  // Return value from withContext block
            }

            Log.i(TAG, "Setting initState to Ready (accountExists=$accountExists)")
            _initState.value = InitState.Ready(accountExists)
            Log.i(TAG, "initState set to Ready")

            // Start background sync service directly from Activity to ensure it starts
            // even if the fragment misses the state change due to lifecycle issues
            IrohService.start(applicationContext)
            Log.i(TAG, "IrohService started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Bailiwick", e)
            _initState.value = InitState.Error(
                message = "Failed to initialize: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    private fun showDisplay() {
        // Start showing things!
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
    }

    companion object {
        const val TAG = "BailiwickActivity"

        // Pending QR scan result - stored statically to survive Activity recreation
        @Volatile
        var pendingQrResult: String? = null
            private set

        fun setPendingQrResult(result: String) {
            pendingQrResult = result
        }

        fun consumePendingQrResult(): String? {
            val result = pendingQrResult
            pendingQrResult = null
            return result
        }
    }
}
