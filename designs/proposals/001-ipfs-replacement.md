# Proposal 001: IPFS/IPNS Replacement with Iroh

**Status**: Draft
**Author**: Architecture Review
**Date**: 2026-01-05
**Updated**: 2026-01-05
**Priority**: Critical

## Executive Summary

Replace the outdated `lite-debug.aar` IPFS library with [Iroh](https://github.com/n0-computer/iroh), using the official [iroh-ffi](https://github.com/n0-computer/iroh-ffi) Kotlin bindings. This is a well-supported path: iroh-ffi already provides production Kotlin bindings via UniFFI (41% of the repo is Kotlin code).

## Key Discovery: iroh-ffi Already Exists

The n0 team maintains official FFI bindings for Iroh:
- **Repository**: [github.com/n0-computer/iroh-ffi](https://github.com/n0-computer/iroh-ffi)
- **Languages**: Kotlin, Swift, Python, JavaScript
- **Technology**: UniFFI (Mozilla's multi-language binding generator)
- **Maturity**: Active development, Kotlin is 41% of codebase

This dramatically reduces implementation effort from "build FFI bindings" to "integrate existing bindings."

## Iroh Architecture Overview

Iroh has evolved beyond IPFS into a modular peer-to-peer toolkit:

```
┌─────────────────────────────────────────────────────────────┐
│                         Iroh Stack                           │
├─────────────────────────────────────────────────────────────┤
│  iroh-docs          │  Key-value stores with sync           │
│  (Replaces IPNS)    │  Eventually consistent, multi-writer  │
├─────────────────────┴───────────────────────────────────────┤
│  iroh-blobs                                                  │
│  Content-addressed storage (BLAKE3 hashes)                   │
│  Verified streaming, collections (like directories)          │
├─────────────────────────────────────────────────────────────┤
│  iroh (networking)                                           │
│  QUIC connections, NAT traversal, relay fallback            │
│  Endpoint discovery, peer-to-peer                            │
└─────────────────────────────────────────────────────────────┘
```

### Key Differences from IPFS

| Feature | IPFS (current) | Iroh |
|---------|----------------|------|
| Content hash | Multihash (SHA-256) | BLAKE3 (faster, 32 bytes) |
| Mutable names | IPNS (DHT-based) | Docs (sync protocol) |
| Directories | UnixFS DAG | Collections (HashSeq) |
| Discovery | DHT | DNS + Relay servers |
| Transport | libp2p | QUIC (native) |

## iroh-ffi Kotlin API

Based on the [source code](https://github.com/n0-computer/iroh-ffi/blob/main/src/blob.rs), here's what's available:

### Node Creation

```kotlin
// Persistent storage (recommended for mobile)
val node = Iroh.persistent("/data/data/com.perfectlunacy.bailiwick/iroh")

// Or with options
val options = NodeOptions(
    gcIntervalMillis = 300_000,  // 5 min GC
    enableDocs = true,
    // secretKey = existingKey  // For identity preservation
)
val node = Iroh.persistentWithOptions(path, options)
```

### Blobs API (Content Storage)

```kotlin
val blobs = node.blobs()

// Store content
val outcome = blobs.addBytes(encryptedData)
val hash: Hash = outcome.hash  // BLAKE3 hash (like CID)

// Retrieve content
val data: ByteArray = blobs.readToBytes(hash)

// Check existence
val exists: Boolean = blobs.has(hash)

// Partial reads (for large files)
val chunk = blobs.readAtToBytes(hash, offset = 0, len = ReadAtLen.Exact(1024))
```

### Collections (Directory-like structures)

```kotlin
// Create a collection (like IPFS directory)
val collection = Collection()
collection.push("manifest.json", manifestHash)
collection.push("identity.json", identityHash)

val result = blobs.createCollection(collection, SetTagOption.Auto, listOf())
val rootHash = result.hash  // Points to the collection
```

### Docs API (Replaces IPNS)

```kotlin
val docs = node.docs()

// Create a document (like IPNS name)
val doc = docs.create()
val namespaceId = doc.id()  // Stable identifier

// Set content (mutable!)
val author = node.authors().default()
doc.setBytes(author, key = "latest".toByteArray(), value = rootHash.toBytes())

// Sync with peers
doc.subscribe(callback)
doc.startSync(peers)
```

### Hash Type

```kotlin
// From bytes
val hash = Hash.fromBytes(byteArray)

// From string (hex or base32)
val hash = Hash.fromString("bafk...")

// To string
val hexString = hash.toHex()
```

## Mapping Current API to Iroh

### Current Interface

```kotlin
interface IPFS : IpfsReader, IpfsWriter

interface IpfsReader {
    val peerID: PeerId
    val publicKey: PublicKey
    val privateKey: PrivateKey
    fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray
    fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): List<Link>?
    fun resolveName(peerId: PeerId, sequenceDao: SequenceDao, timeoutSeconds: Long): IPNSRecord?
    fun resolveNode(link: String, timeoutSeconds: Long): ContentId?
    fun resolveNode(root: ContentId, path: String, timeoutSeconds: Long): ContentId?
    fun resolveBailiwickFile(rootCid: ContentId, filename: String, timeoutSeconds: Long): ContentId?
}

interface IpfsWriter {
    fun bootstrap(context: Context)
    fun storeData(data: ByteArray): ContentId
    fun createEmptyDir(): ContentId?
    fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId?
    fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long)
    fun provide(cid: ContentId, timeoutSeconds: Long)
    fun isConnected(): Boolean
}
```

### Iroh Implementation

```kotlin
class IrohWrapper(private val node: Iroh) : IPFS {

    private val blobs = node.blobs()
    private val docs = node.docs()
    private val endpoint = node.endpoint()

    // Identity
    override val peerID: PeerId
        get() = endpoint.nodeId().toString()

    override val publicKey: PublicKey
        get() = endpoint.secretKey().public().toJavaPublicKey()

    override val privateKey: PrivateKey
        get() = endpoint.secretKey().toJavaPrivateKey()

    // Content operations
    override fun getData(cid: ContentId, timeoutSeconds: Long): ByteArray {
        val hash = Hash.fromString(cid)
        return blobs.readToBytes(hash)
    }

    override fun storeData(data: ByteArray): ContentId {
        val outcome = blobs.addBytes(data)
        return outcome.hash.toHex()
    }

    // Directory operations via Collections
    override fun createEmptyDir(): ContentId? {
        val collection = Collection()
        val result = blobs.createCollection(collection, SetTagOption.Auto, listOf())
        return result.hash.toHex()
    }

    override fun addLinkToDir(dirCid: ContentId, name: String, cid: ContentId): ContentId? {
        // Get existing collection
        val collection = blobs.getCollection(Hash.fromString(dirCid))

        // Add new entry
        collection.push(name, Hash.fromString(cid))

        // Create new collection (immutable, returns new hash)
        val result = blobs.createCollection(collection, SetTagOption.Auto, listOf())
        return result.hash.toHex()
    }

    override fun getLinks(cid: ContentId, resolveChildren: Boolean, timeoutSeconds: Long): List<Link>? {
        return try {
            val collection = blobs.getCollection(Hash.fromString(cid))
            collection.blobs().map { (name, hash) ->
                Link(name, hash.toHex(), LinkType.File)  // Collections don't distinguish file/dir
            }
        } catch (e: Exception) {
            null
        }
    }

    // IPNS replacement via Docs
    override fun resolveName(peerId: PeerId, sequenceDao: SequenceDao, timeoutSeconds: Long): IPNSRecord? {
        // Use a Doc keyed by peerId for mutable naming
        val docId = getOrCreateDocForPeer(peerId)
        val doc = docs.open(docId)

        // Get latest entry
        val entry = doc.getExact(authorId, key = "latest".toByteArray())
        if (entry == null) return null

        val hash = Hash.fromBytes(entry.contentBytes(blobs))
        return IPNSRecord(
            retrievedAt = System.currentTimeMillis(),
            hash = hash.toHex(),
            sequence = entry.timestamp()  // Docs use timestamps, not sequence numbers
        )
    }

    override fun publishName(root: ContentId, sequence: Long, timeoutSeconds: Long) {
        val author = node.authors().default()
        val doc = getOrCreateMyDoc()

        // Set the latest content hash
        doc.setBytes(author, "latest".toByteArray(), Hash.fromString(root).toBytes())
    }

    // Network
    override fun bootstrap(context: Context) {
        // Iroh handles this automatically with relay servers
        // Optional: configure custom relays or discovery
    }

    override fun isConnected(): Boolean {
        return endpoint.connectionInfo()?.let { it.latency != null } ?: false
    }

    override fun provide(cid: ContentId, timeoutSeconds: Long) {
        // Iroh provides automatically when content is added
        // Tags control what gets retained vs garbage collected
        blobs.setTag(SetTagOption.Named("provide-$cid"), Hash.fromString(cid))
    }

    // Path resolution
    override fun resolveBailiwickFile(rootCid: ContentId, filename: String, timeoutSeconds: Long): ContentId? {
        val collection = blobs.getCollection(Hash.fromString(rootCid))
        val bwHash = collection.blobs().find { it.first == "bw" }?.second ?: return null

        val bwCollection = blobs.getCollection(bwHash)
        val verHash = bwCollection.blobs().find { it.first == Bailiwick.VERSION }?.second ?: return null

        val verCollection = blobs.getCollection(verHash)
        return verCollection.blobs().find { it.first == filename }?.second?.toHex()
    }
}
```

## Build Integration

### Directory Structure

```
app/
├── build.gradle.kts
├── libs/
│   └── iroh-android.aar          # Built from iroh-ffi
└── src/main/
    ├── jniLibs/
    │   ├── arm64-v8a/
    │   │   └── libiroh_ffi.so
    │   ├── armeabi-v7a/
    │   │   └── libiroh_ffi.so
    │   └── x86_64/
    │       └── libiroh_ffi.so
    └── java/.../storage/ipfs/
        └── IrohWrapper.kt
```

### Gradle Configuration

```kotlin
// app/build.gradle.kts
android {
    // ...
    ndkVersion = "25.2.9519653"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    implementation(files("libs/iroh-android.aar"))
    // Or if published to Maven:
    // implementation("computer.iroh:iroh-kotlin:0.x.x")
}
```

### Building iroh-ffi for Android

```bash
# Clone iroh-ffi
git clone https://github.com/n0-computer/iroh-ffi
cd iroh-ffi

# Install Android targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# Configure cargo for Android NDK (in ~/.cargo/config.toml)
# See README.kotlin.md for linker configuration

# Build
./make_kotlin.sh

# Output: kotlin/iroh/build/outputs/aar/iroh-release.aar
```

## Migration Strategy

### Phase 1: Parallel Implementation (Week 1-2)

1. Add iroh-ffi AAR to project
2. Create `IrohWrapper` implementing existing `IPFS` interface
3. Add feature flag to switch between implementations
4. Test basic operations (store, retrieve)

### Phase 2: Naming Migration (Week 3-4)

The biggest change is IPNS → Docs:

**Current (IPNS)**:
- PeerId resolves to latest CID via DHT
- Sequence numbers for versioning

**New (Docs)**:
- Each user has a Doc (namespace)
- Doc syncs between peers who subscribe
- Entries have timestamps instead of sequences

**Migration approach**:
```kotlin
// Store mapping: old PeerId → new Doc namespace
data class PeerDocMapping(
    val peerId: String,
    val docNamespaceId: String,
    val migratedAt: Long
)
```

### Phase 3: Content Migration (Week 5-6)

1. Export content list from old IPFS store
2. Re-add to Iroh (hashes will change: SHA-256 → BLAKE3)
3. Update all CID references in database
4. Publish new root via Docs

### Phase 4: Identity Preservation (Week 7)

Iroh uses Ed25519 keys; current system uses RSA:

**Option A**: Derive Ed25519 from RSA seed
```kotlin
// Use RSA private key bytes as seed for Ed25519
val seed = MessageDigest.getInstance("SHA-256")
    .digest(rsaPrivateKey.encoded)
val secretKey = SecretKey.fromBytes(seed)
```

**Option B**: Maintain both keys during transition
- Old RSA for existing peers
- New Ed25519 for Iroh identity
- Gradually migrate peers to new identity

### Phase 5: Remove Legacy (Week 8)

1. Remove `lite-debug.aar`
2. Remove old `IPFSWrapper`
3. Update CLAUDE.md
4. Clean up feature flags

## Compatibility Considerations

### CID/Hash Format Change

| Before | After |
|--------|-------|
| `QmXxx...` (CIDv0) | `bafk...` (BLAKE3 hex) |
| `bafy...` (CIDv1) | 32-byte hash |

All stored CIDs in database need migration or dual-format support.

### IPNS → Docs Conceptual Shift

| IPNS | Docs |
|------|------|
| DHT-based resolution | Direct peer sync |
| Sequence numbers | Timestamps |
| Single value per name | Key-value namespace |
| Global namespace | Per-document namespace |

Docs is more powerful but requires different thinking:
- Each user's "feed" is a Doc
- Subscribers sync that Doc
- Updates propagate via gossip, not DHT lookup

### Network Compatibility

Iroh nodes **cannot** communicate with IPFS nodes directly. This means:
- Bailiwick users must all upgrade
- Or run a bridge (complex, not recommended)
- Clean break is simplest

## Testing Strategy

### Unit Tests

```kotlin
@Test
fun `store and retrieve content`() {
    val node = Iroh.memory()
    val wrapper = IrohWrapper(node)

    val data = "Hello, Iroh!".toByteArray()
    val cid = wrapper.storeData(data)

    val retrieved = wrapper.getData(cid, 10)
    assertEquals(data.toList(), retrieved.toList())
}

@Test
fun `collection operations`() {
    val wrapper = IrohWrapper(Iroh.memory())

    val dirCid = wrapper.createEmptyDir()!!
    val fileCid = wrapper.storeData("content".toByteArray())

    val newDirCid = wrapper.addLinkToDir(dirCid, "file.txt", fileCid)!!

    val links = wrapper.getLinks(newDirCid, false, 10)!!
    assertEquals(1, links.size)
    assertEquals("file.txt", links[0].name)
}
```

### Integration Tests

```kotlin
@Test
fun `publish and resolve name`() {
    val node1 = Iroh.memory()
    val node2 = Iroh.memory()

    // Publish from node1
    val content = wrapper1.storeData("feed content".toByteArray())
    wrapper1.publishName(content, 1, 30)

    // Resolve from node2 (after sync)
    val record = wrapper2.resolveName(node1.peerID, sequenceDao, 30)
    assertEquals(content, record?.hash)
}
```

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Hash format breaks existing content | High | High | Database migration script |
| Docs sync slower than IPNS | Medium | Medium | Benchmark, tune gossip |
| iroh-ffi API changes | Low | Medium | Pin to specific version |
| Android-specific bugs | Low | High | Test on multiple devices |
| Key migration issues | Medium | High | Support both key types during transition |

## Success Criteria

1. **All existing tests pass** with new implementation
2. **Content storage/retrieval** works identically
3. **Mutable naming** (Docs) resolves within 5 seconds
4. **No increase** in battery/memory usage
5. **Clean migration** of existing user data

## Appendix: BLAKE3 vs SHA-256

| Property | SHA-256 (IPFS) | BLAKE3 (Iroh) |
|----------|----------------|---------------|
| Output size | 32 bytes | 32 bytes |
| Speed | ~500 MB/s | ~2000 MB/s |
| Parallelism | None | Built-in |
| Security | 128-bit | 128-bit |

BLAKE3 is faster and equally secure. The only downside is incompatibility with existing IPFS content hashes.

## References

- [Iroh Documentation](https://www.iroh.computer/docs)
- [iroh-ffi Repository](https://github.com/n0-computer/iroh-ffi)
- [iroh-blobs API](https://docs.rs/iroh-blobs/latest/iroh_blobs/)
- [iroh-docs API](https://docs.rs/iroh-docs/latest/iroh_docs/)
- [UniFFI Documentation](https://mozilla.github.io/uniffi-rs/)
- [Kotlin README](https://github.com/n0-computer/iroh-ffi/blob/main/README.kotlin.md)
