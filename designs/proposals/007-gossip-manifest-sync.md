# Proposal 007: Gossip-Based Manifest Synchronization

**Status:** Draft
**Created:** 2026-01-14
**Supersedes:** 005 (Iroh Implementation Plan), 006 (Doc-Native Sync)

## Summary

Replace Iroh Docs with a Gossip-based manifest announcement system. Each user maintains a hierarchical manifest structure (user manifest → circle manifests) and announces updates via Gossip topics. This eliminates the unreliable Docs sync mechanism while providing real-time update notifications.

## Motivation

### Problems with Current Approach

1. **Iroh Docs sync is unreliable**: Known bugs in iroh-docs (issues #51, #56) cause new entries to not propagate between peers. This affects versions v0.28.1 through v0.35.0+.

2. **Polling is inefficient**: The current 5-minute sync interval means users wait up to 5 minutes to see new content, even when peers are online.

3. **No notification mechanism**: Users have no way to know when new content is available without polling.

4. **Individual entry approach is fragile**: Publishing each post as a separate Doc entry (`posts/{circleId}/{timestamp}`) works around sync bugs but doesn't solve the fundamental problem.

### Why Gossip + Manifest

1. **Real-time notifications**: Gossip broadcasts reach all subscribed peers immediately.

2. **Swarm-based delivery**: Messages propagate through the swarm, so peers can receive updates from any connected node, not just the publisher.

3. **Version-based deduplication**: Monotonic version numbers let clients skip unchanged manifests efficiently.

4. **Hierarchical encryption**: User manifest encrypted with user key; circle manifests encrypted with circle keys. Peers see "something changed" but can only read authorized content.

5. **Simpler architecture**: Blobs + Gossip is a cleaner model than Blobs + Docs + polling.

## Design

### Cryptographic Migration: RSA → Ed25519/X25519

As part of this proposal, we migrate from RSA-2048 to Ed25519/X25519 for device identity. This dramatically reduces QR code size and aligns with modern cryptographic standards.

#### Why Change?

| Aspect | RSA-2048 (Current) | Ed25519/X25519 (Proposed) |
|--------|-------------------|---------------------------|
| Public key size | 256 bytes | 32 bytes |
| QR code density | High (hard to scan) | Low (easy to scan) |
| Performance | Slow | Fast |
| Security margin | Adequate | Excellent |

#### New Key Architecture

```
Device Identity (Ed25519)
├── Public key: 32 bytes - shared in Introduction
├── Private key: 32 bytes - stored in Android KeyStore
│
└── Derived Keys:
    ├── X25519 public: For ECDH key agreement
    └── X25519 private: Derived from Ed25519 private
```

**Separation from Iroh NodeId**: The device Ed25519 key is separate from Iroh's node identity key. This provides isolation - if Iroh's key changes (e.g., data reset), the device identity remains stable.

#### Cryptographic Operations

| Operation | Algorithm | Notes |
|-----------|-----------|-------|
| **Signing** | Ed25519 | Sign posts, manifests, announcements |
| **Key agreement** | X25519 ECDH | Derive shared secrets with peers |
| **Symmetric encryption** | AES-256-GCM | Encrypt content with derived keys |
| **Key derivation** | HKDF-SHA256 | Derive AES keys from ECDH shared secret |

#### X25519 Key Agreement Flow

```
Alice                                    Bob
─────                                    ───
Ed25519 keypair                          Ed25519 keypair
    │                                        │
    ▼                                        ▼
X25519 public ──────────────────────────► X25519 public
    │                                        │
    │         (exchange public keys)         │
    │                                        │
    ▼                                        ▼
ECDH(alice_priv, bob_pub)               ECDH(bob_priv, alice_pub)
    │                                        │
    └──────────► shared_secret ◄─────────────┘
                      │
                      ▼
              HKDF(shared_secret)
                      │
                      ▼
                  AES-256 key
```

#### Library Support

Android/Kotlin options for Ed25519/X25519:
- **Tink** (Google): Full support, Android KeyStore integration
- **libsodium** (via Lazysodium-android): Battle-tested, used by Signal
- **BouncyCastle**: Already in project, supports Ed25519

Recommended: **Tink** for Android KeyStore integration, or **Lazysodium** for libsodium compatibility.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Gossip Layer                            │
│  - One topic per user (dedicated topic key)                 │
│  - Encrypted announcements: {manifestHash, version}         │
│  - Swarm-based message propagation                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Manifest Layer                           │
│  User Manifest (encrypted with user's public key):          │
│  ├── identity: BlobHash                                     │
│  ├── circles: Map<CircleId, BlobHash>  (circle manifests)   │
│  └── actions: Map<NodeId, List<BlobHash>>                   │
│                                                             │
│  Circle Manifest (encrypted with circle key):               │
│  ├── circleId: Int                                          │
│  ├── posts: List<BlobHash>                                  │
│  └── members: List<NodeId>                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Blob Layer                             │
│  - Content-addressed storage (BLAKE3)                       │
│  - Immutable, encrypted content                             │
│  - Downloaded from any peer that has it                     │
└─────────────────────────────────────────────────────────────┘
```

### Manifest Structure

#### User Manifest

The top-level manifest, encrypted with the user's RSA public key:

```kotlin
data class UserManifest(
    val version: ULong,                      // Monotonically increasing
    val identityHash: BlobHash,              // User's IrohIdentity
    val circleManifests: Map<Int, BlobHash>, // CircleId → CircleManifest hash
    val actions: Map<NodeId, List<BlobHash>>,// Pending actions per peer
    val updatedAt: Long                      // Timestamp
)
```

#### Circle Manifest

Per-circle content listing, encrypted with the circle's AES key:

```kotlin
data class CircleManifest(
    val circleId: Int,
    val name: String,
    val posts: List<PostEntry>,      // Ordered by timestamp, newest first
    val members: List<NodeId>,       // Current circle members
    val updatedAt: Long
)

data class PostEntry(
    val hash: BlobHash,
    val timestamp: Long,
    val authorNodeId: NodeId         // For multi-author circles
)
```

**Content Retention**: Only posts from the last 30 days are included in manifests. Older posts are dropped from the manifest and their blobs unpinned, allowing Iroh's garbage collector to reclaim storage. This keeps manifests small (typically a few KB of JSON hash references) regardless of how long a user has been active.

#### Version Semantics

- `version` is a 64-bit unsigned integer starting at 1
- Increments by 1 for ANY change to the manifest (new post, updated identity, new action, etc.)
- Clients store `lastKnownVersion` per peer
- Only download manifest when `announcedVersion > lastKnownVersion`

### Gossip Protocol

#### Topic Key Generation

Each user generates a dedicated 32-byte topic key during account creation:

```kotlin
fun generateTopicKey(): ByteArray {
    return SecureRandom().generateSeed(32)
}
```

The topic key is:
- Stored locally in SharedPreferences (like the Iroh secret key)
- Shared with peers during introduction (in the QR code)
- Used to derive the Gossip topic for that user

#### Announcement Message

Messages broadcast on the topic, encrypted with the user's RSA public key:

```kotlin
data class ManifestAnnouncement(
    val manifestHash: BlobHash,      // Hash of encrypted UserManifest blob
    val version: ULong,              // Current manifest version
    val timestamp: Long,             // When published
    val signature: String            // Ed25519 signature for authenticity
)
```

The announcement is serialized to JSON, encrypted with RSA, then broadcast.

#### Subscription Flow

```
1. Peer Introduction (QR Code Exchange)
   ├── Receive peer's: NodeId, RSA public key, topic key
   └── Store in database

2. Subscribe to Peer's Topic
   ├── gossip.subscribe(topicKey, bootstrapPeers, callback)
   └── Store Sender handle for later unsubscribe

3. On Message Received
   ├── Decrypt with peer's RSA public key
   ├── Verify signature
   ├── Check version > lastKnownVersion
   ├── If newer: download and process manifest
   └── Update lastKnownVersion

4. On Publishing
   ├── Increment version
   ├── Publish manifest blob
   ├── Create signed announcement
   ├── Encrypt and broadcast
   └── Store locally
```

### Peer Introduction Changes

Update the `Introduction` data model to use Ed25519 and include topic key and addresses:

```kotlin
data class Introduction(
    val version: Int = 2,            // Protocol version (1 = RSA, 2 = Ed25519)
    val isResponse: Boolean,
    val peerId: NodeId,              // Iroh node ID (for network routing)
    val name: String,
    val publicKey: String,           // Ed25519 public key (32 bytes, Base64 = 44 chars)
    val topicKey: String,            // Topic key (32 bytes, Base64 = 44 chars)
    val addresses: List<String>,     // Peer addresses for bootstrap
    // Removed: docTicket (no longer needed)
    // Changed: publicKey is now Ed25519 instead of RSA
)
```

#### QR Code Size Comparison

```
Version 1 (RSA - Current):
├── peerId:      64 chars (NodeId)
├── name:        ~20 chars
├── publicKey:   344 chars (RSA-2048 Base64)  ← Bottleneck
├── docTicket:   ~200 chars
└── Total:       ~628 chars → QR v13+ (69x69 modules)

Version 2 (Ed25519 - Proposed):
├── version:     1 char
├── peerId:      64 chars (NodeId)
├── name:        ~20 chars
├── publicKey:   44 chars (Ed25519 Base64)    ← 8x smaller!
├── topicKey:    44 chars (Base64)
├── addresses:   ~80 chars
└── Total:       ~253 chars → QR v7 (45x45 modules)
```

**Result**: 60% smaller QR codes that are much easier to scan.

Update QR code generation and scanning to use the new format.

### Database Schema Changes

#### New Tables

```kotlin
@Entity(tableName = "peer_topic")
data class PeerTopic(
    @PrimaryKey
    val nodeId: NodeId,
    val ed25519PublicKey: ByteArray, // 32-byte Ed25519 public key
    val topicKey: ByteArray,         // 32-byte topic key
    val addresses: List<String>,     // Known addresses for bootstrap
    val lastKnownVersion: ULong,     // Last processed version
    val lastManifestHash: BlobHash?, // For change detection
    val isSubscribed: Boolean,
    val subscribedAt: Long
)

@Entity(tableName = "manifest_cache")
data class ManifestCache(
    @PrimaryKey
    val nodeId: NodeId,
    val manifestHash: BlobHash,
    val version: ULong,
    val downloadedAt: Long,
    val userManifestJson: String     // Cached decrypted manifest
)
```

#### Removed Tables

- `PeerDoc` - No longer needed (replaced by `PeerTopic`)
- Any Doc-related caching

#### Modified Tables

`Account` - Add:
```kotlin
val ed25519PublicKey: ByteArray,   // My Ed25519 public key (32 bytes)
val topicKey: ByteArray,           // My topic key (32 bytes)
val manifestVersion: ULong         // Current manifest version
```

Note: Ed25519 private key stored in Android KeyStore (not in Room database).

### Content Publishing Flow

```kotlin
class ManifestPublisher(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase,
    private val gossip: Gossip
) {
    private var sender: Sender? = null

    suspend fun publishManifest() {
        val account = db.accountDao().getAccount() ?: return

        // 1. Build circle manifests
        val circleManifests = mutableMapOf<Int, BlobHash>()
        for (circle in db.circleDao().getAllCircles()) {
            val manifest = buildCircleManifest(circle)
            val encrypted = encryptWithCircleKey(manifest, circle.id)
            val hash = iroh.storeBlob(encrypted)
            circleManifests[circle.id] = hash
        }

        // 2. Build user manifest
        val userManifest = UserManifest(
            version = account.manifestVersion + 1u,
            identityHash = getIdentityHash(),
            circleManifests = circleManifests,
            actions = getPendingActions(),
            updatedAt = System.currentTimeMillis()
        )

        // 3. Store encrypted manifest (encrypted with derived symmetric key)
        val manifestJson = gson.toJson(userManifest)
        val manifestKey = deriveManifestKey(account.ed25519PrivateKey)
        val encrypted = aesGcmEncrypt(manifestJson, manifestKey)
        val manifestHash = iroh.storeBlob(encrypted)

        // 4. Create and sign announcement
        val announcement = ManifestAnnouncement(
            manifestHash = manifestHash,
            version = userManifest.version,
            timestamp = System.currentTimeMillis(),
            signature = ed25519Sign(manifestHash + version, account.ed25519PrivateKey)
        )

        // 5. Broadcast to topic (announcement is signed, not encrypted)
        val announcementJson = gson.toJson(announcement)
        sender?.broadcast(announcementJson.toByteArray())

        // 6. Update local version
        db.accountDao().updateManifestVersion(userManifest.version)
    }

    suspend fun initializeGossip() {
        val account = db.accountDao().getAccount() ?: return
        sender = gossip.subscribe(
            topic = account.topicKey,
            bootstrap = getBootstrapPeers(),
            cb = NoOpCallback  // We only publish, don't receive on our own topic
        )
    }
}
```

### Content Downloading Flow

```kotlin
class ManifestDownloader(
    private val iroh: IrohNode,
    private val db: BailiwickDatabase,
    private val gossip: Gossip,
    private val deviceKeyring: DeviceKeyring  // Ed25519/X25519 keys
) {
    private val subscriptions = mutableMapOf<NodeId, Sender>()

    suspend fun subscribeToAllPeers() {
        val peers = db.peerTopicDao().getAllSubscribed()
        for (peer in peers) {
            subscribeToPeer(peer)
        }
    }

    private suspend fun subscribeToPeer(peer: PeerTopic) {
        val sender = gossip.subscribe(
            topic = peer.topicKey,
            bootstrap = getBootstrapPeers(),
            cb = object : GossipMessageCallback {
                override suspend fun onMessage(msg: Message) {
                    handleMessage(msg, peer)
                }
            }
        )
        subscriptions[peer.nodeId] = sender
    }

    private suspend fun handleMessage(msg: Message, peer: PeerTopic) {
        when (msg.type()) {
            MessageType.RECEIVED -> {
                val content = msg.asReceived()
                processAnnouncement(content.content, peer)
            }
            MessageType.NEIGHBOR_UP -> {
                Log.i(TAG, "Peer ${msg.asNeighborUp()} connected")
            }
            MessageType.NEIGHBOR_DOWN -> {
                Log.i(TAG, "Peer ${msg.asNeighborDown()} disconnected")
            }
            else -> { /* ignore */ }
        }
    }

    private suspend fun processAnnouncement(data: ByteArray, peer: PeerTopic) {
        // 1. Parse announcement (not encrypted, but signed)
        val json = String(data)
        val announcement = gson.fromJson(json, ManifestAnnouncement::class.java)

        // 2. Verify Ed25519 signature
        if (!ed25519Verify(announcement.signature, peer.ed25519PublicKey)) {
            Log.w(TAG, "Invalid signature from ${peer.nodeId}")
            return
        }

        // 3. Check version
        if (announcement.version <= peer.lastKnownVersion) {
            Log.d(TAG, "Already have version ${announcement.version}")
            return
        }

        // 4. Download manifest
        val manifestData = iroh.downloadBlob(announcement.manifestHash, peer.nodeId)
        if (manifestData == null) {
            Log.e(TAG, "Failed to download manifest ${announcement.manifestHash}")
            return
        }

        // 5. Decrypt manifest using X25519 key agreement
        val sharedSecret = x25519KeyAgreement(
            deviceKeyring.x25519PrivateKey,
            deriveX25519Public(peer.ed25519PublicKey)
        )
        val manifestKey = hkdfDerive(sharedSecret, "manifest")
        val decrypted = aesGcmDecrypt(manifestData, manifestKey)
        val userManifest = gson.fromJson(String(decrypted), UserManifest::class.java)

        // 6. Process manifest contents
        processUserManifest(userManifest, peer)

        // 7. Update tracking
        db.peerTopicDao().updateVersion(peer.nodeId, announcement.version)
    }

    private suspend fun processUserManifest(manifest: UserManifest, peer: PeerTopic) {
        // Download and update identity
        downloadIdentity(manifest.identityHash, peer.nodeId)

        // Process actions meant for us
        val myActions = manifest.actions[iroh.nodeId()] ?: emptyList()
        for (actionHash in myActions) {
            downloadAndProcessAction(actionHash, peer.nodeId)
        }

        // Process circle manifests we have keys for
        for ((circleId, circleManifestHash) in manifest.circleManifests) {
            val cipher = getCipherForCircle(circleId, peer.nodeId)
            if (cipher != null) {
                downloadCircleManifest(circleManifestHash, cipher, peer.nodeId)
            }
        }
    }
}
```

### Bootstrap Peers

Gossip requires bootstrap peers for initial connectivity. Options:

1. **Relay servers as bootstrap**: Use Iroh's relay servers as initial bootstrap points
2. **Known peers**: Use already-connected peers from previous sessions
3. **Hardcoded fallback**: Include default bootstrap addresses in the app

```kotlin
fun getBootstrapPeers(): List<String> {
    val peers = mutableListOf<String>()

    // Add relay server if connected
    iroh.net().homeRelay()?.let { relay ->
        peers.add(relay)
    }

    // Add known peer addresses from introductions
    val knownPeers = db.peerTopicDao().getAllSubscribed()
    for (peer in knownPeers) {
        peers.addAll(peer.addresses)
    }

    return peers.distinct()
}
```

### Service Architecture

Replace `IrohService` with `GossipService`:

```kotlin
class GossipService : LifecycleService() {
    private lateinit var gossip: Gossip
    private lateinit var publisher: ManifestPublisher
    private lateinit var downloader: ManifestDownloader

    override fun onCreate() {
        super.onCreate()

        val iroh = Bailiwick.iroh
        gossip = iroh.gossip()

        publisher = ManifestPublisher(iroh, Bailiwick.db, gossip)
        downloader = ManifestDownloader(iroh, Bailiwick.db, gossip, deviceKey)

        lifecycleScope.launch {
            // Initialize our topic
            publisher.initializeGossip()

            // Subscribe to all peers
            downloader.subscribeToAllPeers()
        }
    }

    // Called when local content changes
    suspend fun publishUpdate() {
        publisher.publishManifest()
    }
}
```

### Offline Recovery

Since Gossip messages propagate through the swarm, offline recovery works as follows:

1. **On reconnect**: Subscribe to peer topics
2. **Swarm propagation**: Other online peers relay recent announcements
3. **Version check**: Only download if version > lastKnownVersion
4. **Blob availability**: Blobs remain available from any peer that has them

If a peer has been offline for extended periods and missed all swarm messages:

```kotlin
// Request current manifest from any connected peer
suspend fun requestCurrentManifest(peerNodeId: NodeId) {
    // Send a direct request via Gossip broadcastNeighbors
    val request = ManifestRequest(targetPeer = peerNodeId)
    sender?.broadcastNeighbors(gson.toJson(request).toByteArray())
}

// Handle incoming manifest requests
private fun handleManifestRequest(request: ManifestRequest) {
    if (request.targetPeer == myNodeId) {
        // Re-broadcast our current announcement
        publisher.publishManifest()
    }
}
```

## Migration Plan

### Phase 0: Cryptographic Migration

1. Add Ed25519/X25519 support via Tink or Lazysodium
2. Create new `DeviceKeyring` with Ed25519 keypair generation
3. Add key derivation functions (Ed25519 → X25519, HKDF)
4. Update encryption utilities to use AES-256-GCM
5. Add protocol version field to `Introduction`
6. Maintain RSA support for reading old data (if needed)

### Phase 1: Add Gossip Infrastructure

1. Add `PeerTopic` table and DAO
2. Add `topicKey` and `manifestVersion` to Account
3. Create `ManifestPublisher` and `ManifestDownloader`
4. Create `GossipService`

### Phase 2: Update Introduction Flow

1. Update `Introduction` data model with topic key
2. Update QR code generation/scanning
3. Create `PeerTopic` entries on introduction
4. Generate topic key on account creation

### Phase 3: Parallel Operation

1. Run both `IrohService` (Docs) and `GossipService` (Gossip)
2. Publish to both systems
3. Download from both, deduplicate
4. Monitor reliability metrics

### Phase 4: Remove Docs

1. Stop publishing to Docs
2. Remove Doc-related code
3. Remove `PeerDoc` table
4. Clean up IrohWrapper Doc methods

### Phase 5: Cleanup

1. Remove dead code
2. Update documentation
3. Update tests

## Security Considerations

### Topic Privacy

- Topic keys are only shared with authorized peers during introduction
- Without the topic key, attackers cannot subscribe to a user's announcements
- Topic keys can be rotated by sharing new keys with existing peers

### Announcement Encryption

- Announcements encrypted with user's RSA public key
- Only peers with the public key can decrypt
- Signature prevents spoofed announcements

### Circle Key Distribution

- Circle manifests encrypted with circle-specific AES keys
- Keys distributed via UpdateKey actions (unchanged from current system)
- Revoked peers lose access to new circle manifests

### Replay Protection

- Monotonic version numbers prevent old announcements from being replayed
- Timestamps provide additional ordering guarantee
- Signatures prove announcement authenticity

## Testing Strategy

### Unit Tests

- Manifest serialization/deserialization
- Encryption/decryption round-trips
- Version comparison logic
- Bootstrap peer selection

### Integration Tests

- Topic subscription and message receipt
- Manifest publishing and downloading
- Offline recovery scenarios
- Multi-peer swarm propagation

### Manual Testing

- QR code introduction with topic key
- Real-time update visibility
- Extended offline recovery
- Large manifest handling

## Resolved Design Questions

1. **Topic key rotation**: Deferred to future work. For now, topic keys are static after creation.

2. **Manifest size limits**: Not a concern. Manifests are JSON files containing only text hash values pointing to other content. Even with thousands of posts, a manifest is well under 1MB - smaller than a single image file. Additionally, Bailiwick drops posts older than 30 days, naturally limiting manifest growth.

3. **Garbage collection**: Unpin old manifest versions and let Iroh's garbage collector reclaim storage. The internal retention standard is 30 days - content older than that is considered deletable.

4. **Peer discovery/bootstrap**: Two mechanisms:
   - **Public relay nodes**: Iroh's relay servers can serve as initial bootstrap points
   - **Peer addresses in introduction**: Include discovered IP addresses in the QR code key exchange, so new friends immediately know how to reach each other

## Appendix: Iroh Gossip API Reference

```kotlin
// Subscribe to a topic
val sender: Sender = gossip.subscribe(
    topic = ByteArray,           // 32-byte topic key
    bootstrap = List<String>,    // Bootstrap peer addresses
    cb = GossipMessageCallback   // Message handler
)

// Broadcast to all peers in swarm
sender.broadcast(msg: ByteArray)

// Broadcast to direct neighbors only
sender.broadcastNeighbors(msg: ByteArray)

// Message types
enum class MessageType {
    NEIGHBOR_UP,      // Peer connected
    NEIGHBOR_DOWN,    // Peer disconnected
    RECEIVED,         // Message received
    JOINED,           // Peers joined swarm
    LAGGED,           // Messages may have been missed
    ERROR             // Error occurred
}

// Received message content
data class MessageContent(
    val content: ByteArray,         // Message payload
    val deliveredFrom: String       // Delivering peer's NodeId
)
```

## References

- [Iroh Gossip Documentation](https://iroh.computer/docs/gossip)
- [Proposal 005: Iroh Implementation Plan](./005-iroh-implementation-plan.md)
- [Proposal 006: Doc-Native Sync](./006-doc-native-sync.md)
- [iroh-docs sync issues](https://github.com/n0-computer/iroh-docs/issues/51)
