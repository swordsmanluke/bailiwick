# Proposal 002: Synchronization Bug Fix

**Status**: Draft
**Author**: Architecture Review
**Date**: 2026-01-05
**Priority**: Critical

## Executive Summary

Clients incorrectly believe they are up-to-date with friends' content when they are not. This causes missed updates and stale feeds. The root cause is a combination of disabled safety checks, improper sequence tracking, and missing error propagation.

## Bug Description

### Symptoms
- Users don't see new posts from friends they're subscribed to
- App reports "up to date" when content is actually stale
- Manual refresh doesn't fix the issue
- Problem persists across app restarts

### Root Cause

**Primary Issue**: Line 54 of `DownloadRunner.kt`
```kotlin
if(downloadRequired(peerId, curSequence) || true) {  // || true disables the check!
```

The `|| true` was added as a workaround because the download success tracking doesn't work correctly. The comment explains:
```kotlin
// FIXME: The downloadRequired logic works fine... but we
//        don't really know if download succeeded when
//        downloadManifest returned. Need to send a signal
//        back up the stack.
```

### Why This Causes Problems

```
Timeline of Bug:

1. Client downloads peer X at sequence 10
2. Download partially fails (network timeout, missing content)
3. Code marks sequence 10 as "up to date" anyway
4. Peer X publishes new content (sequence 11)
5. Client resolves IPNS → discovers sequence 11
6. resolveName() updates Sequence row to 11
7. BUT: upToDate flag remains TRUE from step 3
8. downloadRequired() returns false (11 is marked up-to-date)
9. Client skips download, never gets new content
```

## Detailed Analysis

### Issue 1: Early Mark as Up-To-Date

**Location**: `DownloadRunner.kt:54-60`

```kotlin
if(downloadRequired(peerId, curSequence) || true) {
    downloadIdentity(peerId)
    downloadManifest(peerId)
    Log.i(TAG, "Marking sequence $curSequence as up to date")
    db.sequenceDao().setUpToDate(peerId, curSequence)  // ← Happens regardless of success
}
```

**Problem**: The sequence is marked up-to-date before downloads complete, and regardless of whether they succeed.

### Issue 2: No Error Propagation

**Location**: `DownloadRunner.kt:112-139`

```kotlin
private fun downloadManifest(peerId: PeerId) {
    val maniPair = IpfsDeserializer.fromBailiwickFile(...)

    if (maniPair == null) {
        Log.w(TAG, "Failed to locate Manifest for peer $peerId")
        return  // ← Silent failure, caller doesn't know
    }
    // ...
}
```

**Problem**: `downloadManifest()` returns `Unit`, not a success/failure indicator. The caller (`iteration()`) has no way to know if the download actually worked.

### Issue 3: upToDate Flag Not Cleared

**Location**: `IPFSWrapper.kt:109-142`

```kotlin
override fun resolveName(peerId: PeerId, sequenceDao: SequenceDao, timeoutSeconds: Long): IPNSRecord? {
    // ...
    if(rec.sequence > seq.sequence) {
        Log.i(TAG, "Updating $peerId sequence to ${rec.sequence}")
        sequenceDao.updateSequence(peerId, rec.sequence)  // ← Updates sequence...
        // But doesn't clear upToDate flag!
    }
    // ...
}
```

**Problem**: When a new sequence is discovered, the sequence number updates but `upToDate` remains true from the previous download.

### Issue 4: Parallel Tracking Systems

The codebase has TWO systems for tracking sync state:

1. **Sequence table**: Used by `DownloadRunner`
   - `upToDateSequence(peerId)` query
   - Single flag per peer

2. **IpnsCache table**: Used by `PublishRunner` only
   - Path-to-CID mappings
   - Never consulted during downloads

This creates confusion about what's actually been downloaded.

## Proposed Fix

### Fix 1: Return Success Status from Downloads

Change download methods to return success/failure:

```kotlin
// Before
private fun downloadManifest(peerId: PeerId) { ... }

// After
private fun downloadManifest(peerId: PeerId): DownloadResult {
    val maniPair = IpfsDeserializer.fromBailiwickFile(...)
    if (maniPair == null) {
        Log.w(TAG, "Failed to locate Manifest for peer $peerId")
        return DownloadResult.Failed("Manifest not found")
    }

    var feedsSucceeded = 0
    var feedsFailed = 0

    manifest.feeds.forEach { feedCid ->
        when (val result = feedDownloader.download(feedCid, peerId, cipher)) {
            is DownloadResult.Success -> feedsSucceeded++
            is DownloadResult.Failed -> feedsFailed++
        }
    }

    return if (feedsFailed == 0) {
        DownloadResult.Success
    } else {
        DownloadResult.Partial(feedsSucceeded, feedsFailed)
    }
}

sealed class DownloadResult {
    object Success : DownloadResult()
    data class Partial(val succeeded: Int, val failed: Int) : DownloadResult()
    data class Failed(val reason: String) : DownloadResult()
}
```

### Fix 2: Only Mark Up-To-Date on Success

```kotlin
// Before
if(downloadRequired(peerId, curSequence) || true) {
    downloadIdentity(peerId)
    downloadManifest(peerId)
    db.sequenceDao().setUpToDate(peerId, curSequence)
}

// After
if (downloadRequired(peerId, curSequence)) {
    val identityResult = downloadIdentity(peerId)
    val manifestResult = downloadManifest(peerId)

    if (identityResult is DownloadResult.Success &&
        manifestResult is DownloadResult.Success) {
        Log.i(TAG, "Marking sequence $curSequence as up to date")
        db.sequenceDao().setUpToDate(peerId, curSequence)
    } else {
        Log.w(TAG, "Download incomplete for $peerId at sequence $curSequence")
        // Don't mark as up-to-date, will retry next cycle
    }
}
```

### Fix 3: Clear upToDate When New Sequence Discovered

```kotlin
// In IPFSWrapper.resolveName()
if (rec.sequence > seq.sequence) {
    Log.i(TAG, "Updating $peerId sequence to ${rec.sequence}")
    sequenceDao.updateSequence(peerId, rec.sequence)
    sequenceDao.clearUpToDate(peerId)  // ← ADD THIS
}
```

Add DAO method:
```kotlin
// In Sequence.kt
@Query("UPDATE sequence SET upToDate = 0 WHERE peerId = :peerId")
fun clearUpToDate(peerId: PeerId)
```

### Fix 4: Remove the || true Hack

Once fixes 1-3 are in place:
```kotlin
// Remove the hack
if (downloadRequired(peerId, curSequence)) {  // No more || true
```

### Fix 5: Track Downloaded Content CIDs

Add content hash verification:

```kotlin
@Entity
data class DownloadedContent(
    @PrimaryKey val cid: ContentId,
    val peerId: PeerId,
    val sequence: Long,
    val downloadedAt: Long,
    val contentType: String  // "manifest", "feed", "post", "file"
)

@Dao
interface DownloadedContentDao {
    @Query("SELECT EXISTS(SELECT 1 FROM downloadedcontent WHERE cid = :cid)")
    fun hasDownloaded(cid: ContentId): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun markDownloaded(content: DownloadedContent)
}
```

Use in downloaders:
```kotlin
fun download(cid: ContentId, peerId: PeerId, cipher: Encryptor): DownloadResult {
    if (db.downloadedContentDao().hasDownloaded(cid)) {
        return DownloadResult.Success  // Already have it
    }

    // ... do download ...

    db.downloadedContentDao().markDownloaded(
        DownloadedContent(cid, peerId, sequence, System.currentTimeMillis(), "post")
    )
    return DownloadResult.Success
}
```

## Database Migration

### New Entity

```kotlin
@Entity(
    indices = [
        Index(value = ["peerId", "sequence"]),
        Index(value = ["contentType"])
    ]
)
data class DownloadedContent(
    @PrimaryKey val cid: ContentId,
    val peerId: PeerId,
    val sequence: Long,
    val downloadedAt: Long,
    val contentType: String
)
```

### Migration

```kotlin
@Database(
    entities = [..., DownloadedContent::class],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 8, to = 9)
    ]
)
```

## Testing Strategy

### Unit Tests

```kotlin
@Test
fun `downloadRequired returns true when sequence increases`() {
    // Setup: sequence 5 marked as up-to-date
    sequenceDao.insert(Sequence("peer1", 5, true))

    // Simulate new sequence discovered
    sequenceDao.updateSequence("peer1", 6)
    sequenceDao.clearUpToDate("peer1")

    // Should require download
    assertTrue(downloadRunner.downloadRequired("peer1", 6))
}

@Test
fun `failed download does not mark as up-to-date`() {
    // Setup
    val peerId = "peer1"
    every { ipfs.getData(any(), any()) } throws TimeoutException()

    // Execute
    downloadRunner.iteration(peerId, 5)

    // Verify not marked as up-to-date
    assertNull(sequenceDao.upToDateSequence(peerId))
}
```

### Integration Tests

```kotlin
@Test
fun `sync recovers after partial failure`() {
    // First download: network fails partway
    mockNetworkFailureAfter(2, posts)
    downloadRunner.run()
    assertFalse(sequenceDao.find("peer1")!!.upToDate)

    // Second download: network works
    mockNetworkSuccess()
    downloadRunner.run()
    assertTrue(sequenceDao.find("peer1")!!.upToDate)
    assertEquals(posts.size, postDao.all().size)
}
```

## Implementation Sequence

### Phase 1: Add Download Results (Low Risk)

1. Create `DownloadResult` sealed class
2. Change `downloadManifest` return type
3. Change `downloadIdentity` return type
4. Update `FeedDownloader.download()` return type
5. Update `PostDownloader.download()` return type

### Phase 2: Fix Up-To-Date Logic (Medium Risk)

1. Add `clearUpToDate()` DAO method
2. Update `IPFSWrapper.resolveName()` to clear flag
3. Update `iteration()` to check results before marking

### Phase 3: Remove Workaround (Final Step)

1. Remove `|| true` from condition
2. Run comprehensive tests
3. Monitor for issues in development builds

### Phase 4: Add Content Tracking (Optional Enhancement)

1. Add `DownloadedContent` entity
2. Add database migration
3. Update downloaders to use content tracking
4. Add skip logic for already-downloaded content

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing sync | Feature flag to enable new logic gradually |
| Database migration issues | Comprehensive backup before migration |
| Performance regression | Benchmark download cycles before/after |
| Edge cases in error handling | Extensive unit test coverage |

## Success Criteria

1. **No missed updates**: All published content eventually appears
2. **Correct state tracking**: `upToDate` accurately reflects download status
3. **Recovery from failures**: Partial downloads retry on next cycle
4. **No regression**: Existing functionality continues to work

## Appendix: Current Code References

- `DownloadRunner.kt`: Lines 30-165
- `FeedDownloader.kt`: Lines 26-43
- `PostDownloader.kt`: Lines 20-56
- `FileDownloader.kt`: Lines 27-44
- `Sequence.kt`: Lines 1-41
- `IPFSWrapper.kt`: Lines 109-142

## Related Issues

- Commit "Disable download required logic" suggests awareness of this issue
- Commit "Only download new updates" attempted to fix but introduced `|| true`
- FIXME comment in code acknowledges the problem
