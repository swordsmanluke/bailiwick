package com.perfectlunacy.bailiwick.fragments

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.crypto.KeyImporter
import com.perfectlunacy.bailiwick.databinding.FragmentFirstRunBinding
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.Circle
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.storage.BailiwickNetworkImpl.Companion.ALL_CIRCLE
import com.perfectlunacy.bailiwick.storage.db.getBailiwickDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * First run screen with options to create new account or import existing identity.
 */
class FirstRunFragment : Fragment() {

    private var _binding: FragmentFirstRunBinding? = null
    private val binding get() = _binding!!

    private var pendingFileUri: Uri? = null

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onFileSelected(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_first_run,
            container,
            false
        )

        // Bind button handlers
        binding.btnSignUp.setOnClickListener { view ->
            view.findNavController().navigate(R.id.action_firstRunFragment_to_newUserFragment)
        }

        binding.btnUseKey.setOnClickListener {
            openFilePicker()
        }

        return binding.root
    }

    private fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    private fun onFileSelected(uri: Uri) {
        pendingFileUri = uri
        showPasswordDialog()
    }

    private fun showPasswordDialog() {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val passwordInput = EditText(context).apply {
            hint = getString(R.string.import_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        dialogView.addView(passwordInput)

        AlertDialog.Builder(context)
            .setTitle(R.string.import_identity)
            .setMessage(R.string.import_password_message)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(context, R.string.password_required, Toast.LENGTH_SHORT).show()
                } else {
                    performImport(password)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performImport(password: String) {
        val uri = pendingFileUri ?: return
        val context = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show loading state
                binding.btnUseKey.isEnabled = false
                binding.btnSignUp.isEnabled = false

                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        KeyImporter.import(input, password)
                    } ?: KeyImporter.ImportResult.Error("Could not open file")
                }

                when (result) {
                    is KeyImporter.ImportResult.Success -> {
                        createAccountFromImport(result.data, password)
                    }
                    is KeyImporter.ImportResult.WrongPassword -> {
                        Toast.makeText(context, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                    }
                    is KeyImporter.ImportResult.InvalidFormat -> {
                        Toast.makeText(context, R.string.invalid_backup_file, Toast.LENGTH_SHORT).show()
                    }
                    is KeyImporter.ImportResult.UnsupportedVersion -> {
                        Toast.makeText(
                            context,
                            getString(R.string.unsupported_backup_version, result.version),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is KeyImporter.ImportResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnUseKey.isEnabled = true
                binding.btnSignUp.isEnabled = true
            }
        }
    }

    private suspend fun createAccountFromImport(data: KeyImporter.ImportedData, password: String) {
        val context = requireContext()

        withContext(Dispatchers.IO) {
            // Apply the secret key (will take effect on restart)
            KeyImporter.applyImport(context, data)

            // Get database
            val db = getBailiwickDb(context)

            // Hash the password
            val passwordHash = hashPassword(password)

            // We need to restart the app to get the correct NodeId from the imported key
            // For now, use a placeholder that will be updated on restart
            val placeholderNodeId = "pending_import_${System.currentTimeMillis()}"

            // Create account
            val account = Account(
                username = data.username,
                passwordHash = passwordHash,
                peerId = placeholderNodeId,
                rootCid = "",
                sequence = 0,
                loggedIn = true
            )
            db.accountDao().insert(account)

            // Create identity
            val identity = Identity(
                blobHash = null,
                owner = placeholderNodeId,
                name = data.displayName,
                profilePicHash = data.avatarHash
            )
            val identityId = db.identityDao().insert(identity)

            // Create default "All" circle
            val circle = Circle(
                name = ALL_CIRCLE,
                identityId = identityId,
                blobHash = null
            )
            db.circleDao().insert(circle)

            Log.i(TAG, "Account created from import: ${data.username}")
        }

        // Show success and prompt for restart
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(context)
                .setTitle(R.string.import_success)
                .setMessage(R.string.import_restart_required)
                .setPositiveButton(R.string.restart) { _, _ ->
                    // Restart the app
                    requireActivity().finishAffinity()
                    val intent = requireActivity().packageManager
                        .getLaunchIntentForPackage(requireActivity().packageName)
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "FirstRunFragment"

        @JvmStatic
        fun newInstance() = FirstRunFragment()
    }
}