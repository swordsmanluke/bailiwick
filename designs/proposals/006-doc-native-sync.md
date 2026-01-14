# Proposal 006: Doc-Native Sync Architecture

## Background

The original sync design was based on IPNS, which provided:
- A mutable pointer (`feed/latest`) to the current feed
- A monotonically increasing version number for quick "is there something new?" checks

Iroh Docs work differently:
- Key-value store with append-only entries per author
- Set reconciliation for efficient sync
- Event-driven updates via `INSERT_REMOTE` events
- Updates to existing keys create new entries (not in-place updates)

This proposal redesigns the sync architecture to work natively with Iroh Docs.

## Current Structure (IPNS-style)

```
User's Doc:
├── identity           → identity blob hash
├── circles/{id}       → circle definition blob hash
├── feed/latest        → feed blob hash (contains list of post hashes)
└── actions/{target}/{ts} → action blob hash (key exchange, intros)
```

Problems:
1. `feed/latest` is overwritten, but updates don't propagate reliably
2. Requires downloading entire feed blob to find new posts
3. No built-in "what's new" detection

## Proposed Structure (Doc-native)

```
User's Doc:
├── identity              → identity blob hash
├── circles/{id}          → circle definition blob hash
├── posts/{circle_id}/{ts} → encrypted post blob hash
└── actions/{target}/{ts}  → action blob hash
```

Changes:
1. Remove `feed/latest` - no longer needed
2. Add `posts/{circle_id}/{timestamp}` - each post is its own entry
3. Keep `actions/` - key exchange mechanism unchanged

## Key Design Decisions

### 1. Post Discovery via Doc Entries

Instead of:
```
feed/latest → feed blob → parse → list of post hashes → download each
```

Now:
```
posts/* entries → each value IS a post hash → download directly
```

### 2. "What's New" Detection

**Old**: Compare version numbers (we don't have this in Iroh)

**New**: Two approaches (can use both):

**A. Event-driven (real-time)**:
```kotlin
peerDoc.subscribe { event ->
    when (event.type) {
        INSERT_REMOTE -> {
            val key = event.key
            if (key.startsWith("posts/")) {
                processNewPost(key, event.contentHash)
            }
        }
    }
}
```

**B. Diff-based (on sync)**:
```kotlin
// Get all post keys from peer doc
val remotePostKeys = peerDoc.keysWithPrefix("posts/")

// Compare with what we've already processed
val newKeys = remotePostKeys - processedKeys

// Download only new posts
for (key in newKeys) {
    val hash = peerDoc.get(key)
    downloadAndProcessPost(hash)
}
```

### 3. Circle-Based Encryption (Unchanged)

Posts are still encrypted with circle keys:
```kotlin
// Publishing
val encryptedPost = circleKey.encrypt(postJson)
val hash = iroh.storeBlob(encryptedPost)
myDoc.set("posts/${circleId}/${timestamp}", hash)

// Downloading
val hash = peerDoc.get("posts/${circleId}/${timestamp}")
val encrypted = iroh.getBlob(hash)
val postJson = circleKey.decrypt(encrypted)  // Only works if we have the key
```

### 4. Key Versioning for Circle Membership Changes

When someone is removed from a circle:
1. Generate new circle key
2. Send new key to remaining members via `actions/` (encrypted with their public key)
3. New posts use new key
4. Old posts remain encrypted with old key (removed member can still see historical posts they had access to)

The `posts/{circle_id}/{timestamp}` structure naturally handles this:
- Old posts: decryptable with old key (if we have it)
- New posts: only decryptable with new key

## Implementation Plan

### Phase 1: Update Doc Key Structure

**Files to modify:**
- `IrohDocKeys.kt` - Add post key format

```kotlin
object IrohDocKeys {
    const val KEY_IDENTITY = "identity"
    const val KEY_FEED_LATEST = "feed/latest"  // DEPRECATED

    fun circleKey(circleId: Long) = "circles/$circleId"
    fun postKey(circleId: Long, timestamp: Long) = "posts/$circleId/$timestamp"
    fun actionKey(targetNodeId: String, timestamp: Long) = "actions/$targetNodeId/$timestamp"

    // Key prefix for listing
    const val POSTS_PREFIX = "posts/"
    fun postsForCirclePrefix(circleId: Long) = "posts/$circleId/"
}
```

### Phase 2: Update ContentPublisher

**Current flow:**
1. Get all posts for circle
2. Create IrohFeed blob with post hashes
3. Set `feed/latest` to feed hash

**New flow:**
1. For each unpublished post:
   - Encrypt post with circle key
   - Store as blob
   - Set `posts/{circleId}/{timestamp}` to blob hash
2. No feed blob needed

```kotlin
suspend fun publishPost(post: Post, circleId: Long, cipher: Encryptor): BlobHash {
    val irohPost = IrohPost(
        timestamp = post.timestamp,
        parentHash = post.parentHash,
        text = post.text,
        files = getFilesForPost(post.id),
        signature = post.signature
    )

    val hash = storeEncrypted(irohPost, cipher)

    // Store directly in doc with structured key
    val key = IrohDocKeys.postKey(circleId, post.timestamp ?: System.currentTimeMillis())
    iroh.getMyDoc().set(key, hash.toByteArray())

    // Update post record
    db.postDao().updateHash(post.id, hash)

    return hash
}
```

### Phase 3: Update ContentDownloader

**Current flow:**
1. Get `feed/latest` from peer doc
2. Download and decrypt feed blob
3. For each post hash in feed, download post

**New flow:**
1. List all `posts/*` keys in peer doc
2. For each key we haven't processed:
   - Extract circle ID from key
   - Get cipher for that circle (if we have access)
   - Download and decrypt post

```kotlin
suspend fun downloadPosts(doc: IrohDoc, nodeId: NodeId) {
    val postKeys = doc.keysWithPrefix(IrohDocKeys.POSTS_PREFIX)

    for (key in postKeys) {
        // Parse key: "posts/{circleId}/{timestamp}"
        val parts = key.split("/")
        if (parts.size != 3) continue

        val circleId = parts[1].toLongOrNull() ?: continue
        val timestamp = parts[2].toLongOrNull() ?: continue

        // Get hash from doc
        val hashBytes = doc.get(key) ?: continue
        val hash = String(hashBytes)

        // Skip if already have it
        if (db.postDao().findByHash(hash) != null) continue

        // Get cipher for this circle
        val cipher = EncryptorFactory.forCircle(db.keyDao(), circleId, nodeId)

        // Download and process
        downloadPost(hash, nodeId, cipher)
    }
}
```

### Phase 4: Add keysWithPrefix to IrohDoc

```kotlin
interface IrohDoc {
    // ... existing methods ...

    /**
     * List all keys matching a prefix.
     */
    suspend fun keysWithPrefix(prefix: String): List<String>
}

// Implementation
override suspend fun keysWithPrefix(prefix: String): List<String> {
    val query = Query.keyPrefix(prefix.toByteArray(), null)
    val entries = doc.getMany(query)
    return entries.map { String(it.key()) }.distinct()
}
```

### Phase 5: Event-Driven Sync (Enhancement)

For real-time updates when app is open:

```kotlin
class ContentSyncManager(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase
) {
    private val subscriptions = mutableMapOf<NodeId, Subscription>()

    suspend fun subscribeToPeer(peerDoc: IrohDoc, nodeId: NodeId) {
        val subscription = peerDoc.subscribe { key, value ->
            if (key.startsWith(IrohDocKeys.POSTS_PREFIX)) {
                // New post from peer - trigger download
                val hash = String(value)
                CoroutineScope(Dispatchers.IO).launch {
                    processNewPost(key, hash, nodeId)
                }
            }
        }
        subscriptions[nodeId] = subscription
    }
}
```

## Migration Path

1. **Backward compatibility**: Keep reading `feed/latest` if it exists
2. **Dual-write**: Publish to both old and new format during transition
3. **Cutover**: After both devices updated, remove old format support

```kotlin
suspend fun publishPending(cipher: Encryptor) {
    val pendingPosts = db.postDao().inNeedOfSync()

    for (post in pendingPosts) {
        val circleIds = db.circlePostDao().circlesForPost(post.id)
        for (circleId in circleIds) {
            // New format: individual post entry
            publishPost(post, circleId, cipher)
        }
    }

    // Legacy format: also update feed/latest (remove after migration)
    for (circle in db.circleDao().all()) {
        publishFeed(circle, cipher)
    }
}
```

## Benefits

1. **Reliable sync**: New entries sync reliably (no key overwrite issues)
2. **Efficient**: Only download posts we don't have
3. **Granular**: Can sync individual posts, not entire feed
4. **Event-driven**: Real-time updates via doc subscriptions
5. **Natural key versioning**: Old posts keep old keys, new posts use new keys

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Doc bloat from many post keys | Periodic cleanup of very old posts |
| Key listing performance | Use prefix queries, cache results |
| Migration complexity | Dual-write period, feature flag |

## Success Metrics

- Posts sync between devices within 30 seconds
- No duplicate post downloads
- Battery usage comparable to or better than IPNS approach
