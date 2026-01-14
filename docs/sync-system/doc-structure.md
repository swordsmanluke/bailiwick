# Iroh Doc Structure

![Doc Structure Diagram](doc-structure.png)

## What is an Iroh Doc?

An Iroh Doc is a mutable key-value store that syncs between peers using **set reconciliation**. Each user has their own Doc where they publish content. Peers subscribe to each other's Docs to receive updates.

## Entry Structure

Every entry in a Doc contains:

| Field | Description |
|-------|-------------|
| **Key** | Byte string identifying the content (e.g., `posts/1/1704067200000`) |
| **Author ID** | Who wrote this entry (Ed25519 public key) |
| **Content Hash** | BLAKE3 hash of the blob content |
| **Timestamp** | When the entry was written |

## Key Types

### `identity`
User profile information (name, profile picture hash). Unencrypted so peers can display profile info.

### `circles/{circleId}`
Circle definitions with member lists. Encrypted with circle keys.

### `posts/{circleId}/{timestamp}`
Individual posts. Each post gets a **unique key** with its timestamp, making them append-only. Encrypted with circle keys.

### `actions/{targetNodeId}/{timestamp}`
Actions directed at specific peers (e.g., `UpdateKey` to share encryption keys). Used for key exchange during introductions.

### `feed/latest` (DEPRECATED)
Previously stored a list of all post hashes. **Problematic** because overwrites don't sync reliably between devices. Being replaced by key-based discovery.

## Sync Behavior

Iroh Docs use **set reconciliation** to sync entries:

- **Same key, different author**: Both entries kept
- **Same key, same author**: Latest timestamp wins
- **New keys**: Should propagate to all replicas

### Known Issue

New entries are not reliably propagating between devices even after successful sync. See [Sync Failure Analysis](sync-failure.md) for details.

## Content Storage

Doc entries store **blob hashes**, not content directly:

```
Key: "posts/1/1704067200000"
Value: "58d47ae784f6..." (BLAKE3 hash)
         ↓
Blob Store: JSON → encrypted bytes
```

The actual content is stored in Iroh's blob store, content-addressed by hash.
