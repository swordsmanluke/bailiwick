# Bailiwick Sync System Documentation

This documentation describes how Bailiwick's peer-to-peer sync system works using Iroh Docs.

## Overview

Bailiwick uses **Iroh** for decentralized content synchronization. Each user has their own **Doc** (a mutable key-value store) where they publish content. Users subscribe to each other's Docs to receive updates.

The sync system is **pull-based**: you pull content from peers you've subscribed to, rather than having content pushed to you.

## Architecture

- [Architecture Overview](architecture.md) - High-level view of the sync system components

## Core Concepts

- [Doc Structure](doc-structure.md) - How data is organized in Iroh Docs
- [Peer Introduction](introduction.md) - How users connect and exchange keys

## Sync Operations

- [Publishing](publishing.md) - How local content is encrypted and published
- [Downloading](downloading.md) - How content is synced from peers
- [Sync Loop](sync-loop.md) - Background service that drives sync

## Known Issues

- [Sync Failure Analysis](sync-failure.md) - Documentation of observed sync problems
- [Blobs API Workaround](blobs-workaround.md) - Potential workarounds using direct blob transfer

## Quick Reference

### Key Flows

1. **Connect to peer**: Scan QR code → Join Doc → Exchange keys
2. **Create post**: Store locally → Encrypt → Publish to Doc
3. **Sync content**: Join peer Doc → Wait for sync → Download posts

### Doc Entry Types

| Key Pattern | Content |
|-------------|---------|
| `identity` | User profile (name, picture) |
| `circles/{id}` | Circle with member list |
| `posts/{circleId}/{timestamp}` | Individual post |
| `actions/{targetNodeId}/{timestamp}` | Key exchange action |
| `feed/latest` | (Deprecated) Post list |

### Key Files

| File | Purpose |
|------|---------|
| `ContentPublisher.kt` | Publishes local content to Iroh |
| `ContentDownloader.kt` | Downloads content from peers |
| `IrohService.kt` | Background sync service |
| `IrohWrapper.kt` | Kotlin interface to Iroh |
| `IrohDocKeys.kt` | Doc key constants |

## Diagrams

All diagrams are created with [D2](https://d2lang.com/) and compiled to PNG:

- `architecture.png` - Component overview
- `doc-structure.png` - Entry layout
- `introduction.png` - Peer connection flow
- `publishing.png` - Content publishing
- `downloading.png` - Content downloading
- `sync-loop.png` - Background sync
- `sync-failure.png` - Known issue timeline
