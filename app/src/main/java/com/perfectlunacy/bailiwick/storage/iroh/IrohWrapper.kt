package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import android.util.Base64
import android.util.Log
import com.perfectlunacy.bailiwick.storage.BlobHash
import java.security.SecureRandom
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId
import computer.iroh.AuthorId
import computer.iroh.Blobs
import computer.iroh.Collection
import computer.iroh.Doc
import computer.iroh.Docs
import computer.iroh.Hash
import computer.iroh.Iroh
import computer.iroh.IrohException
import computer.iroh.NodeOptions
import computer.iroh.LiveEvent
import computer.iroh.LiveEventType
import computer.iroh.NodeAddr
import computer.iroh.PublicKey
import computer.iroh.Query
import computer.iroh.SetTagOption
import computer.iroh.ShareMode
import computer.iroh.AddrInfoOptions
import computer.iroh.DocTicket
import computer.iroh.SubscribeCallback
import computer.iroh.BlobDownloadOptions
import computer.iroh.BlobFormat
import computer.iroh.DownloadCallback
import computer.iroh.DownloadProgress
import computer.iroh.DownloadProgressType

/**
 * Implementation of IrohNode using the iroh-ffi bindings.
 *
 * ## Error Handling Policy
 *
 * This class uses consistent error handling strategies:
 *
 * - **Write operations** (storeBlob, createCollection, createDoc): Log error, rethrow exception.
 *   These operations must succeed for the app to function correctly.
 *
 * - **Read operations** (getBlob, getCollection, openDoc, get, keys): Log warning, return null/empty.
 *   Missing data is expected (e.g., content not yet synced), so failures are not exceptional.
 *
 * - **Query operations** (hasBlob, isConnected): Return false on error.
 *   These are best-effort queries where failure is equivalent to "not found/connected".
 *
 * - **Background operations** (shutdown, syncWith, subscribe): Log and suppress errors.
 *   These run in background contexts where failures shouldn't crash the caller.
 */
class IrohWrapper private constructor(
    private val iroh: Iroh,
    private val defaultAuthorId: AuthorId,
    private val myDoc: Doc,
    private val cachedNodeId: NodeId,
    private val cachedMyDocNamespaceId: DocNamespaceId
) : IrohNode {

    companion object {
        private const val TAG = "IrohWrapper"
        private const val MY_DOC_KEY = "my_doc_namespace_id"
        private const val SECRET_KEY = "iroh_secret_key"

        /**
         * Initialize Iroh with persistent storage.
         */
        suspend fun create(context: Context): IrohWrapper {
            val dataDir = context.filesDir.resolve("iroh").absolutePath

            Log.i(TAG, "Initializing Iroh at $dataDir")

            // Get or create persistent secret key to maintain stable node identity
            val secretKey = getOrCreateSecretKey(context)

            // Create persistent Iroh node with docs enabled and our secret key
            val options = NodeOptions(enableDocs = true, secretKey = secretKey)
            val iroh = Iroh.persistentWithOptions(dataDir, options)

            // Get or create default author
            val defaultAuthorId = iroh.authors().default()

            // Get or create our primary document
            val myDoc = getOrCreateMyDoc(context, iroh)

            // Cache values that don't change
            val nodeIdStr = iroh.net().nodeId().toString()
            val docId = myDoc.id()
            Log.i(TAG, "Iroh initialized. NodeId: $nodeIdStr, DocId: $docId")

            return IrohWrapper(iroh, defaultAuthorId, myDoc, nodeIdStr, docId)
        }

        private suspend fun getOrCreateMyDoc(context: Context, iroh: Iroh): Doc {
            // Try to load existing doc ID from preferences
            val prefs = context.getSharedPreferences("iroh_config", Context.MODE_PRIVATE)
            val savedDocId = prefs.getString(MY_DOC_KEY, null)

            if (savedDocId != null) {
                val existingDoc = iroh.docs().open(savedDocId)
                if (existingDoc != null) {
                    Log.i(TAG, "Opened existing doc: $savedDocId")
                    return existingDoc
                }
            }

            // Create a new document
            val newDoc = iroh.docs().create()
            val newDocId = newDoc.id()

            // Save the doc ID
            prefs.edit().putString(MY_DOC_KEY, newDocId).apply()
            Log.i(TAG, "Created new doc: $newDocId")

            return newDoc
        }

        /**
         * Get or create a persistent secret key for the Iroh node.
         * This ensures the NodeId remains stable across app restarts.
         */
        private fun getOrCreateSecretKey(context: Context): ByteArray {
            val prefs = context.getSharedPreferences("iroh_config", Context.MODE_PRIVATE)
            val savedKey = prefs.getString(SECRET_KEY, null)

            if (savedKey != null) {
                Log.d(TAG, "Using existing secret key")
                return Base64.decode(savedKey, Base64.NO_WRAP)
            }

            // Generate a new 32-byte secret key
            val newKey = ByteArray(32)
            SecureRandom().nextBytes(newKey)

            // Save it for future use
            val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)
            prefs.edit().putString(SECRET_KEY, encoded).apply()
            Log.i(TAG, "Generated new secret key")

            return newKey
        }
    }

    private val blobs: Blobs = iroh.blobs()
    private val docs: Docs = iroh.docs()

    override suspend fun nodeId(): NodeId = cachedNodeId

    override suspend fun myDocNamespaceId(): DocNamespaceId = cachedMyDocNamespaceId

    override suspend fun myDocTicket(): String {
        return try {
            val ticket = myDoc.share(ShareMode.READ, AddrInfoOptions.RELAY_AND_ADDRESSES)
            val ticketStr = ticket.toString()
            Log.d(TAG, "Generated doc ticket: ${ticketStr.take(50)}...")
            ticketStr
        } catch (e: IrohException) {
            Log.e(TAG, "Failed to generate doc ticket: ${e.message}")
            throw e
        }
    }

    // ===== Blob Operations =====

    override suspend fun storeBlob(data: ByteArray): BlobHash {
        return try {
            val outcome = blobs.addBytes(data)
            val hash = outcome.hash.toHex()
            Log.d(TAG, "Stored blob: $hash (${data.size} bytes)")
            hash
        } catch (e: IrohException) {
            Log.e(TAG, "Failed to store blob: ${e.message}")
            throw e
        }
    }

    override suspend fun getBlob(hash: BlobHash): ByteArray? {
        return try {
            val irohHash = Hash.fromString(hash)
            blobs.readToBytes(irohHash)
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to get blob $hash: ${e.message}")
            null
        }
    }

    override suspend fun downloadBlob(hash: BlobHash, nodeId: NodeId): ByteArray? {
        // First check if we already have it locally
        val local = getBlob(hash)
        if (local != null) {
            Log.d(TAG, "Blob $hash already available locally")
            return local
        }

        Log.d(TAG, "Downloading blob $hash from peer $nodeId")

        return try {
            val irohHash = Hash.fromString(hash)
            val publicKey = PublicKey.fromString(nodeId)
            val nodeAddr = NodeAddr(publicKey, null, listOf())

            // Use a named tag based on the hash to ensure persistence
            // Named tags prevent garbage collection and allow re-sharing
            val tag = SetTagOption.named("blob-$hash".toByteArray())
            val options = BlobDownloadOptions(
                BlobFormat.RAW,
                listOf(nodeAddr),
                tag
            )

            // Track download result
            var downloadFailed = false
            var failureReason: String? = null

            val callback = object : DownloadCallback {
                override suspend fun progress(progress: DownloadProgress) {
                    when (progress.type()) {
                        DownloadProgressType.ALL_DONE -> {
                            Log.d(TAG, "Blob $hash download complete")
                        }
                        DownloadProgressType.ABORT -> {
                            downloadFailed = true
                            failureReason = progress.asAbort().toString()
                            Log.w(TAG, "Blob $hash download aborted: $failureReason")
                        }
                        DownloadProgressType.PROGRESS -> {
                            // Just log progress
                            Log.d(TAG, "Blob $hash download in progress")
                        }
                        DownloadProgressType.FOUND -> {
                            Log.d(TAG, "Blob $hash found on peer")
                        }
                        else -> {
                            // Ignore other progress types
                        }
                    }
                }
            }

            // Download is a suspend function that completes when download finishes
            blobs.download(irohHash, options, callback)

            if (downloadFailed) {
                Log.w(TAG, "Blob $hash download failed: $failureReason")
                return null
            }

            // Now read from local store
            blobs.readToBytes(irohHash)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download blob $hash from $nodeId: ${e.message}")
            null
        }
    }

    override suspend fun hasBlob(hash: BlobHash): Boolean {
        return try {
            blobs.has(Hash.fromString(hash))
        } catch (e: IrohException) {
            Log.d(TAG, "hasBlob($hash) failed: ${e.message}")
            false
        }
    }

    // ===== Collection Operations =====

    override suspend fun createCollection(entries: Map<String, BlobHash>): BlobHash {
        return try {
            val collection = Collection()
            entries.forEach { (name, hash) ->
                collection.push(name, Hash.fromString(hash))
            }
            val result = blobs.createCollection(collection, SetTagOption.auto(), listOf())
            val hash = result.hash.toHex()
            Log.d(TAG, "Created collection: $hash with ${entries.size} entries")
            hash
        } catch (e: IrohException) {
            Log.e(TAG, "Failed to create collection: ${e.message}")
            throw e
        }
    }

    override suspend fun getCollection(hash: BlobHash): Map<String, BlobHash>? {
        return try {
            val irohHash = Hash.fromString(hash)
            val collection = blobs.getCollection(irohHash)
            val result = mutableMapOf<String, BlobHash>()
            for (blob in collection.blobs()) {
                result[blob.name] = blob.link.toHex()
            }
            result
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to get collection $hash: ${e.message}")
            null
        }
    }

    // ===== Doc Operations =====

    override suspend fun createDoc(): DocNamespaceId {
        return try {
            val doc = docs.create()
            val id = doc.id()
            Log.d(TAG, "Created doc: $id")
            id
        } catch (e: IrohException) {
            Log.e(TAG, "Failed to create doc: ${e.message}")
            throw e
        }
    }

    override suspend fun openDoc(namespaceId: DocNamespaceId): IrohDoc? {
        return try {
            val doc = docs.open(namespaceId)
            if (doc != null) {
                IrohDocImpl(doc, defaultAuthorId, blobs)
            } else {
                null
            }
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to open doc $namespaceId: ${e.message}")
            null
        }
    }

    override suspend fun joinDoc(ticket: String): IrohDoc? {
        return try {
            val docTicket = DocTicket(ticket)
            val doc = docs.join(docTicket)
            Log.i(TAG, "Joined doc: ${doc.id()}")
            IrohDocImpl(doc, defaultAuthorId, blobs)
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to join doc with ticket: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse doc ticket: ${e.message}")
            null
        }
    }

    override suspend fun getMyDoc(): IrohDoc {
        return IrohDocImpl(myDoc, defaultAuthorId, blobs)
    }

    // ===== Network =====

    override suspend fun isConnected(): Boolean {
        return try {
            // Check if we have a home relay connection
            val netInfo = iroh.net().homeRelay()
            netInfo != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun shutdown() {
        try {
            iroh.node().shutdown()
            Log.i(TAG, "Iroh shutdown")
        } catch (e: Exception) {
            Log.w(TAG, "Error during shutdown: ${e.message}")
        }
    }
}

/**
 * Implementation of IrohDoc wrapping the actual Iroh Doc.
 *
 * Follows the same error handling policy as [IrohWrapper].
 */
private class IrohDocImpl(
    private val doc: Doc,
    private val authorId: AuthorId,
    private val blobs: Blobs
) : IrohDoc {

    private val cachedNamespaceId: DocNamespaceId by lazy {
        // This is still blocking but only happens once, lazily
        // The underlying doc.id() is actually synchronous in the FFI
        doc.id()
    }

    override suspend fun namespaceId(): DocNamespaceId = cachedNamespaceId

    override suspend fun set(key: String, value: ByteArray) {
        doc.setBytes(authorId, key.toByteArray(), value)
    }

    override suspend fun get(key: String): ByteArray? {
        return try {
            // Query for the key from ANY author, not just our own
            // This is important for reading from peer docs
            val keyBytes = key.toByteArray()
            val query = Query.keyExact(keyBytes, null)
            val entries = doc.getMany(query)

            // Get the first (most recent) entry for this key
            val entry = entries.firstOrNull()
            if (entry != null) {
                // Read the content from the blob store
                val hash = entry.contentHash()
                blobs.readToBytes(hash)
            } else {
                null
            }
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to get key '$key': ${e.message}")
            null
        }
    }

    override suspend fun delete(key: String) {
        doc.delete(authorId, key.toByteArray())
    }

    override suspend fun keys(): List<String> {
        return try {
            val query = Query.all(null)
            val entries = doc.getMany(query)
            entries.map { entry ->
                String(entry.key())
            }.distinct()
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to list keys: ${e.message}")
            emptyList()
        }
    }

    private var activeCallback: SubscribeCallback? = null

    override suspend fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit): Subscription {
        val callback = object : SubscribeCallback {
            override suspend fun event(event: LiveEvent) {
                if (event.type() == LiveEventType.CONTENT_READY) {
                    try {
                        val entry = event.asInsertLocal()
                        if (entry != null) {
                            val key = String(entry.key())
                            val hash = entry.contentHash()
                            val value = blobs.readToBytes(hash)
                            onUpdate(key, value)
                        }
                    } catch (e: Exception) {
                        // Ignore errors during callback
                    }
                }
            }
        }
        activeCallback = callback
        doc.subscribe(callback)

        return object : Subscription {
            override fun unsubscribe() {
                // Note: Iroh FFI doesn't currently support unsubscribing,
                // so we just clear the reference. The callback will still
                // be registered but this allows us to track intent.
                activeCallback = null
            }
        }
    }

    override suspend fun syncWith(nodeId: NodeId) {
        try {
            val publicKey = PublicKey.fromString(nodeId)
            val nodeAddr = NodeAddr(publicKey, null, listOf())
            doc.startSync(listOf(nodeAddr))
        } catch (e: IrohException) {
            Log.w(TAG, "Failed to sync with $nodeId: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "IrohDocImpl"
    }
}
