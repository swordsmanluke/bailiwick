# Blobs API Workaround for Doc Sync Issues

## Current Architecture

The sync system has two distinct phases:

1. **Discovery**: Find out what content exists (uses Iroh Docs)
2. **Download**: Get the actual content (uses Iroh Blobs)

```
┌─────────────────────────────────────────────────────────────┐
│                    Current Flow                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Discovery (BROKEN)              Download (WORKS)            │
│  ─────────────────              ───────────────              │
│  doc.keysWithPrefix("posts/")   iroh.downloadBlob(hash, id)  │
│  → Returns stale keys           → Successfully fetches blob  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## What's Working

The **Blobs API** works correctly:
- `iroh.storeBlob(data)` - Stores content locally
- `iroh.downloadBlob(hash, nodeId)` - Downloads from peer
- Uses BLAKE3 verified streaming
- Content-addressed, immutable

See `IrohWrapper.downloadBlob()` implementation at `IrohWrapper.kt:189-256`.

## What's Broken

The **Docs API** has sync issues:
- Initial sync works
- Subsequent syncs don't propagate new entries
- `syncWithAndWait()` returns success but data is stale
- Known issues: [iroh-docs #51](https://github.com/n0-computer/iroh-docs/issues/51), [#56](https://github.com/n0-computer/iroh-docs/issues/56)

## Workaround Options

### Option A: Manifest Blob

Replace doc-based discovery with a "manifest" blob that lists all content.

**How it works:**
1. Publisher creates a manifest blob listing all post hashes
2. Publisher shares manifest hash out-of-band (QR code, or fixed "latest" convention)
3. Downloader fetches manifest via blobs API
4. Downloader fetches each listed post via blobs API

**Pros:**
- Uses working blobs API for everything
- Simple to implement

**Cons:**
- Need to update manifest on every new post
- Need way to share latest manifest hash (bootstrapping problem)
- No incremental sync - must re-download manifest each time

```kotlin
// Publisher
data class Manifest(
    val timestamp: Long,
    val posts: List<BlobHash>  // All post hashes
)

val manifest = Manifest(System.currentTimeMillis(), getAllPostHashes())
val manifestHash = iroh.storeBlob(gson.toJson(manifest).toByteArray())
// Share manifestHash somehow...

// Downloader
val manifestData = iroh.downloadBlob(manifestHash, peerId)
val manifest = gson.fromJson(manifestData, Manifest::class.java)
for (postHash in manifest.posts) {
    iroh.downloadBlob(postHash, peerId)
}
```

### Option B: Gossip Protocol

Use Iroh's gossip feature to broadcast new content notifications.

**How it works:**
1. Each peer subscribes to a topic (e.g., peer's nodeId)
2. When publisher creates new post, broadcast hash to topic
3. Subscribers receive notification and download blob directly

**Pros:**
- Real-time notifications
- Incremental - only new content announced

**Cons:**
- Requires gossip infrastructure
- Need iroh-gossip FFI bindings (may not exist for Kotlin)
- Ephemeral - missed messages are lost

### Option C: Versioned Manifest with Known Location

Combine docs (for discovery bootstrapping) with blobs (for reliable content).

**How it works:**
1. Keep using docs for initial introduction (exchanging nodeIds)
2. Store a single "manifest" entry in doc: `manifest -> hash`
3. Manifest blob contains version number + all post hashes
4. Downloader checks manifest version, downloads if newer

**Pros:**
- Minimal doc usage (single entry)
- Docs might work for single overwrites (less buggy than many entries?)
- Blobs handle actual content

**Cons:**
- Still depends on docs for manifest hash
- Manifest overwrites might also not sync (same bug)

### Option D: Direct Peer Query (Custom Protocol)

Implement a custom request-response protocol over Iroh connections.

**How it works:**
1. Define a "list posts" request/response protocol
2. Downloader connects to peer and requests post list
3. Peer responds with current post hashes
4. Downloader fetches each via blobs API

**Pros:**
- Completely bypasses docs
- Real-time, always current

**Cons:**
- Requires custom protocol implementation
- Peer must be online to query
- More complex FFI work

## Recommendation

**Option A (Manifest Blob)** is the simplest workaround to implement now:

1. Minimal code changes
2. Uses proven blobs API
3. Can be tested quickly

The main challenge is sharing the manifest hash. Options:
- Include in QR code alongside doc ticket
- Store in a well-known doc entry (risk: same sync bug)
- Use a deterministic hash (risk: stale reads)

For production, **Option B (Gossip)** would be ideal but requires FFI bindings.

## Implementation Sketch for Option A

```kotlin
// In ContentPublisher
suspend fun publishManifest() {
    val posts = db.postDao().getPublishedPosts()
    val manifest = PostManifest(
        version = System.currentTimeMillis(),
        posts = posts.map { PostEntry(it.timestamp, it.blobHash!!) }
    )
    val json = gson.toJson(manifest)
    val hash = iroh.storeBlob(json.toByteArray())

    // Store hash in doc (hope it syncs...)
    myDoc.set("manifest", hash.toByteArray())
}

// In ContentDownloader
suspend fun syncPeerViaManifest(peer: PeerDoc) {
    // Try to get manifest hash from doc
    val doc = iroh.joinDoc(peer.docTicket) ?: return
    val manifestHash = doc.get("manifest")?.let { String(it) }

    if (manifestHash != null) {
        // Download manifest via blobs (reliable!)
        val manifestData = iroh.downloadBlob(manifestHash, peer.nodeId)
        val manifest = gson.fromJson(String(manifestData), PostManifest::class.java)

        // Download each post via blobs (reliable!)
        for (entry in manifest.posts) {
            if (db.postDao().findByHash(entry.hash) == null) {
                downloadPost(entry.hash, peer.nodeId, cipher)
            }
        }
    }
}
```

## Version Analysis

### Tested Versions
- **iroh-ffi v0.28.1**: Bug confirmed (Jan 2025 test)
- **iroh-ffi v0.35.0**: Bug confirmed (Jan 2025 test)

### Test Results

**v0.28.1 Test (Jan 12, 2025):**
We rebuilt iroh-ffi v0.28.1 from source and tested. Results:
- Introduction between devices succeeded
- Doc tickets exchanged correctly
- `SYNC_FINISHED` event received
- **But**: Local replica shows 0 keys after sync

```
Peer doc exzzxqt5x6or7xbl54dbch7d2ox5trxzvcmyoureucqs5lieposa has 0 keys: []
Found 0 post keys in peer doc
All entries for feed/latest (0): []
```

**Conclusion**: The sync bug is **not a regression** - it exists in iroh-docs since at least v0.28.1. This is a fundamental issue in the sync protocol, not something introduced in newer versions.

### Can We Force Re-Sync?

Investigated the FFI bindings for ways to force a full re-sync:

**Available Doc methods:**
- `startSync(peers)` - Initiate sync with specified peers
- `leave()` - Leave the doc (clears local replica)
- `getSyncPeers()` - Get currently connected peers
- `setDownloadPolicy(policy)` - Controls what content to download
  - `DownloadPolicy.everything()` - Download all content
  - `DownloadPolicy.nothing()` - Download nothing
  - `DownloadPolicy.everythingExcept(filters)` - Selective

**Conclusion:** There's no "forceResync" API. The `DownloadPolicy` controls *what* gets downloaded, but if entries aren't in the local replica (the core bug), there's nothing to download.

**Potential approach:** Call `leave()` then `joinDoc()` to force a completely fresh replica. We already do this but it doesn't fix the issue - suggesting the bug is in the sync protocol itself, not caching.

## Upgrade/Downgrade Path

**Status: Version changes do NOT fix the bug.**

We tested both older (v0.28.1) and newer (v0.35.0) versions of iroh-ffi. The sync bug exists in both. This confirms the issue is fundamental to iroh-docs, not a version-specific regression.

The upstream issues ([#51](https://github.com/n0-computer/iroh-docs/issues/51), [#56](https://github.com/n0-computer/iroh-docs/issues/56)) remain open.

**Next steps:**
1. Implement a workaround (Option A or C recommended)
2. Monitor upstream issues for a fix
3. Consider contributing a fix or detailed bug report to iroh-docs

## Related Issues

- [sync-system-a53](../.beads/) - Tracking upstream iroh-docs issues
- [Sync Failure Analysis](sync-failure.md) - Detailed problem documentation
