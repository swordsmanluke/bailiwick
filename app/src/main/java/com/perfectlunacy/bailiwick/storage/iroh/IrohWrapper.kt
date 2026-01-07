package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId
import computer.iroh.AuthorId
import computer.iroh.Blobs
import computer.iroh.Doc
import computer.iroh.Docs
import computer.iroh.Hash
import computer.iroh.Iroh
import computer.iroh.IrohException
import computer.iroh.Collection
import computer.iroh.SetTagOption
import kotlinx.coroutines.runBlocking

/**
 * Implementation of IrohNode using the iroh-ffi bindings.
 */
class IrohWrapper private constructor(
    private val context: Context,
    private val iroh: Iroh,
    private val defaultAuthorId: AuthorId,
    private val myDoc: Doc
) : IrohNode {

    companion object {
        private const val TAG = "IrohWrapper"
        private const val MY_DOC_KEY = "my_doc_namespace_id"

        /**
         * Initialize Iroh with persistent storage.
         */
        suspend fun create(context: Context): IrohWrapper {
            val dataDir = context.filesDir.resolve("iroh").absolutePath

            Log.i(TAG, "Initializing Iroh at $dataDir")

            // Create persistent Iroh node
            val iroh = Iroh.persistent(dataDir)

            // Get or create default author
            val defaultAuthorId = iroh.authors().default()

            // Get or create our primary document
            val myDoc = getOrCreateMyDoc(context, iroh)

            val nodeIdStr = iroh.net().nodeId().toString()
            val docId = myDoc.id()
            Log.i(TAG, "Iroh initialized. NodeId: $nodeIdStr, DocId: $docId")

            return IrohWrapper(context, iroh, defaultAuthorId, myDoc)
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
    }

    private val blobs: Blobs = iroh.blobs()
    private val docs: Docs = iroh.docs()

    override val nodeId: NodeId
        get() = runBlocking { iroh.net().nodeId().toString() }

    override val myDocNamespaceId: DocNamespaceId
        get() = runBlocking { myDoc.id() }

    // ===== Blob Operations =====

    override fun storeBlob(data: ByteArray): BlobHash {
        return runBlocking {
            try {
                val outcome = blobs.addBytes(data)
                val hash = outcome.hash.toHex()
                Log.d(TAG, "Stored blob: $hash (${data.size} bytes)")
                hash
            } catch (e: IrohException) {
                Log.e(TAG, "Failed to store blob: ${e.message}")
                throw e
            }
        }
    }

    override fun getBlob(hash: BlobHash): ByteArray? {
        return runBlocking {
            try {
                val irohHash = Hash.fromString(hash)
                blobs.readToBytes(irohHash)
            } catch (e: IrohException) {
                Log.w(TAG, "Failed to get blob $hash: ${e.message}")
                null
            }
        }
    }

    override fun hasBlob(hash: BlobHash): Boolean {
        return runBlocking {
            try {
                blobs.has(Hash.fromString(hash))
            } catch (e: IrohException) {
                false
            }
        }
    }

    // ===== Collection Operations =====

    override fun createCollection(entries: Map<String, BlobHash>): BlobHash {
        return runBlocking {
            try {
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
    }

    override fun getCollection(hash: BlobHash): Map<String, BlobHash>? {
        return runBlocking {
            try {
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
    }

    // ===== Doc Operations =====

    override fun createDoc(): DocNamespaceId {
        return runBlocking {
            try {
                val doc = docs.create()
                val id = doc.id()
                Log.d(TAG, "Created doc: $id")
                id
            } catch (e: IrohException) {
                Log.e(TAG, "Failed to create doc: ${e.message}")
                throw e
            }
        }
    }

    override fun openDoc(namespaceId: DocNamespaceId): IrohDoc? {
        return runBlocking {
            try {
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
    }

    override fun getMyDoc(): IrohDoc {
        return IrohDocImpl(myDoc, defaultAuthorId, blobs)
    }

    // ===== Network =====

    override fun isConnected(): Boolean {
        return runBlocking {
            try {
                // Check if we have a home relay connection
                val netInfo = iroh.net().homeRelay()
                netInfo != null
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun shutdown() {
        runBlocking {
            try {
                iroh.node().shutdown()
                Log.i(TAG, "Iroh shutdown")
            } catch (e: Exception) {
                Log.w(TAG, "Error during shutdown: ${e.message}")
            }
        }
    }
}

/**
 * Implementation of IrohDoc wrapping the actual Iroh Doc.
 */
private class IrohDocImpl(
    private val doc: Doc,
    private val authorId: AuthorId,
    private val blobs: Blobs
) : IrohDoc {

    override val namespaceId: DocNamespaceId
        get() = runBlocking { doc.id() }

    override fun set(key: String, value: ByteArray) {
        runBlocking {
            doc.setBytes(authorId, key.toByteArray(), value)
        }
    }

    override fun get(key: String): ByteArray? {
        return runBlocking {
            try {
                val entry = doc.getExact(authorId, key.toByteArray(), false)
                if (entry != null) {
                    // Read the content from the blob store
                    val hash = entry.contentHash()
                    blobs.readToBytes(hash)
                } else {
                    null
                }
            } catch (e: IrohException) {
                null
            }
        }
    }

    override fun delete(key: String) {
        runBlocking {
            doc.delete(authorId, key.toByteArray())
        }
    }

    override fun keys(): List<String> {
        return runBlocking {
            try {
                val query = computer.iroh.Query.all(null)
                val entries = doc.getMany(query)
                entries.map { entry ->
                    String(entry.key())
                }.distinct()
            } catch (e: IrohException) {
                emptyList()
            }
        }
    }

    override fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit) {
        runBlocking {
            doc.subscribe(object : computer.iroh.SubscribeCallback {
                override suspend fun event(event: computer.iroh.LiveEvent) {
                    if (event.type() == computer.iroh.LiveEventType.CONTENT_READY) {
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
            })
        }
    }

    override fun syncWith(nodeId: NodeId) {
        runBlocking {
            try {
                val publicKey = computer.iroh.PublicKey.fromString(nodeId)
                val nodeAddr = computer.iroh.NodeAddr(publicKey, null, listOf())
                doc.startSync(listOf(nodeAddr))
            } catch (e: IrohException) {
                // Log but don't throw
            }
        }
    }
}
