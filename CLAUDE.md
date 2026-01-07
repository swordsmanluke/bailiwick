# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Bailiwick is an Android decentralized social network built on Iroh (a modern peer-to-peer toolkit). It's a pull-oriented, privacy-focused platform where users subscribe to content rather than having content pushed to them.

**Note**: The project is transitioning from IPFS to Iroh. Some legacy IPFS code remains but is being phased out.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.perfectlunacy.bailiwick.ExampleUnitTest"

# Run instrumentation tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### Layer Overview
```
UI (Fragments) → ViewModel → BailiwickNetwork → Database (Room) / Iroh
```

The app uses MVVM with a repository pattern:
- **Fragments** (`fragments/`): UI layer with navigation via AndroidX Navigation
- **ViewModel** (`viewmodels/BailiwickViewModel.kt`): Presentation logic
- **BailiwickNetwork** (`storage/BailiwickNetwork.kt`): Repository interface abstracting data access
- **BailiwickDatabase** (`storage/db/`): Room ORM with entities
- **Iroh** (`storage/iroh/`): Decentralized storage via Iroh blobs and docs

### Key Components

**Entry Point**: `BailiwickActivity.kt` initializes Iroh, database, and ViewModel

**Service Locator**: `Bailiwick.kt` singleton provides global access to DB, Iroh, cache directory

**Background Sync**:
- `IrohService.kt`: Foreground service for background synchronization
- `ContentPublisher.kt`: Publishes identity, posts, and feeds to Iroh
- `ContentDownloader.kt`: Downloads content from peer Docs

**Cryptography** (`ciphers/`, `signatures/`):
- Ed25519 keys managed by Iroh for node identity
- AES for symmetric encryption of posts
- Posts are encrypted on-device; only key holders can decrypt

### Iroh Architecture

Iroh replaces IPFS with three main concepts:

1. **Blobs**: Content-addressed storage using BLAKE3 hashes
   - Store and retrieve arbitrary data by hash
   - Collections group named blobs (like directories)

2. **Docs**: Mutable key-value stores that sync between peers
   - Each user has a primary Doc for publishing content
   - Peers subscribe to each other's Docs to receive updates
   - Replaces IPNS for mutable naming

3. **Networking**: QUIC-based peer-to-peer with relay fallback
   - NAT traversal built-in
   - Automatic relay when direct connection fails

### Data Model

Room database entities in `models/db/`:
- `Account`: Local user account
- `Identity`: User profiles (name, profile picture)
- `Post`, `PostFile`: Social posts with attachments
- `Circle`, `CircleMember`, `CirclePost`: Groups/circles for content sharing
- `Key`: Encryption keys for circles
- `PeerDoc`: Maps peer NodeIds to their Doc namespaces
- `Action`: Pending actions (introductions, invites)

Iroh models in `models/iroh/`:
- `IrohIdentity`: User profile for network serialization
- `IrohPost`: Post content (encrypted)
- `IrohFeed`: List of post hashes in a circle
- `IrohCircle`: Circle definition with members
- `IrohAction`: Network actions

### Type Aliases

Defined in `storage/Types.kt`:
- `BlobHash`: BLAKE3 hash (32 bytes, hex-encoded)
- `NodeId`: Ed25519 public key identifying a peer
- `DocNamespaceId`: Identifies a mutable document
- `AuthorId`: Identifies who wrote to a document

## Technology Stack

- **Language**: Kotlin 1.6.0
- **Build**: Gradle 7.2
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 31 (Android 12)
- **Database**: Room 2.4.0-beta01
- **P2P**: Iroh via iroh-ffi (Rust with Kotlin bindings)
- **QR Codes**: ZXing for generation and scanning
- **Crypto**: Bouncy Castle, HKDF

## Iroh Integration

### Building iroh-ffi for Android

See `designs/proposals/005-iroh-implementation-plan.md` for detailed instructions.

Summary:
1. Clone [iroh-ffi](https://github.com/n0-computer/iroh-ffi)
2. Configure Cargo for Android NDK
3. Build for ARM64, ARMv7, x86_64
4. Place AAR in `app/libs/iroh.aar`

### IrohWrapper

The `IrohWrapper` class (`storage/iroh/IrohWrapper.kt`) provides the bridge between Kotlin and Iroh:

```kotlin
// Initialize
val iroh = IrohWrapper.create(context)

// Store content
val hash = iroh.storeBlob(data)

// Retrieve content
val data = iroh.getBlob(hash)

// Mutable naming via Docs
iroh.getMyDoc().set("identity", hashBytes)
```

Currently uses a placeholder implementation until iroh-ffi is built.

## Project Status

Version 0.3, actively in development.

**Completed**:
- Iroh data model and interfaces
- ContentPublisher and ContentDownloader
- IrohService for background sync
- Database schema for Iroh

**In Progress**:
- Building iroh-ffi for Android
- Removing legacy IPFS code

**Planned**:
- Media handling (images/video)
- Threaded conversations

See `designs/proposals/` for detailed design documents.
