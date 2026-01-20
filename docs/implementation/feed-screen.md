# Feed Screen Implementation

## Overview

The feed screen (`ContentFragment`) provides the main social experience with three core features:

## 1. Circle Selector

**Implementation:** `CircleFilterAdapter.kt`

- Horizontal RecyclerView with filter chips
- "All" option to show all posts
- Per-circle filtering with visual selection state
- Filter state persisted via `FilterPreferences`

**Key methods:**
- `setupCircleFilter()` - Initializes the circle filter bar
- `filterByCircle()` - Applies circle-based post filtering

## 2. Post Creation

**Implementation:** `ContentFragment.kt` `submitPost()`

Features:
- Text input with multi-line support
- Photo attachment via `PhotoPicker`
- Mention autocomplete with `MentionSuggestionsAdapter`
- Tag input support
- Post signing with RSA signatures
- Iroh blob storage publishing
- Peer notification via `GossipService`

**Key methods:**
- `setupPostComposer()` - Initializes composer UI
- `setupMentionSuggestions()` - Enables @mention autocomplete
- `submitPost()` - Creates, signs, and publishes posts

## 3. Post List

**Implementation:** `PostAdapter.kt`

Features:
- Author avatar and name display
- Clickable author navigation to profile
- Single photo or photo grid display
- Post text with mention highlighting
- Reaction display and picker
- Comment count and navigation
- Delete button for own posts
- Timestamp formatting

**Key components:**
- `setupPostList()` - Initializes ListView with adapter
- `applyFilters()` - Filters posts by circle or user
- `PostAdapter` - Renders individual posts with all features

## Data Flow

```
User creates post
    -> submitPost() validates content
    -> Photos compressed and stored as blobs
    -> Post signed with user's RSA key
    -> Stored in local database
    -> Published to Iroh blob storage
    -> GossipService broadcasts to peers
    -> LiveData updates UI
```

## Filter Flow

```
User taps circle chip
    -> CircleFilterAdapter updates selection
    -> filterByCircle() called
    -> FilterPreferences updated
    -> applyFilters() queries circle posts
    -> PostAdapter refreshed
```
