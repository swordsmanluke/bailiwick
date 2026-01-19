# Identity Editor Screen Design

## Overview

The Identity Editor screen allows users to edit their profile information. It is accessed by tapping the edit button on the User Profile screen when viewing your own profile.

## User Story

> As a User, I want to edit my identity information (name, avatar) so that I can update how I appear to others.

## Screen Layout

```
┌─────────────────────────────────────┐
│  ← Edit Profile              Save   │  Header
├─────────────────────────────────────┤
│                                     │
│         ┌─────────────┐             │
│         │             │             │
│         │   Avatar    │             │  Profile Picture Card
│         │   (120dp)   │             │
│         │      📷     │             │
│         └─────────────┘             │
│       Tap to change photo           │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Change Photo                  │  │
│  │ ┌─────────┐ ┌─────────────┐   │  │  Photo Options
│  │ │ Camera  │ │ Gallery     │   │  │
│  │ └─────────┘ └─────────────┘   │  │
│  │ ┌─────────────────────────┐   │  │
│  │ │ Generate Robot Avatar   │   │  │
│  │ └─────────────────────────┘   │  │
│  └───────────────────────────────┘  │
│                                     │
├─────────────────────────────────────┤
│                                     │
│  Profile Information                │  Section Header
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Public Name                   │  │
│  │ ┌───────────────────────────┐ │  │  Profile Info Card
│  │ │ Alice                     │ │  │
│  │ └───────────────────────────┘ │  │
│  │ This is how others see you    │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ ⚠ Changes will be published   │  │  Info Banner
│  │ to your contacts              │  │
│  └───────────────────────────────┘  │
│                                     │
└─────────────────────────────────────┘
```

## Components

### Header
- Back button (←) - returns to profile without saving
- Title: "Edit Profile"
- Save button - saves changes and returns to profile

### Avatar Section
- Large avatar display (120dp, `@dimen/avatar_profile`)
- Camera overlay icon indicating tappable
- Helper text: "Tap to change photo"

### Photo Options Card
| Option | Icon | Description |
|--------|------|-------------|
| Take Photo | `ic_camera` | Open camera to take new photo |
| Choose from Gallery | `ic_photo_library` | Select from device photos |
| Generate Robot Avatar | - | Generate a robohash-style avatar |

### Profile Information Card
- **Public Name** field with helper text: "This is how others see you"

### Info Banner
- Warning style with text: "Changes will be published to your contacts"

## Interactions

### User Flows

#### Edit Name
1. User taps on Public Name field
2. Keyboard appears, user edits text
3. User taps Save button
4. Changes saved and identity republished
5. User returns to Profile screen

#### Change Avatar
1. User taps avatar or photo option button
2. Camera/gallery/generator opens
3. User selects/captures image
4. Preview shown in avatar
5. User taps Save to confirm

#### Cancel/Back
1. User taps back button
2. If unsaved changes: show confirmation dialog
3. Options: "Discard" / "Keep Editing"

### Save Operation
1. If avatar changed: compress and store as Iroh blob
2. Update Identity in database
3. Trigger identity republish
4. Show toast: "Profile updated"
5. Navigate back to Profile

## States

| State | Display |
|-------|---------|
| Loading | Overlay with spinner and "Saving changes..." |
| Error | Toast message, remain on screen |
| Success | Toast "Profile updated", navigate back |

## Design Tokens

| Element | Token |
|---------|-------|
| Card padding | `@dimen/spacing_md` (16dp) |
| Card corner radius | `@dimen/corner_radius_md` (12dp) |
| Avatar size | `@dimen/avatar_profile` (120dp) |
| Button style | `@style/Widget.Bailiwick.Button.*` |

## Navigation

```
UserProfileFragment → [edit tap] → EditIdentityFragment → [save/back] → UserProfileFragment
```

## Implementation Files

| File | Purpose |
|------|---------|
| `fragment_edit_identity.xml` | Layout |
| `EditIdentityFragment.kt` | Fragment (to be created) |
| `nav_graph.xml` | Navigation action (to be added) |
| `strings.xml` | String resources |

## String Resources

```xml
<string name="change_photo">Change Photo</string>
<string name="profile_information">Profile Information</string>
<string name="public_name_helper">This is how others see you</string>
<string name="changes_published_warning">Changes will be published to your contacts</string>
<string name="saving_changes">Saving changes…</string>
<string name="profile_updated">Profile updated</string>
<string name="discard_changes_title">Discard changes?</string>
<string name="discard_changes_message">You have unsaved changes.</string>
<string name="discard">Discard</string>
<string name="keep_editing">Keep Editing</string>
```
