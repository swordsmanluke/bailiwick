# Proposal 004: Threaded Conversations

**Status**: Draft
**Author**: Architecture Review
**Date**: 2026-01-05
**Priority**: Medium

## Executive Summary

Enable threaded conversations (replies) in Bailiwick. The database model already supports threading via `Post.parent`, but the UI and query infrastructure are not implemented. This proposal outlines how to build a complete threading system on the existing foundation.

## Current State

### Existing Support

**Database Model** (`Post.kt`):
```kotlin
@Entity
data class Post(
    val authorId: Long,
    val cid: ContentId?,
    val timestamp: Long,
    val parent: ContentId?,  // ← Already exists!
    val text: String,
    var signature: String
)
```

**Post Model** (`Post.kt`):
```kotlin
data class Post(
    val timestamp: Long,
    val parent_cid: ContentId?,  // ← Already exists!
    val text: String,
    val files: List<FileDef>,
    val signature: String
)
```

**Signing includes parent** (`Post.kt`):
```kotlin
fun signatureContent(): String {
    return "$authorId|$timestamp|$parent|$text"  // Parent included
}
```

### What's Missing

1. **No UI for replies**: Can't compose or view replies
2. **No thread queries**: Can't fetch conversation chains
3. **No reply counts**: Can't show "5 replies" on posts
4. **No mention support**: Can't reference @users
5. **No notifications**: Can't alert users of replies
6. **No interaction support**: Likes/reactions not implemented

## Threading Model Design

### Flat vs. Nested Threading

**Option A: Flat Threading (like Twitter/X)**
```
Post A
├─ Reply 1 (to A)
├─ Reply 2 (to A)
├─ Reply 3 (to A)
└─ Reply 4 (to A)
```
- Simpler UI
- All replies at same level
- Harder to follow sub-conversations

**Option B: Nested Threading (like Reddit)**
```
Post A
├─ Reply 1 (to A)
│  ├─ Reply 1.1 (to Reply 1)
│  └─ Reply 1.2 (to Reply 1)
├─ Reply 2 (to A)
└─ Reply 3 (to A)
   └─ Reply 3.1 (to Reply 3)
```
- Richer conversations
- Complex UI (indentation, collapse)
- Can get deeply nested

**Option C: Hybrid (like Slack)**
- Main timeline is flat
- Clicking opens thread view
- Thread view can be nested

**Recommendation**: Start with **Flat Threading**, add nesting later if needed.

## Data Model Extensions

### Thread Statistics

Track reply counts without N+1 queries:

```kotlin
@Entity
data class ThreadStats(
    @PrimaryKey val postCid: ContentId,
    val replyCount: Int,
    val lastReplyAt: Long,
    val participantCount: Int
)

@Dao
interface ThreadStatsDao {
    @Query("SELECT * FROM threadstats WHERE postCid = :cid")
    fun getStats(cid: ContentId): ThreadStats?

    @Query("""
        UPDATE threadstats
        SET replyCount = replyCount + 1,
            lastReplyAt = :timestamp
        WHERE postCid = :parentCid
    """)
    fun incrementReplyCount(parentCid: ContentId, timestamp: Long)
}
```

### Post with Thread Context

```kotlin
data class PostWithThread(
    @Embedded val post: Post,
    @Relation(parentColumn = "authorId", entityColumn = "id")
    val author: Identity,
    val replyCount: Int,
    val isReply: Boolean get() = post.parent != null
)
```

## Query Design

### Fetch Thread (All Replies to a Post)

```kotlin
@Dao
interface PostDao {
    // Get all direct replies to a post
    @Query("""
        SELECT * FROM post
        WHERE parent = :parentCid
        ORDER BY timestamp ASC
    """)
    fun getReplies(parentCid: ContentId): List<Post>

    // Get reply count for a post
    @Query("""
        SELECT COUNT(*) FROM post
        WHERE parent = :parentCid
    """)
    fun getReplyCount(parentCid: ContentId): Int

    // Get full thread (recursive - for nested view)
    @Query("""
        WITH RECURSIVE thread AS (
            SELECT *, 0 as depth FROM post WHERE cid = :rootCid
            UNION ALL
            SELECT p.*, t.depth + 1 FROM post p
            INNER JOIN thread t ON p.parent = t.cid
            WHERE t.depth < :maxDepth
        )
        SELECT * FROM thread ORDER BY depth, timestamp
    """)
    fun getThreadRecursive(rootCid: ContentId, maxDepth: Int = 10): List<Post>

    // Get ancestor chain (for "in reply to" display)
    @Query("""
        WITH RECURSIVE ancestors AS (
            SELECT * FROM post WHERE cid = :cid
            UNION ALL
            SELECT p.* FROM post p
            INNER JOIN ancestors a ON a.parent = p.cid
        )
        SELECT * FROM ancestors ORDER BY timestamp ASC
    """)
    fun getAncestors(cid: ContentId): List<Post>
}
```

### Feed with Reply Context

Enhance feed query to include reply info:

```kotlin
@Query("""
    SELECT p.*,
           (SELECT COUNT(*) FROM post r WHERE r.parent = p.cid) as replyCount,
           (SELECT MAX(timestamp) FROM post r WHERE r.parent = p.cid) as lastReplyAt
    FROM post p
    JOIN circlepost cp ON p.id = cp.postId
    JOIN circle c ON cp.circleId = c.id
    WHERE c.id IN (:circleIds)
    AND p.parent IS NULL  -- Only top-level posts
    ORDER BY p.timestamp DESC
""")
fun getFeedWithReplyCounts(circleIds: List<Long>): List<PostWithReplies>
```

## UI Design

### Thread View Layout

```xml
<!-- fragment_thread.xml -->
<LinearLayout
    android:orientation="vertical">

    <!-- Original post (expanded) -->
    <include layout="@layout/item_post_expanded" />

    <!-- Divider -->
    <View style="@style/Divider" />

    <!-- Reply composer -->
    <include layout="@layout/compose_reply" />

    <!-- Replies list -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/repliesList"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

### Reply Item Layout

```xml
<!-- item_reply.xml -->
<LinearLayout
    android:orientation="horizontal"
    android:paddingStart="@{depth * 16dp}">  <!-- Indent for nesting -->

    <!-- Vertical line connector -->
    <View
        android:layout_width="2dp"
        android:layout_height="match_parent"
        android:background="@color/thread_line" />

    <LinearLayout android:orientation="vertical">
        <!-- Author + timestamp -->
        <TextView android:id="@+id/authorName" />
        <TextView android:id="@+id/timestamp" />

        <!-- Reply content -->
        <TextView android:id="@+id/replyText" />

        <!-- Action buttons -->
        <LinearLayout android:orientation="horizontal">
            <ImageButton android:id="@+id/btnReply" />
            <TextView android:id="@+id/replyCount" />
        </LinearLayout>
    </LinearLayout>

</LinearLayout>
```

### Feed Post with Reply Preview

```xml
<!-- Add to item_post.xml -->
<LinearLayout
    android:id="@+id/replyPreview"
    android:visibility="gone">

    <TextView
        android:id="@+id/replyCountText"
        android:text="@{String.format('%d replies', replyCount)}" />

    <TextView
        android:id="@+id/lastReplyPreview"
        android:text="@{lastReplyText}"
        android:ellipsize="end"
        android:maxLines="1" />

</LinearLayout>
```

## Navigation Flow

```
Feed (top-level posts)
    │
    ▼ (tap post)
Thread View
    │
    ├─ Original post (expanded)
    ├─ Reply composer
    └─ Replies list
           │
           ▼ (tap reply with replies)
        Nested Thread View
```

### Navigation Graph Extension

```xml
<!-- Add to navigation.xml -->
<fragment
    android:id="@+id/threadFragment"
    android:name="com.perfectlunacy.bailiwick.fragments.ThreadFragment">
    <argument
        android:name="postCid"
        app:argType="string" />
</fragment>

<action
    android:id="@+id/action_content_to_thread"
    app:destination="@id/threadFragment" />
```

## Reply Composition

### Reply Composer Fragment

```kotlin
class ReplyComposerFragment : Fragment() {
    private lateinit var binding: FragmentReplyComposerBinding
    private lateinit var parentPost: Post

    fun setParentPost(post: Post) {
        parentPost = post
        binding.replyingTo.text = "Replying to ${post.authorName}"
    }

    private fun submitReply() {
        val replyText = binding.replyInput.text.toString()
        if (replyText.isBlank()) return

        val reply = Post(
            authorId = currentIdentity.id,
            cid = null,  // Assigned after Iroh upload
            timestamp = System.currentTimeMillis(),
            parent = parentPost.cid,  // Link to parent
            text = replyText,
            signature = ""  // Computed later
        )

        viewModel.createPost(reply)
        dismiss()
    }
}
```

### Sign Reply with Parent

Ensure signature includes parent reference (already implemented):

```kotlin
// Post.kt
fun signatureContent(): String {
    return "$authorId|$timestamp|$parent|$text"
}
```

## Syncing Replies

### Publishing Replies

Replies are just posts with a non-null `parent`. No changes needed to `PostPublisher`:

```kotlin
// PostPublisher.kt - already works
fun publish(post: Post, cipher: Encryptor): ContentId? {
    val postData = PostData(
        timestamp = post.timestamp,
        parent_cid = post.parent,  // Already included
        text = post.text,
        files = getFileDefs(post),
        signature = post.signature
    )
    return postData.upload(cipher, iroh)
}
```

### Downloading Thread Context

When downloading a post, also fetch its parent chain for context:

```kotlin
// PostDownloader.kt enhancement
fun download(cid: ContentId, identityId: Long, cipher: Encryptor) {
    val postData = Deserializer.fromCid(cipher, iroh, cid, PostData::class.java)

    // ... existing post save logic ...

    // If this is a reply, ensure we have the parent
    postData.parent_cid?.let { parentCid ->
        if (db.postDao().findByCid(parentCid) == null) {
            // Try to download parent (may be from different user)
            downloadParentChain(parentCid, cipher)
        }
    }
}

private fun downloadParentChain(cid: ContentId, cipher: Encryptor, depth: Int = 0) {
    if (depth > 5) return  // Limit recursion

    val postData = Deserializer.fromCid(cipher, iroh, cid, PostData::class.java)
    // ... save post ...

    postData.parent_cid?.let { parentCid ->
        if (db.postDao().findByCid(parentCid) == null) {
            downloadParentChain(parentCid, cipher, depth + 1)
        }
    }
}
```

## Interactions (Likes/Reactions)

### Interaction Entity

The model exists but database entity is missing:

```kotlin
// New: models/db/Interaction.kt
enum class InteractionType { Reaction, Tag }

@Entity(
    indices = [
        Index(value = ["targetCid", "authorId", "type"], unique = true)
    ]
)
data class Interaction(
    val targetCid: ContentId,      // Post or other interaction
    val authorId: Long,
    val type: InteractionType,
    val content: String,           // Emoji for reaction, text for tag
    val timestamp: Long,
    var signature: String,
    var cid: ContentId? = null
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface InteractionDao {
    @Query("""
        SELECT * FROM interaction
        WHERE targetCid = :targetCid
        AND type = 'Reaction'
    """)
    fun getReactions(targetCid: ContentId): List<Interaction>

    @Query("""
        SELECT content, COUNT(*) as count
        FROM interaction
        WHERE targetCid = :targetCid
        AND type = 'Reaction'
        GROUP BY content
    """)
    fun getReactionCounts(targetCid: ContentId): List<ReactionCount>
}

data class ReactionCount(val content: String, val count: Int)
```

### Database Registration

```kotlin
@Database(
    entities = [
        // ... existing entities ...
        Interaction::class,
        ThreadStats::class
    ],
    version = 9
)
abstract class BailiwickDatabase : RoomDatabase() {
    // ... existing DAOs ...
    abstract fun interactionDao(): InteractionDao
    abstract fun threadStatsDao(): ThreadStatsDao
}
```

### Reaction UI

```kotlin
// Add to PostAdapter or ThreadAdapter
private fun showReactionPicker(post: Post) {
    val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "😡")

    MaterialAlertDialogBuilder(context)
        .setTitle("React")
        .setItems(reactions.toTypedArray()) { _, which ->
            addReaction(post, reactions[which])
        }
        .show()
}

private fun addReaction(post: Post, emoji: String) {
    val interaction = Interaction(
        targetCid = post.cid!!,
        authorId = currentIdentity.id,
        type = InteractionType.Reaction,
        content = emoji,
        timestamp = System.currentTimeMillis(),
        signature = ""
    )
    viewModel.createInteraction(interaction)
}
```

## Mention Support

### @Mention Detection

```kotlin
object MentionParser {
    private val MENTION_PATTERN = Regex("@([\\w]+)")

    fun extractMentions(text: String): List<String> {
        return MENTION_PATTERN.findAll(text).map { it.groupValues[1] }.toList()
    }

    fun formatWithMentions(text: String): SpannableString {
        val spannable = SpannableString(text)
        MENTION_PATTERN.findAll(text).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(Color.BLUE),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }
}
```

### Mention Autocomplete

```kotlin
class MentionAdapter(
    private val users: List<User>
) : RecyclerView.Adapter<MentionAdapter.ViewHolder>() {

    fun filter(query: String): List<User> {
        return users.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }
}

// In compose fragment
binding.replyInput.addTextChangedListener { text ->
    val lastWord = text?.toString()?.split(" ")?.lastOrNull() ?: ""
    if (lastWord.startsWith("@") && lastWord.length > 1) {
        showMentionSuggestions(lastWord.substring(1))
    } else {
        hideMentionSuggestions()
    }
}
```

## Implementation Phases

### Phase 1: Basic Threading (2 weeks)

1. Add thread view fragment
2. Implement reply queries
3. Create reply composer
4. Add navigation from feed to thread

### Phase 2: Reply Counts (1 week)

1. Add ThreadStats entity
2. Update feed queries
3. Show reply count in feed

### Phase 3: Nested View (1-2 weeks)

1. Implement recursive thread query
2. Add indentation to reply items
3. Add collapse/expand controls

### Phase 4: Interactions (2 weeks)

1. Add Interaction entity
2. Implement reaction picker
3. Show reaction counts
4. Sync interactions

### Phase 5: Mentions (1 week)

1. Add mention parser
2. Implement autocomplete
3. Linkify mentions in display
4. (Future) Notification on mention

## Database Migration

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add Interaction table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS Interaction (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                targetCid TEXT NOT NULL,
                authorId INTEGER NOT NULL,
                type TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                signature TEXT NOT NULL,
                cid TEXT
            )
        """)
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_Interaction_targetCid_authorId_type
            ON Interaction (targetCid, authorId, type)
        """)

        // Add ThreadStats table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS ThreadStats (
                postCid TEXT PRIMARY KEY NOT NULL,
                replyCount INTEGER NOT NULL DEFAULT 0,
                lastReplyAt INTEGER NOT NULL DEFAULT 0,
                participantCount INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}
```

## Success Criteria

1. **Reply to post**: User can reply from thread view
2. **View thread**: All replies display correctly
3. **Reply counts**: Accurate counts in feed
4. **Nested display**: Proper indentation for nested replies
5. **Reactions**: Can add/view emoji reactions
6. **Performance**: Thread with 100 replies loads < 1 second

## Security Considerations

### Reply Authenticity

Replies are signed including parent CID:
```kotlin
fun signatureContent(): String {
    return "$authorId|$timestamp|$parent|$text"
}
```

This prevents:
- Moving replies to different parent posts
- Modifying reply content after posting

### Cross-User Replies

Replies may reference posts from different users. Handle missing parent gracefully:

```kotlin
// In thread view
val parent = db.postDao().findByCid(post.parent)
if (parent == null) {
    binding.parentPreview.text = "[Original post not available]"
}
```

## Future Enhancements

1. **Quote Replies**: Embed original post content
2. **Branching**: Show where conversation splits
3. **Notifications**: Alert on replies to your posts
4. **Muting**: Hide threads you're not interested in
5. **Bookmarks**: Save conversations for later
