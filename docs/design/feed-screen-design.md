# Feed Screen UI Design Document

## Overview

The Feed Screen (ContentFragment) is the main view of the Bailiwick application, providing users with a scrollable feed of posts from their connections. This document describes the current UI layout, interaction patterns, and design specifications.

## Screen Structure

```
+--------------------------------------------------+
|  [Logo]  Peer ID (truncated)                      |  <- Header
+--------------------------------------------------+
|  Filter: [All] [Circle1] [Circle2] ...            |  <- Circle Filter Bar
+--------------------------------------------------+
|  [Avatar1] [Avatar2] [Avatar3] ...  [+]           |  <- User Filter Bar
+--------------------------------------------------+
|  [MyAvatar]  What's on your mind?                 |  <- Post Composer
|              [Photos preview...]                  |
|              [Camera] [Tag]           [Post]      |
+--------------------------------------------------+
|                                                   |
|  [Post 1]                                         |  <- Post List
|  [Post 2]                                         |
|  [Post 3]                                         |
|  ...                                              |
+--------------------------------------------------+
```

## Component Details

### 1. Header Bar

**Location:** Top of screen
**Height:** `wrap_content` with `spacing_sm` padding
**Background:** `colorSurface` (#ffffff)
**Elevation:** `elevation_low` (2dp)

**Elements:**
- **Refresh Button (Logo):** 48x48dp (`avatar_md`), shows Bailiwick icon
  - Tap: Refreshes feed content
  - Long-press: Force re-downloads all images (hidden sync feature)
- **Peer ID Text:** Truncated with ellipsize middle, `TextAppearance.Bailiwick.Caption`

### 2. Circle Filter Bar

**Location:** Below header
**Height:** 48dp (`filter_bar_height`)
**Background:** `colorSurface`

**Layout:** Horizontal LinearLayout
- **Label:** "Filter:" text with `colorTextHint`
- **Circle Chips:** Horizontal RecyclerView
  - Chip height: 32dp (`filter_chip_height`)
  - Chip background: `bg_filter_chip` drawable
  - Chip margin: `spacing_sm` (8dp) end
  - Selected state: Visual highlight via adapter

**Interaction:**
- Tap circle chip: Filter posts to selected circle
- Tap "All": Show all posts (clear filter)
- Selection persists via `FilterPreferences`

### 3. User Filter Bar

**Location:** Below circle filter
**Height:** `wrap_content` with `spacing_sm` vertical padding
**Background:** `colorSurface`

**Elements:**
- **User Avatars:** Horizontal RecyclerView
  - Avatar size: 48x48dp (`avatar_md`)
  - Horizontal margin: 2dp
  - Items show user profile photos
- **Add Connection Button:** 48x48dp (`touch_target_min`)
  - Icon: `ic_plus_circle_outline`
  - Tint: `colorPrimary`

**Interaction:**
- Tap avatar: Filter posts by that user
- Tap "+": Navigate to connection screen

### 4. Post Composer

**Location:** Below user filter
**Background:** `colorSurface`
**Padding:** `spacing_sm` (8dp)

**Elements:**
- **User Avatar:** 40x40dp (`post_avatar_size`) with circular clip
- **Text Input:** Multi-line EditText, max 4 lines
  - Hint: "What's on your mind?"
  - Background: Transparent
  - Style: `TextAppearance.Bailiwick.Body1`
- **Mention Suggestions:** RecyclerView (hidden by default)
  - Max height: 150dp
  - Elevation: `elevation_medium` (4dp)
  - Appears when typing @ followed by text
- **Photo Previews:** Horizontal RecyclerView (hidden when empty)
  - Shows thumbnails of selected photos
  - Tap to remove photo
- **Tag Input:** EditText (hidden by default)
  - Style: `TextAppearance.Bailiwick.Caption`
  - Color: `tagText` (#1565c0)
- **Action Buttons:** Horizontal layout, gravity end
  - Camera button: 48x48dp
  - Tag button: 48x48dp (hidden)
  - Post button: Primary style, 32dp height

**Interaction:**
- Tap camera: Open photo picker
- Type @username: Show mention suggestions
- Tap suggestion: Insert mention
- Tap Post: Submit post and clear composer

### 5. Post List

**Location:** Below composer, fills remaining space
**Type:** ListView
**Background:** `colorBackground` (#fafafa)
**Divider:** Transparent with `spacing_sm` height

## Post Item Layout

```
+--------------------------------------------------+
|  [Avatar] Author Name              [Delete?]      |
|  12/20/2021 5:01PM                                |
+--------------------------------------------------+
|  [Single Image or Photo Grid]                     |
+--------------------------------------------------+
|  Post text content goes here...                   |
+--------------------------------------------------+
|  [Reaction1] [Reaction2] ...                      |
+--------------------------------------------------+
|  [Emote]     [Comment]     [Tag]                  |
+--------------------------------------------------+
```

### Post Header
- **Avatar:** 50x50dp
- **Author Name:** 24sp, black text, weight 1
- **Delete Button:** 48x48dp, visible only for own posts
- **Timestamp:** Uses `PostFormatter.formatTime()`

### Post Content
- **Single Image Container:** FrameLayout
  - Image: Full width, 300dp height, centerCrop
  - Loading indicator: Centered ProgressBar
- **Photo Grid:** RecyclerView (for multiple photos)
  - Grid item: 150dp height
  - Shows +N overlay for additional photos
- **Text:** 18sp, black, horizontal padding 10dp

### Reactions Row
- **RecyclerView:** Horizontal, hidden when empty
- **Reaction Chip:**
  - Background: `bg_reaction_chip`
  - Emoji: 16sp
  - Count: Caption style, `colorTextSecondary`
  - Padding: 8dp horizontal, 4dp vertical

### Interaction Footer
- **Layout:** Horizontal button bar
- **Buttons:** Equal weight, `buttonBarButtonStyle`
  - Emote: Opens emoji picker
  - Comment: Navigate to comments screen
  - Tag: Tag functionality

## Interaction Flows

### 1. Filtering by Circle
```
User taps circle chip
  -> CircleFilterAdapter.onCircleSelected()
  -> ContentFragment.filterByCircle()
  -> FilterPreferences.setCircleFilter()
  -> applyFilters() updates adapter
```

### 2. Filtering by User
```
User taps user avatar
  -> UserButtonAdapter.onUserClick()
  -> ContentFragment.filterPostsByUser()
  -> FilterPreferences.setPersonFilter()
  -> applyFilters() updates adapter
```

### 3. Creating a Post
```
User enters text and/or selects photos
  -> Photo previews update via PhotoPreviewAdapter
User taps Post button
  -> submitPost() validates content
  -> Photos compressed and stored as blobs
  -> Post created with signature
  -> Published to Iroh blob storage
  -> GossipService notifies peers
  -> UI refreshed
```

### 4. Adding Mentions
```
User types "@" in post text
  -> TextWatcher detects @ prefix
  -> MentionParser.getMentionContext() extracts prefix
  -> MentionParser.getAutocompleteSuggestions() filters users
  -> MentionSuggestionsAdapter shows matching users
User taps suggestion
  -> insertMention() replaces partial text
  -> Suggestions hidden
```

### 5. Adding Reactions
```
User taps Emote button on post
  -> Emoji picker dialog shown
User selects emoji
  -> PostAdapter.onReactionAdded()
  -> ContentFragment.addReaction()
  -> Reaction saved to database
  -> Adapter refreshed to show reaction
```

### 6. Viewing Photos
```
User taps photo in post
  -> PostAdapter.onPhotoClick()
  -> ContentFragment.navigateToPhotoViewer()
  -> PhotoViewerFragment opens with swipeable gallery
```

### 7. Viewing Comments
```
User taps Comment button
  -> PostAdapter.onCommentClick()
  -> ContentFragment.navigateToComments()
  -> CommentsFragment opens with threaded comments
```

### 8. Deleting Posts
```
User taps delete button on own post
  -> Confirmation dialog shown
User confirms
  -> performDeletePost()
  -> Files deleted from cache
  -> PostFiles deleted from DB
  -> Post deleted from DB
  -> Delete action created for gossip
  -> Adapter updated
```

## Design Tokens

### Spacing
| Token | Value | Usage |
|-------|-------|-------|
| `spacing_xs` | 4dp | Small gaps |
| `spacing_sm` | 8dp | Standard padding |
| `spacing_md` | 16dp | Section padding |
| `spacing_lg` | 24dp | Large gaps |

### Colors
| Token | Value | Usage |
|-------|-------|-------|
| `colorPrimary` | #ffc04a | Primary actions, highlights |
| `colorSurface` | #ffffff | Card/container backgrounds |
| `colorBackground` | #fafafa | Page background |
| `colorTextPrimary` | #212121 | Main text |
| `colorTextSecondary` | #757575 | Secondary text |
| `colorTextHint` | #9e9e9e | Placeholder text |

### Typography
| Style | Usage |
|-------|-------|
| `Body1` | Post composer input |
| `Body2` | Post content, comments |
| `Caption` | Timestamps, hints, secondary info |

### Touch Targets
- Minimum: 48x48dp (`touch_target_min`)
- All interactive elements meet accessibility guidelines

## State Management

### Filter State
- Persisted via `FilterPreferences` (SharedPreferences)
- Modes: NONE, CIRCLE, PERSON
- Restored in `onCreateView()`

### Post List
- Observed via `bwModel.postsLive` LiveData
- Sorted by timestamp (descending)
- Filtered by `applyFilters()` method

### Photo Selection
- Maintained in `selectedPhotos` mutableList
- Cleared after successful post submission

## Navigation

| Action | Destination |
|--------|-------------|
| Tap "+" button | ConnectFragment |
| Tap author avatar | UserProfileFragment |
| Tap own avatar | UserProfileFragment (self) |
| Tap Comment button | CommentsFragment |
| Tap photo | PhotoViewerFragment |

## Accessibility

- All images have contentDescription
- Touch targets minimum 48dp
- Sufficient color contrast (21:1 ratio for primary text)
- Screen reader compatible via Android Data Binding

## Performance Considerations

- Photo loading is async via coroutines
- Filter operations use Dispatchers.Default
- LiveData prevents unnecessary redraws
- Photo compression before upload (JPEG)
