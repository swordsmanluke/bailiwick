# Bailiwick Architecture Proposals Index

**Last Updated**: 2026-01-14

This directory contains design proposals addressing major architectural issues in Bailiwick, a decentralized social network built on Iroh.

## Proposals

| # | Title | Priority | Status | Summary |
|---|-------|----------|--------|---------|
| [001](001-ipfs-replacement.md) | IPFS/IPNS Replacement | Critical | Superseded | Replace outdated lite-debug.aar with Iroh (see 007) |
| [002](002-sync-bug-fix.md) | Sync Bug Fix | Critical | Superseded | Fix bug where clients miss updates (see 007) |
| [003](003-media-handling.md) | Media Handling | High | Draft | Enable images and video with battery-efficient transcoding |
| [004](004-threaded-conversations.md) | Threaded Conversations | Medium | Draft | Implement replies and conversation threading |
| [005](005-iroh-implementation-plan.md) | Iroh Implementation Plan | Critical | Superseded | Step-by-step plan to replace IPFS with Iroh (see 007) |
| [006](006-doc-native-sync.md) | Doc-Native Sync | Critical | Superseded | Event-driven sync with Iroh Docs (see 007) |
| [007](007-gossip-manifest-sync.md) | Gossip Manifest Sync | Critical | **Active** | Replace Docs with Gossip-based manifest announcements |

---

## 001: IPFS/IPNS Replacement with Iroh

**Priority**: Critical
**Effort**: 6-8 weeks (reduced due to existing bindings)

### Problem

The current IPFS implementation relies on `lite-debug.aar`, an outdated and unmaintained library. No native JVM IPFS implementation exists.

### Key Discovery

**[iroh-ffi](https://github.com/n0-computer/iroh-ffi) already provides production Kotlin bindings!**
- 41% of the repo is Kotlin code
- Uses Mozilla's UniFFI for binding generation
- Actively maintained by the n0 team
- Supports Android via NDK

### Iroh vs IPFS

| Feature | IPFS (current) | Iroh |
|---------|----------------|------|
| Content hash | SHA-256 (Multihash) | BLAKE3 (faster) |
| Mutable names | IPNS (DHT) | Docs (sync protocol) |
| Directories | UnixFS DAG | Collections |
| Transport | libp2p | QUIC (native) |

### Implementation Path

1. Build iroh-ffi AAR for Android targets
2. Create `IrohWrapper` implementing existing `IPFS` interface
3. Map blobs → content storage, collections → directories, docs → IPNS
4. Migrate content (hashes will change)
5. Migrate identity (RSA → Ed25519)

### Breaking Changes

- Iroh nodes cannot communicate with IPFS nodes
- All Bailiwick users must upgrade together
- Content hashes change format (SHA-256 → BLAKE3)

---

## 002: Synchronization Bug Fix

**Priority**: Critical
**Effort**: 2-3 weeks

### Problem

Clients incorrectly believe they're up-to-date with friends' content. Root cause: download success is not tracked, but sequences are marked "up to date" regardless of outcome.

### Primary Issue

```kotlin
// DownloadRunner.kt:54
if(downloadRequired(peerId, curSequence) || true) {  // || true disables check!
```

The `|| true` was added because the underlying success tracking doesn't work. The comment acknowledges this is a FIXME.

### Bug Flow

1. Download partially fails (network timeout)
2. Sequence marked as "up to date" anyway
3. New content published
4. IPNS resolves new sequence but `upToDate` flag not cleared
5. Future downloads skipped
6. User never sees new content

### Fix Strategy

1. **Return success status** from download methods
2. **Only mark up-to-date on success**
3. **Clear upToDate flag** when new sequence discovered
4. **Remove the `|| true` hack** once proper tracking works

### Database Addition

New `DownloadedContent` entity to track what was actually downloaded by CID.

---

## 003: Media Handling (Images & Video)

**Priority**: High
**Effort**: 6-8 weeks

### Problem

Image rendering is disabled (`imgSocialContent.visibility = View.GONE`), and there's no video support. The data model supports files but the UI doesn't render them.

### Image Support

- PostFile entity already stores CID + MIME type
- Enable rendering with Picasso
- Add gallery view for multiple images
- Enhance metadata (dimensions, thumbnails, blurHash)

### Video Support (Critical: Battery Efficiency)

Software video encoding drains battery rapidly (30-50% for 5-minute video). **Hardware encoding via MediaCodec is essential**.

**Recommended Approach**:
- Use [Transcoder](https://github.com/deepmedia/Transcoder) library (MediaCodec-based)
- Target 720p @ 4-5 Mbps H.264
- Run transcoding in foreground service
- Generate thumbnails at upload
- Use ExoPlayer for playback

### Transcoding Specs

| Parameter | Value |
|-----------|-------|
| Resolution | 720p max |
| Video Codec | H.264 High |
| Video Bitrate | 4-5 Mbps |
| Audio Codec | AAC @ 128 kbps |
| Container | MP4 |

### Implementation Phases

1. Enable image display (1-2 weeks)
2. Image upload pipeline (1 week)
3. Video playback with ExoPlayer (2 weeks)
4. Hardware-accelerated transcoding (2-3 weeks)
5. Enhanced metadata (1 week)

---

## 004: Threaded Conversations

**Priority**: Medium
**Effort**: 6-8 weeks

### Problem

The data model supports threading (`Post.parent` field exists), but there's no UI for viewing or composing replies.

### Current State

```kotlin
// Already in Post.kt
val parent: ContentId?  // Links to parent post's CID
```

### Missing Components

- Thread view UI
- Reply composer
- Reply count queries
- Interaction support (likes/reactions)
- Mention support (@username)

### Threading Model

**Recommended**: Start with **flat threading** (like Twitter), add nesting later.

```
Post A
├─ Reply 1
├─ Reply 2
└─ Reply 3
```

### New Database Entities

- `Interaction` - For reactions and tags (model exists, DB entity missing)
- `ThreadStats` - Cached reply counts for feed display

### Implementation Phases

1. Basic threading UI (2 weeks)
2. Reply counts in feed (1 week)
3. Nested view with indentation (1-2 weeks)
4. Reactions/interactions (2 weeks)
5. @Mention support (1 week)

---

## Implementation Priority

### Immediate (Active)

1. **007 - Gossip Manifest Sync**: Replace Docs with Gossip-based manifest announcements
   - ~3-4 weeks estimated
   - Fixes sync reliability issues by removing Docs entirely
   - Uses Gossip for real-time update notifications
   - Hierarchical manifest: user manifest → circle manifests
   - Clean slate - no migration needed

### After Gossip Migration

2. **003 - Media Handling (Images first)**
   - Image display is low-hanging fruit
   - Video transcoding follows

3. **004 - Threaded Conversations**
   - Builds on existing data model
   - Significant UX improvement

---

## Dependencies

```
007 Gossip Manifest Sync (ACTIVE)
    ↓ (complete first - replaces 001, 002, 005, 006)
003 Media Handling
    ↓ (file infrastructure stabilized)
004 Threaded Conversations
```

**Note**: Proposal 007 supersedes proposals 001, 002, 005, and 006. It replaces the unreliable Docs sync with Gossip-based manifest announcements while retaining Blobs for content storage.

---

## Database Schema

**Reset to Version 1** (no existing users to migrate)

| Proposal | Schema Version | Changes |
|----------|----------------|---------|
| 005 | v1 (fresh) | New schema: `PeerDoc`, simplified `Post`, `PostFile`, etc. |
| 003 | v2 | Add media metadata columns to `PostFile` |
| 004 | v3 | Add `Interaction`, `ThreadStats` tables |

---

## Risk Summary

| Proposal | Risk Level | Primary Risk |
|----------|------------|--------------|
| 001 | High | CID/key incompatibility during migration |
| 002 | Low | Well-understood bug with clear fix |
| 003 | Medium | Video transcoding performance on low-end devices |
| 004 | Low | Builds on existing model, incremental changes |

---

## Open Questions

1. ~~**IPFS Replacement**: Is full IPFS compatibility required?~~ **RESOLVED**: Using Iroh (not IPFS-compatible, but better suited for mobile)
2. **Video Length Limits**: Should there be a maximum video duration?
3. **Threading Depth**: How deep should nested threads go?
4. **Reaction Types**: Which emoji/reactions to support?
5. **Offline Support**: How to handle reply composition when offline?
6. **Migration Timeline**: When to require all users upgrade to Iroh?

---

## Contributing

When adding new proposals:

1. Create `NNN-<short-name>.md` following the template
2. Add entry to this index
3. Update dependency graph if needed
4. Include effort estimate and priority
