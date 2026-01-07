# Proposal 003: Media Handling (Images & Video)

**Status**: Draft
**Author**: Architecture Review
**Date**: 2026-01-05
**Priority**: High

## Executive Summary

Enable display of images and video in posts. The current implementation has image rendering disabled and no video support. This proposal addresses image rendering, video playback, and critically, battery-efficient video transcoding for upload.

## Current State

### Existing Infrastructure

**What Works:**
- `PostFile` entity stores file CID + MIME type
- `IpfsFileDef` in IPFS model supports file references
- Files download to `bwcache/` directory
- Encryption/decryption pipeline works for files

**What's Disabled:**
```kotlin
// PostAdapter.kt lines 62-66
// TODO: Examine Files and add images if necessary
val img_content = binding.imgSocialContent
img_content.visibility = View.GONE  // Always hidden!
```

**What's Missing:**
- Image rendering logic
- Video player integration
- Thumbnail generation
- Media metadata (dimensions, duration)
- Transcoding pipeline

### Current Data Model

```kotlin
// IpfsPost.kt
data class IpfsFileDef(val mimeType: String, val cid: ContentId)

// PostFile.kt
@Entity
data class PostFile(
    val postId: Long,
    val fileCid: ContentId,
    val mimeType: String
)
```

## Image Support

### Phase 1: Enable Image Display

**Changes to PostAdapter.kt:**

```kotlin
class PostAdapter : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postData[position]
        val binding = holder.binding

        // ... existing code ...

        // Image handling
        val imageFiles = post.files.filter { it.mimeType.startsWith("image/") }
        if (imageFiles.isNotEmpty()) {
            binding.imgSocialContent.visibility = View.VISIBLE
            loadImage(imageFiles.first(), binding.imgSocialContent)
        } else {
            binding.imgSocialContent.visibility = View.GONE
        }
    }

    private fun loadImage(file: PostFile, imageView: ImageView) {
        val cachePath = File(cacheDir, "bwcache/${file.fileCid}")
        if (cachePath.exists()) {
            Picasso.get()
                .load(cachePath)
                .placeholder(R.drawable.loading_placeholder)
                .error(R.drawable.image_error)
                .fit()
                .centerCrop()
                .into(imageView)
        }
    }
}
```

### Phase 2: Image Gallery for Multiple Images

For posts with multiple images, implement a gallery view:

```kotlin
// Add to fragment_post_item.xml
<androidx.viewpager2.widget.ViewPager2
    android:id="@+id/imageGallery"
    android:layout_width="match_parent"
    android:layout_height="200dp"
    android:visibility="gone" />

<com.google.android.material.tabs.TabLayout
    android:id="@+id/imageIndicator"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
```

### Phase 3: Image Metadata Enhancement

Extend the data model for better image handling:

```kotlin
// Enhanced IpfsFileDef
data class IpfsFileDef(
    val mimeType: String,
    val cid: ContentId,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailCid: ContentId? = null,
    val blurHash: String? = null,  // Placeholder blur while loading
    val sizeBytes: Long? = null
)
```

## Video Support

### Critical Consideration: Battery and Performance

Video transcoding on mobile is CPU-intensive. Using software encoders (FFmpeg pure software) can:
- Drain battery rapidly (30-50% for a 5-minute video)
- Cause thermal throttling
- Take 10-20x realtime to encode
- Make the phone unusable during encoding

**Hardware encoding is essential** for acceptable user experience.

### Transcoding Strategy

#### Recommended Approach: Hardware-Accelerated Encoding

Use Android's MediaCodec API for hardware-accelerated transcoding:

| Approach | CPU Usage | Battery Impact | Encoding Speed | Codec Support |
|----------|-----------|----------------|----------------|---------------|
| FFmpeg (software) | 100%+ | Very High | 0.1-0.5x realtime | Excellent |
| MediaCodec (hardware) | 10-20% | Low | 1-4x realtime | Limited but sufficient |
| **Hybrid (recommended)** | 15-30% | Moderate | 0.5-2x realtime | Good |

#### Recommended Library: Transcoder by deepmedia

[Transcoder](https://github.com/deepmedia/Transcoder) provides:
- Hardware-accelerated transcoding via MediaCodec
- Cropping, concatenation, clipping
- Audio processing
- Video speed adjustment
- Clean Kotlin API

**Gradle dependency:**
```gradle
implementation 'com.otaliastudios:transcoder:0.10.5'
```

**Usage example:**
```kotlin
Transcoder.into(outputPath)
    .addDataSource(inputUri)
    .setVideoTrackStrategy(DefaultVideoStrategy.atMost(720).build())
    .setAudioTrackStrategy(DefaultAudioStrategy.builder().build())
    .setListener(object : TranscoderListener {
        override fun onTranscodeProgress(progress: Double) {
            updateProgressBar(progress)
        }
        override fun onTranscodeCompleted(successCode: Int) {
            uploadToIpfs(outputPath)
        }
        override fun onTranscodeCanceled() { }
        override fun onTranscodeFailed(exception: Throwable) {
            showError(exception)
        }
    })
    .transcode()
```

#### Alternative: LiTr (LinkedIn's Transcoder)

[LiTr](https://github.com/nicest/LiTr) is LinkedIn's open-source transcoder:
- Lightweight
- Surface-based (faster than ByteBuffer)
- OpenGL frame modification support
- Well-documented

### Video Upload Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                     VIDEO UPLOAD PIPELINE                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User selects video                                          │
│     ↓                                                            │
│  2. Check if transcoding needed                                 │
│     • Resolution > 720p?                                        │
│     • Bitrate > 5 Mbps?                                         │
│     • Codec not H.264?                                          │
│     • Duration > 5 minutes?                                     │
│     ↓                                                            │
│  3. If needed: Transcode with MediaCodec                        │
│     • Target: 720p, H.264 High, 4-5 Mbps                        │
│     • Show progress (non-blocking UI)                           │
│     • Allow cancellation                                        │
│     ↓                                                            │
│  4. Generate thumbnail                                          │
│     • Extract frame at 1 second                                 │
│     • Resize to 320x180                                         │
│     • Generate blurHash for placeholder                         │
│     ↓                                                            │
│  5. Encrypt video file                                          │
│     ↓                                                            │
│  6. Upload to IPFS                                              │
│     • Chunk large files                                         │
│     • Show upload progress                                      │
│     ↓                                                            │
│  7. Store CID in PostFile                                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Video Playback

**Recommended: ExoPlayer**

ExoPlayer is the standard for Android video playback:

```gradle
implementation 'com.google.android.exoplayer:exoplayer-core:2.18.1'
implementation 'com.google.android.exoplayer:exoplayer-ui:2.18.1'
```

**Integration:**

```kotlin
class VideoPlayerFragment : Fragment() {
    private var player: ExoPlayer? = null

    fun playVideo(cid: ContentId, cipher: Encryptor) {
        val cachePath = File(cacheDir, "bwcache/$cid")

        // Decrypt to temp file for playback
        val decryptedPath = decryptForPlayback(cachePath, cipher)

        player = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.fromUri(Uri.fromFile(decryptedPath))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}
```

### Streaming vs. Download-First

**Option A: Download-First (Current Architecture)**
- Download entire file before playback
- Simple implementation
- Poor UX for large videos (long wait)

**Option B: Streaming with Decryption (Complex)**
- Requires custom DataSource for ExoPlayer
- Decrypt chunks on-demand
- Better UX but significant complexity

**Recommendation**: Start with download-first, add streaming later if needed.

### Video Thumbnail Generation

```kotlin
object ThumbnailGenerator {
    fun generateThumbnail(videoPath: String, outputPath: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val bitmap = retriever.getFrameAtTime(
                1_000_000,  // 1 second in microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            bitmap?.let {
                val scaled = Bitmap.createScaledBitmap(it, 320, 180, true)
                FileOutputStream(outputPath).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                true
            } ?: false
        } finally {
            retriever.release()
        }
    }
}
```

## Data Model Extensions

### Enhanced PostFile

```kotlin
@Entity(indices = [Index(value = ["postId", "fileCid"], unique = true)])
data class PostFile(
    val postId: Long,
    val fileCid: ContentId,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,      // For video/audio
    val thumbnailCid: ContentId? = null,
    val blurHash: String? = null,
    val sizeBytes: Long? = null
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}
```

### Enhanced IPFS Model

```kotlin
data class IpfsFileDef(
    val mimeType: String,
    val cid: ContentId,
    val meta: MediaMetadata? = null
)

data class MediaMetadata(
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val thumbnailCid: ContentId? = null,
    val blurHash: String? = null,
    val sizeBytes: Long? = null
)
```

### Database Migration

```kotlin
// Migration from version 8 to 9
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE PostFile ADD COLUMN width INTEGER DEFAULT NULL
        """)
        database.execSQL("""
            ALTER TABLE PostFile ADD COLUMN height INTEGER DEFAULT NULL
        """)
        database.execSQL("""
            ALTER TABLE PostFile ADD COLUMN durationMs INTEGER DEFAULT NULL
        """)
        database.execSQL("""
            ALTER TABLE PostFile ADD COLUMN thumbnailCid TEXT DEFAULT NULL
        """)
        database.execSQL("""
            ALTER TABLE PostFile ADD COLUMN blurHash TEXT DEFAULT NULL
        """)
        database.execSQL("""
            ALTER TABLE PostFile ADD COLUMN sizeBytes INTEGER DEFAULT NULL
        """)
    }
}
```

## Transcoding Settings

### Recommended Output Specifications

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Resolution | 720p max | Good quality, reasonable size |
| Video Codec | H.264 High | Universal device support |
| Video Bitrate | 4-5 Mbps | Quality/size balance |
| Audio Codec | AAC | Universal support |
| Audio Bitrate | 128 kbps | Sufficient for voice/music |
| Container | MP4 | Universal playback |
| Frame Rate | Original or 30fps | Preserve original when possible |

### Size Estimation

| Duration | 720p @ 4Mbps | 1080p @ 8Mbps |
|----------|--------------|---------------|
| 30 sec | ~15 MB | ~30 MB |
| 1 min | ~30 MB | ~60 MB |
| 5 min | ~150 MB | ~300 MB |
| 10 min | ~300 MB | ~600 MB |

### User Experience Guidelines

1. **Show estimated size** before upload
2. **Allow quality selection** (Low/Medium/High)
3. **Warn on large files** (>100MB)
4. **Show transcoding progress** with cancel option
5. **Run transcoding in foreground service** to prevent interruption
6. **Notify when complete** if backgrounded

## Implementation Phases

### Phase 1: Basic Image Display (1-2 weeks)

1. Enable `imgSocialContent` in PostAdapter
2. Add MIME type checking
3. Load images from cache with Picasso
4. Handle missing images gracefully

### Phase 2: Image Upload (1 week)

1. Add image picker to post composition
2. Resize large images (max 2048px)
3. Encrypt and upload to IPFS
4. Store in PostFile

### Phase 3: Video Playback (2 weeks)

1. Add ExoPlayer dependency
2. Create video player view
3. Handle download-then-play flow
4. Add playback controls

### Phase 4: Video Transcoding (2-3 weeks)

1. Add Transcoder library
2. Implement transcoding pipeline
3. Add progress UI
4. Run as foreground service
5. Generate thumbnails

### Phase 5: Enhanced Metadata (1 week)

1. Database migration for new fields
2. Extract metadata during upload
3. Generate blurHash
4. Update IPFS model

## Battery Considerations

### Transcoding Service

```kotlin
class TranscodingService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Acquire partial wake lock to prevent CPU throttling
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Bailiwick::Transcoding"
        )
        wakeLock.acquire(30 * 60 * 1000L)  // Max 30 minutes

        startTranscoding(intent)
        return START_NOT_STICKY
    }
}
```

### Battery Optimization Tips

1. **Use hardware encoding** (MediaCodec) exclusively
2. **Cap resolution** at 720p to reduce encoding work
3. **Show battery warning** for videos > 5 minutes
4. **Queue transcoding** when charging if possible
5. **Allow WiFi-only upload** option

## Security Considerations

### Encrypted Playback

Videos must be decrypted for playback. Options:

1. **Decrypt to temp file** (current approach for files)
   - Simple
   - Leaves decrypted file temporarily on disk

2. **Memory-mapped decryption**
   - More secure
   - Higher memory usage
   - Complex ExoPlayer DataSource

3. **Encrypted streaming** (future enhancement)
   - Decrypt chunks on-demand
   - Requires custom crypto DataSource

**Recommendation**: Start with temp file, clean up after playback.

```kotlin
override fun onStop() {
    super.onStop()
    // Clean up decrypted temp files
    tempDecryptedFile?.delete()
}
```

## Success Criteria

1. **Images display** in feed within 500ms of visible
2. **Videos play** without buffering for cached content
3. **Transcoding completes** within 2x realtime for hardware
4. **Battery drain** < 5% per minute of transcoding
5. **Memory usage** stays under 200MB during playback

## References

- [Transcoder Library](https://github.com/deepmedia/Transcoder)
- [LiTr (LinkedIn)](https://engineering.linkedin.com/blog/2019/litr-a-lightweight-video-audio-transcoder-for-android)
- [ExoPlayer Documentation](https://exoplayer.dev/)
- [MediaCodec Best Practices](https://developer.android.com/media/optimize/sharing)
- [Android Video Sharing Guide](https://developer.android.com/media/optimize/sharing)
- [BlurHash Algorithm](https://blurha.sh/)
