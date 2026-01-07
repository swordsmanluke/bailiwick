# Proposal 005: Iroh Implementation Plan

**Status**: Active
**Author**: Architecture Review
**Date**: 2026-01-05
**Priority**: Critical

## Overview

Step-by-step plan to replace the IPFS layer with Iroh. Since the prototype has zero users, we can:
- Replace the database schema entirely (reset to version 1)
- Replace classes wholesale rather than migrate
- Skip all data migration concerns

## Prerequisites

### Development Environment

- [ ] Android Studio with NDK installed (version 25.x recommended)
- [ ] Rust toolchain (`rustup`)
- [ ] Android targets for Rust:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
  ```

---

## Phase 1: Build iroh-ffi for Android

**Goal**: Produce `iroh-android.aar` with native libraries for all Android architectures.

### Step 1.1: Clone and Configure iroh-ffi

```bash
# Clone the repository
git clone https://github.com/n0-computer/iroh-ffi.git
cd iroh-ffi

# Check out a stable release (check for latest)
git tag -l
git checkout v0.x.x  # Use latest stable
```

### Step 1.2: Configure Cargo for Android NDK

Create or update `~/.cargo/config.toml`:

```toml
[target.aarch64-linux-android]
ar = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
linker = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

[target.armv7-linux-androideabi]
ar = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
linker = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi21-clang"

[target.x86_64-linux-android]
ar = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
linker = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android21-clang"

[target.i686-linux-android]
ar = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
linker = "/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android21-clang"
```

**Note**: Replace `/path/to/ndk` with your actual NDK path (e.g., `~/Android/Sdk/ndk/25.2.9519653`).

### Step 1.3: Build the Kotlin Bindings

```bash
# From iroh-ffi directory
./make_kotlin.sh

# Output will be in:
# kotlin/iroh/build/outputs/aar/iroh-release.aar
```

If `make_kotlin.sh` doesn't handle Android, you may need to:

```bash
# Build for each Android target
cargo build --release --target aarch64-linux-android
cargo build --release --target armv7-linux-androideabi
cargo build --release --target x86_64-linux-android

# Generate Kotlin bindings
cargo run --bin uniffi-bindgen generate --library target/release/libiroh_ffi.so --language kotlin --out-dir kotlin/
```

### Step 1.4: Integrate into Bailiwick

```bash
# Copy AAR to Bailiwick
cp kotlin/iroh/build/outputs/aar/iroh-release.aar \
   /path/to/Bailiwick/app/libs/iroh.aar

# Copy native libraries if not bundled in AAR
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}
cp target/aarch64-linux-android/release/libiroh_ffi.so app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libiroh_ffi.so app/src/main/jniLibs/armeabi-v7a/
cp target/x86_64-linux-android/release/libiroh_ffi.so app/src/main/jniLibs/x86_64/
```

### Step 1.5: Update Gradle

```kotlin
// app/build.gradle

android {
    // ...
    ndkVersion = "25.2.9519653"  // Match your NDK version

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    // Remove old IPFS
    // implementation files("libs/lite-debug.aar")

    // Add Iroh
    implementation files("libs/iroh.aar")

    // Keep existing deps...
}
```

### Step 1.6: Verify Build

```bash
cd /path/to/Bailiwick
./gradlew assembleDebug
```

**Checkpoint**: App should build (but crash at runtime until we implement the wrapper).

---

## Phase 2: Create New Data Model

**Goal**: Replace IPFS-oriented entities with Iroh-oriented ones.

### Step 2.1: New Type Aliases

Create `app/src/main/java/com/perfectlunacy/bailiwick/storage/Types.kt`:

```kotlin
package com.perfectlunacy.bailiwick.storage

// Iroh uses BLAKE3 hashes (32 bytes, hex-encoded)
typealias BlobHash = String

// Iroh node identifiers (Ed25519 public key)
typealias NodeId = String

// Iroh document namespace ID
typealias DocNamespaceId = String

// Iroh author ID (for document writes)
typealias AuthorId = String
```

### Step 2.2: Simplified Database Entities

The current schema has 14 entities. For Iroh, we can simplify:

**Keep (with modifications)**:
- `Account` - Local user account
- `Identity` - User profile info
- `Post` - Social posts (change `cid` to `blobHash`)
- `PostFile` - File attachments (change `fileCid` to `blobHash`)
- `Circle` - Groups for sharing
- `CircleMember` - Circle membership
- `CirclePost` - Posts in circles
- `Key` - Encryption keys

**Remove**:
- `IpnsCache` - Replaced by Iroh Docs
- `Sequence` - Docs use timestamps, not sequences
- `Manifest` - Simplified with Docs
- `Subscription` - Rethink with Docs model
- `User` - Merge into simplified model

**Add**:
- `PeerDoc` - Maps peers to their Doc namespace IDs

### Step 2.3: Create New Entity Files

Create `app/src/main/java/com/perfectlunacy/bailiwick/models/db/PeerDoc.kt`:

```kotlin
package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId

/**
 * Maps a peer's NodeId to their Iroh Doc namespace.
 * This replaces IPNS - we subscribe to their Doc to get updates.
 */
@Entity(indices = [Index(value = ["nodeId"], unique = true)])
data class PeerDoc(
    @PrimaryKey val nodeId: NodeId,
    val docNamespaceId: DocNamespaceId,
    val displayName: String?,
    val lastSyncedAt: Long = 0,
    val isSubscribed: Boolean = true
)

@Dao
interface PeerDocDao {
    @Query("SELECT * FROM peerdoc WHERE isSubscribed = 1")
    fun subscribedPeers(): List<PeerDoc>

    @Query("SELECT * FROM peerdoc WHERE nodeId = :nodeId")
    fun findByNodeId(nodeId: NodeId): PeerDoc?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(peerDoc: PeerDoc)

    @Query("UPDATE peerdoc SET lastSyncedAt = :timestamp WHERE nodeId = :nodeId")
    fun updateLastSynced(nodeId: NodeId, timestamp: Long)

    @Delete
    fun delete(peerDoc: PeerDoc)
}
```

### Step 2.4: Update Post Entity

Modify `app/src/main/java/com/perfectlunacy/bailiwick/models/db/Post.kt`:

```kotlin
package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash

@Entity
data class Post(
    val authorId: Long,           // Local Identity ID
    val blobHash: BlobHash?,      // Iroh blob hash (was: cid)
    val timestamp: Long,
    val parentHash: BlobHash?,    // Parent post hash for threading (was: parent)
    val text: String,
    var signature: String
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    fun signatureContent(): String {
        return "$authorId|$timestamp|$parentHash|$text"
    }
}

@Dao
interface PostDao {
    @Query("SELECT * FROM post ORDER BY timestamp DESC")
    fun all(): List<Post>

    @Query("SELECT * FROM post WHERE blobHash = :hash")
    fun findByHash(hash: BlobHash): Post?

    @Query("SELECT * FROM post WHERE parentHash = :parentHash ORDER BY timestamp ASC")
    fun replies(parentHash: BlobHash): List<Post>

    @Query("SELECT COUNT(*) FROM post WHERE parentHash = :parentHash")
    fun replyCount(parentHash: BlobHash): Int

    @Insert
    fun insert(post: Post): Long

    @Update
    fun update(post: Post)
}
```

### Step 2.5: Update PostFile Entity

```kotlin
package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash

@Entity(indices = [Index(value = ["postId", "blobHash"], unique = true)])
data class PostFile(
    val postId: Long,
    val blobHash: BlobHash,       // Was: fileCid
    val mimeType: String
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface PostFileDao {
    @Query("SELECT * FROM postfile WHERE postId = :postId")
    fun filesForPost(postId: Long): List<PostFile>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(postFile: PostFile)
}
```

### Step 2.6: Reset Database

Update `app/src/main/java/com/perfectlunacy/bailiwick/storage/db/BailiwickDatabase.kt`:

```kotlin
package com.perfectlunacy.bailiwick.storage.db

import androidx.room.*
import com.perfectlunacy.bailiwick.models.db.*

@Database(
    entities = [
        Account::class,
        Identity::class,
        Post::class,
        PostFile::class,
        Circle::class,
        CircleMember::class,
        CirclePost::class,
        Key::class,
        PeerDoc::class,
        Action::class
    ],
    version = 1,  // Reset to 1!
    exportSchema = true
)
abstract class BailiwickDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun identityDao(): IdentityDao
    abstract fun postDao(): PostDao
    abstract fun postFileDao(): PostFileDao
    abstract fun circleDao(): CircleDao
    abstract fun circleMemberDao(): CircleMemberDao
    abstract fun circlePostDao(): CirclePostDao
    abstract fun keyDao(): KeyDao
    abstract fun peerDocDao(): PeerDocDao
    abstract fun actionDao(): ActionDao
}
```

**Note**: Delete the `schemas/` directory or clear old schema files to avoid migration conflicts.

---

## Phase 3: Create Iroh Wrapper

**Goal**: Implement the new Iroh integration layer.

### Step 3.1: Create Iroh Interface

Create `app/src/main/java/com/perfectlunacy/bailiwick/storage/iroh/Iroh.kt`:

```kotlin
package com.perfectlunacy.bailiwick.storage.iroh

import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Interface for Iroh operations.
 * This replaces the old IPFS interface.
 */
interface IrohNode {
    val nodeId: NodeId

    // Blob operations (content-addressed storage)
    fun storeBlob(data: ByteArray): BlobHash
    fun getBlob(hash: BlobHash): ByteArray?
    fun hasBlob(hash: BlobHash): Boolean

    // Collection operations (like IPFS directories)
    fun createCollection(entries: Map<String, BlobHash>): BlobHash
    fun getCollection(hash: BlobHash): Map<String, BlobHash>?

    // Doc operations (replaces IPNS)
    fun createDoc(): DocNamespaceId
    fun openDoc(namespaceId: DocNamespaceId): IrohDoc?
    fun getMyDoc(): IrohDoc

    // Network
    fun isConnected(): Boolean
    fun peerCount(): Int
}

interface IrohDoc {
    val namespaceId: DocNamespaceId

    fun set(key: String, value: ByteArray)
    fun get(key: String): ByteArray?
    fun delete(key: String)
    fun keys(): List<String>

    // Sync with other peers
    fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit)
    fun syncWith(nodeId: NodeId)
}
```

### Step 3.2: Implement Iroh Wrapper

Create `app/src/main/java/com/perfectlunacy/bailiwick/storage/iroh/IrohWrapper.kt`:

```kotlin
package com.perfectlunacy.bailiwick.storage.iroh

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.DocNamespaceId
import com.perfectlunacy.bailiwick.storage.NodeId
// Import generated Iroh bindings
import uniffi.iroh.*

class IrohWrapper private constructor(
    private val node: Iroh,
    private val myDoc: Doc
) : IrohNode {

    companion object {
        private const val TAG = "IrohWrapper"

        /**
         * Initialize Iroh with persistent storage.
         */
        suspend fun create(context: Context): IrohWrapper {
            val dataDir = context.filesDir.resolve("iroh").absolutePath

            Log.i(TAG, "Initializing Iroh at $dataDir")

            val options = NodeOptions(
                enableDocs = true,
                gcIntervalMillis = 300_000  // 5 minutes
            )

            val node = Iroh.persistentWithOptions(dataDir, options)

            // Create or open our primary document
            val myDoc = node.docs().create()

            Log.i(TAG, "Iroh initialized. NodeId: ${node.endpoint().nodeId()}")

            return IrohWrapper(node, myDoc)
        }
    }

    private val blobs = node.blobs()
    private val docs = node.docs()
    private val authors = node.authors()
    private val defaultAuthor = authors.default()

    override val nodeId: NodeId
        get() = node.endpoint().nodeId().toString()

    // ===== Blob Operations =====

    override fun storeBlob(data: ByteArray): BlobHash {
        val outcome = blobs.addBytes(data)
        Log.d(TAG, "Stored blob: ${outcome.hash.toHex()}")
        return outcome.hash.toHex()
    }

    override fun getBlob(hash: BlobHash): ByteArray? {
        return try {
            val irohHash = Hash.fromString(hash)
            blobs.readToBytes(irohHash)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get blob $hash: ${e.message}")
            null
        }
    }

    override fun hasBlob(hash: BlobHash): Boolean {
        return try {
            blobs.has(Hash.fromString(hash))
        } catch (e: Exception) {
            false
        }
    }

    // ===== Collection Operations =====

    override fun createCollection(entries: Map<String, BlobHash>): BlobHash {
        val collection = Collection()
        entries.forEach { (name, hash) ->
            collection.push(name, Hash.fromString(hash))
        }
        val result = blobs.createCollection(collection, SetTagOption.Auto, listOf())
        return result.hash.toHex()
    }

    override fun getCollection(hash: BlobHash): Map<String, BlobHash>? {
        return try {
            val collection = blobs.getCollection(Hash.fromString(hash))
            collection.blobs().associate { (name, blobHash) ->
                name to blobHash.toHex()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get collection $hash: ${e.message}")
            null
        }
    }

    // ===== Doc Operations =====

    override fun createDoc(): DocNamespaceId {
        val doc = docs.create()
        return doc.id().toString()
    }

    override fun openDoc(namespaceId: DocNamespaceId): IrohDoc? {
        return try {
            val doc = docs.open(NamespaceId.fromString(namespaceId))
            IrohDocImpl(doc, blobs, defaultAuthor)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open doc $namespaceId: ${e.message}")
            null
        }
    }

    override fun getMyDoc(): IrohDoc {
        return IrohDocImpl(myDoc, blobs, defaultAuthor)
    }

    // ===== Network =====

    override fun isConnected(): Boolean {
        // Check if we have any relay connection
        return try {
            node.endpoint().homeRelay() != null
        } catch (e: Exception) {
            false
        }
    }

    override fun peerCount(): Int {
        // Iroh doesn't expose peer count the same way
        // This is a placeholder
        return if (isConnected()) 1 else 0
    }

    fun shutdown() {
        node.shutdown()
    }
}

/**
 * Wrapper around an Iroh Doc.
 */
class IrohDocImpl(
    private val doc: Doc,
    private val blobs: Blobs,
    private val author: AuthorId
) : IrohDoc {

    override val namespaceId: DocNamespaceId
        get() = doc.id().toString()

    override fun set(key: String, value: ByteArray) {
        doc.setBytes(author, key.toByteArray(), value)
    }

    override fun get(key: String): ByteArray? {
        return try {
            val entry = doc.getExact(author, key.toByteArray(), false)
            entry?.contentBytes(blobs)
        } catch (e: Exception) {
            null
        }
    }

    override fun delete(key: String) {
        doc.del(author, key.toByteArray())
    }

    override fun keys(): List<String> {
        return doc.getMany(Query.all()).map { entry ->
            String(entry.key())
        }
    }

    override fun subscribe(onUpdate: (key: String, value: ByteArray) -> Unit) {
        // Subscribe to document changes
        doc.subscribe { event ->
            when (event) {
                is LiveEvent.ContentReady -> {
                    val key = String(event.entry.key())
                    val value = event.entry.contentBytes(blobs)
                    onUpdate(key, value)
                }
                else -> { /* ignore other events */ }
            }
        }
    }

    override fun syncWith(nodeId: NodeId) {
        doc.startSync(listOf(NodeAddr.fromString(nodeId)))
    }
}
```

### Step 3.3: Update Bailiwick Singleton

Modify `app/src/main/java/com/perfectlunacy/bailiwick/Bailiwick.kt`:

```kotlin
package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

object Bailiwick {
    const val VERSION = "0.3"  // Bump version for Iroh

    lateinit var db: BailiwickDatabase
        private set

    lateinit var iroh: IrohNode
        private set

    fun init(db: BailiwickDatabase, iroh: IrohNode) {
        this.db = db
        this.iroh = iroh
    }
}
```

### Step 3.4: Update Activity Initialization

Modify `app/src/main/java/com/perfectlunacy/bailiwick/BailiwickActivity.kt`:

```kotlin
// In onCreate or initialization code:

lifecycleScope.launch {
    // Initialize Iroh
    val iroh = IrohWrapper.create(applicationContext)

    // Initialize Database
    val db = Room.databaseBuilder(
        applicationContext,
        BailiwickDatabase::class.java,
        "bailiwick-db"
    ).fallbackToDestructiveMigration()  // OK since no users
     .build()

    // Initialize singleton
    Bailiwick.init(db, iroh)

    // Continue with app startup...
}
```

---

## Phase 4: Update Publishers

**Goal**: Rewrite publishers to use Iroh blobs and docs.

### Step 4.1: Simplify Publishing Model

Instead of complex manifest/feed/circle publishing, use a simpler model:

```
My Doc
├── "identity" → blob hash of IrohIdentity JSON
├── "feed/latest" → blob hash of latest FeedSnapshot
├── "feed/{timestamp}" → blob hash of historical FeedSnapshot
└── "circles/{circleId}" → blob hash of CircleSnapshot
```

### Step 4.2: Create New Publisher

Create `app/src/main/java/com/perfectlunacy/bailiwick/workers/ContentPublisher.kt`:

```kotlin
package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

/**
 * Publishes local content to Iroh.
 */
class ContentPublisher(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase
) {
    companion object {
        private const val TAG = "ContentPublisher"
    }

    private val gson = Gson()

    /**
     * Publish identity to my doc.
     */
    fun publishIdentity(identity: Identity): BlobHash {
        val json = gson.toJson(IrohIdentity(
            name = identity.name,
            profilePicHash = identity.profilePicCid  // Will update to use blob hash
        ))

        val hash = iroh.storeBlob(json.toByteArray())
        iroh.getMyDoc().set("identity", hash.toByteArray())

        Log.i(TAG, "Published identity: $hash")
        return hash
    }

    /**
     * Publish a post and update the feed.
     */
    fun publishPost(post: Post, cipher: Encryptor): BlobHash {
        // Get files for this post
        val files = db.postFileDao().filesForPost(post.id)

        val irohPost = IrohPost(
            timestamp = post.timestamp,
            parentHash = post.parentHash,
            text = post.text,
            files = files.map { IrohFileDef(it.mimeType, it.blobHash) },
            signature = post.signature
        )

        val json = gson.toJson(irohPost)
        val encrypted = cipher.encrypt(json.toByteArray())
        val hash = iroh.storeBlob(encrypted)

        // Update post with hash
        post.blobHash = hash
        db.postDao().update(post)

        Log.i(TAG, "Published post: $hash")
        return hash
    }

    /**
     * Publish a feed snapshot containing all posts for a circle.
     */
    fun publishFeed(circle: Circle, cipher: Encryptor): BlobHash {
        val posts = db.circlePostDao().postsForCircle(circle.id)
            .mapNotNull { it.blobHash }

        val feed = IrohFeed(
            updatedAt = System.currentTimeMillis(),
            posts = posts
        )

        val json = gson.toJson(feed)
        val encrypted = cipher.encrypt(json.toByteArray())
        val hash = iroh.storeBlob(encrypted)

        // Store in my doc under circle path
        iroh.getMyDoc().set("circles/${circle.id}", hash.toByteArray())

        // Also update latest feed pointer
        iroh.getMyDoc().set("feed/latest", hash.toByteArray())

        Log.i(TAG, "Published feed for circle ${circle.id}: $hash")
        return hash
    }
}

// Data classes for serialization
data class IrohIdentity(val name: String, val profilePicHash: BlobHash?)
data class IrohPost(
    val timestamp: Long,
    val parentHash: BlobHash?,
    val text: String,
    val files: List<IrohFileDef>,
    val signature: String
)
data class IrohFileDef(val mimeType: String, val blobHash: BlobHash)
data class IrohFeed(val updatedAt: Long, val posts: List<BlobHash>)
```

---

## Phase 5: Update Downloaders

**Goal**: Rewrite downloaders to sync from peer Docs.

### Step 5.1: Create Content Downloader

Create `app/src/main/java/com/perfectlunacy/bailiwick/workers/ContentDownloader.kt`:

```kotlin
package com.perfectlunacy.bailiwick.workers

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import com.perfectlunacy.bailiwick.storage.iroh.IrohNode

/**
 * Downloads content from peer Docs.
 */
class ContentDownloader(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase
) {
    companion object {
        private const val TAG = "ContentDownloader"
    }

    private val gson = Gson()

    /**
     * Sync with all subscribed peers.
     */
    fun syncAll() {
        val peers = db.peerDocDao().subscribedPeers()

        for (peer in peers) {
            try {
                syncPeer(peer)
                db.peerDocDao().updateLastSynced(peer.nodeId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync with ${peer.nodeId}: ${e.message}")
            }
        }
    }

    /**
     * Sync with a specific peer.
     */
    fun syncPeer(peer: PeerDoc) {
        Log.i(TAG, "Syncing with peer ${peer.nodeId}")

        val doc = iroh.openDoc(peer.docNamespaceId) ?: run {
            Log.w(TAG, "Could not open doc for ${peer.nodeId}")
            return
        }

        // Trigger sync
        doc.syncWith(peer.nodeId)

        // Download identity
        doc.get("identity")?.let { identityHashBytes ->
            val hash = String(identityHashBytes)
            downloadIdentity(hash, peer.nodeId)
        }

        // Download latest feed
        doc.get("feed/latest")?.let { feedHashBytes ->
            val hash = String(feedHashBytes)
            downloadFeed(hash, peer.nodeId)
        }
    }

    private fun downloadIdentity(hash: BlobHash, nodeId: NodeId) {
        val data = iroh.getBlob(hash) ?: return
        val identity = gson.fromJson(String(data), IrohIdentity::class.java)

        // Update or create identity in DB
        // ... (implementation depends on your Identity model)

        Log.i(TAG, "Downloaded identity for $nodeId: ${identity.name}")
    }

    private fun downloadFeed(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        val encrypted = iroh.getBlob(hash) ?: return
        val data = cipher.decrypt(encrypted)
        val feed = gson.fromJson(String(data), IrohFeed::class.java)

        Log.i(TAG, "Downloaded feed with ${feed.posts.size} posts")

        for (postHash in feed.posts) {
            downloadPost(postHash, nodeId, cipher)
        }
    }

    private fun downloadPost(hash: BlobHash, nodeId: NodeId, cipher: Encryptor) {
        // Skip if already have it
        if (db.postDao().findByHash(hash) != null) return

        val encrypted = iroh.getBlob(hash) ?: return
        val data = cipher.decrypt(encrypted)
        val irohPost = gson.fromJson(String(data), IrohPost::class.java)

        // Find or create identity for this author
        val identity = findOrCreateIdentity(nodeId)

        val post = Post(
            authorId = identity.id,
            blobHash = hash,
            timestamp = irohPost.timestamp,
            parentHash = irohPost.parentHash,
            text = irohPost.text,
            signature = irohPost.signature
        )

        val postId = db.postDao().insert(post)

        // Download files
        for (file in irohPost.files) {
            downloadFile(file.blobHash, postId, file.mimeType, cipher)
        }

        Log.i(TAG, "Downloaded post: $hash")
    }

    private fun downloadFile(hash: BlobHash, postId: Long, mimeType: String, cipher: Encryptor) {
        // Download blob to local cache
        val encrypted = iroh.getBlob(hash) ?: return
        val data = cipher.decrypt(encrypted)

        // Store in local file cache
        val cacheFile = Bailiwick.cacheDir.resolve("blobs/$hash")
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(data)

        // Record in DB
        db.postFileDao().insert(PostFile(postId, hash, mimeType))
    }

    private fun findOrCreateIdentity(nodeId: NodeId): Identity {
        // Implementation depends on your Identity model
        TODO("Implement identity lookup/creation")
    }
}
```

---

## Phase 6: Update Background Service

**Goal**: Simplify the background sync service.

### Step 6.1: Update IpfsService → IrohService

Rename and simplify `IpfsService.kt` to `IrohService.kt`:

```kotlin
package com.perfectlunacy.bailiwick.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.workers.ContentDownloader
import com.perfectlunacy.bailiwick.workers.ContentPublisher
import kotlinx.coroutines.*

class IrohService : Service() {
    companion object {
        private const val TAG = "IrohService"
        private val SYNC_INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startSyncLoop()
        return START_STICKY
    }

    private fun startSyncLoop() {
        syncJob = scope.launch {
            while (isActive) {
                try {
                    sync()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun sync() {
        val iroh = Bailiwick.iroh
        val db = Bailiwick.db

        if (!iroh.isConnected()) {
            Log.i(TAG, "Not connected, skipping sync")
            return
        }

        // Publish local changes
        val publisher = ContentPublisher(iroh, db)
        // ... publish pending content

        // Download from peers
        val downloader = ContentDownloader(iroh, db)
        downloader.syncAll()
    }

    override fun onDestroy() {
        syncJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
```

---

## Phase 7: Clean Up

**Goal**: Remove old IPFS code and finalize.

### Step 7.1: Delete Old Files

```bash
# Remove old IPFS implementation
rm app/src/main/java/com/perfectlunacy/bailiwick/storage/ipfs/IPFSWrapper.kt
rm app/src/main/java/com/perfectlunacy/bailiwick/storage/ipfs/IPFS.kt
rm app/src/main/java/com/perfectlunacy/bailiwick/storage/ipfs/IPFSCache.kt

# Remove old publishers
rm -rf app/src/main/java/com/perfectlunacy/bailiwick/workers/runners/publishers/
rm -rf app/src/main/java/com/perfectlunacy/bailiwick/workers/runners/downloaders/
rm app/src/main/java/com/perfectlunacy/bailiwick/workers/runners/PublishRunner.kt
rm app/src/main/java/com/perfectlunacy/bailiwick/workers/runners/DownloadRunner.kt

# Remove old models
rm app/src/main/java/com/perfectlunacy/bailiwick/models/db/IpnsCache.kt
rm app/src/main/java/com/perfectlunacy/bailiwick/models/db/Sequence.kt
rm app/src/main/java/com/perfectlunacy/bailiwick/models/db/Manifest.kt

# Remove old IPFS library
rm app/libs/lite-debug.aar

# Remove old schemas
rm -rf app/schemas/
```

### Step 7.2: Update CLAUDE.md

Add Iroh information to the project documentation.

### Step 7.3: Update .gitignore

```gitignore
# Iroh data directory (for local testing)
iroh-data/
```

---

## Testing Checklist

### Unit Tests

- [ ] Blob storage: store and retrieve
- [ ] Collection creation and listing
- [ ] Doc set/get/delete
- [ ] Post serialization/deserialization

### Integration Tests

- [ ] Node initialization on fresh install
- [ ] Publish identity and verify in doc
- [ ] Publish post and verify blob exists
- [ ] Sync between two nodes (emulator test)

### Manual Testing

- [ ] Fresh install flow
- [ ] Create account and identity
- [ ] Create and publish a post
- [ ] Add a peer (QR code flow)
- [ ] Receive content from peer
- [ ] App backgrounding/foregrounding
- [ ] Network connectivity changes

---

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| 1. Build iroh-ffi | 1-2 days | NDK setup |
| 2. New data model | 1-2 days | None |
| 3. Iroh wrapper | 2-3 days | Phase 1 |
| 4. Publishers | 2-3 days | Phases 2, 3 |
| 5. Downloaders | 2-3 days | Phases 2, 3 |
| 6. Background service | 1 day | Phases 4, 5 |
| 7. Clean up | 1 day | All above |
| **Total** | **~2 weeks** | |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| iroh-ffi build issues | Check GitHub issues; join Discord for support |
| API differences from docs | Read source code; test incrementally |
| Performance on mobile | Profile early; use persistent storage |
| Sync reliability | Implement retry logic; log extensively |

---

## Success Criteria

1. **Build succeeds** with new Iroh AAR
2. **Fresh install works** and creates identity
3. **Content publishes** to Iroh blobs
4. **Two devices sync** via Docs
5. **No old IPFS code** remains
6. **All tests pass**
