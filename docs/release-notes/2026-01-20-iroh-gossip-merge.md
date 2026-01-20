# Release: iroh-gossip Branch Merge

**Date:** 2026-01-20
**Merge Commit:** 263abef

## Summary

Merged the `iroh-gossip` development branch into `master` for release.

## New Features

### Contact Management Screen
- View all identities a contact has shared
- Mute/unmute contacts to hide their posts
- Delete contacts (removes from all circles and deletes their posts)
- Manage circle membership for contacts

### Identity Editor
- Edit display name
- Change avatar via camera, gallery, or robot avatar generation
- Unsaved changes confirmation dialog

### Circle Editor
- Edit circle name
- Add/remove members via search
- Delete circles
- Member list with avatar display

## Navigation Improvements

- Full bidirectional navigation between Feed, Profile, Contact, and Circle screens
- Long-press on circle filter to edit circle
- Tap on author to view profile
- "Manage Contact" button on other users' profiles

## Bug Fixes

- Fixed main thread database access crash on slower devices (S7)
- Fixed sync race condition with manifest version conflicts
- Fixed S7 crash from displayAvatar click listener

## Testing

- Comprehensive unit tests for DAOs (User, Identity, Circle, CircleMember)
- Integration tests for Contact and Identity editing workflows
- E2E navigation tests (99.8% pass rate on physical devices)

## Files Changed

- 50+ files modified/added
- New fragments: ContactFragment, EditIdentityFragment, EditCircleFragment
- New adapters: ContactCircleAdapter, ContactIdentityAdapter, CircleMemberAdapter
- Updated navigation graph with new actions
- New drawable resources for UI icons
