package com.perfectlunacy.bailiwick.storage

/**
 * Type aliases for Iroh-based content addressing.
 *
 * Iroh uses BLAKE3 hashes instead of IPFS's SHA-256 multihash.
 * These aliases provide type safety and documentation for the different
 * identifier types used throughout the application.
 */

// Content hash - BLAKE3 (32 bytes, hex-encoded)
// Identifies immutable content in Iroh blobs
typealias BlobHash = String

// Iroh node identifier - Ed25519 public key
// Identifies a peer/device on the network
typealias NodeId = String

// Iroh document namespace ID
// Identifies a mutable document (replaces IPNS)
typealias DocNamespaceId = String

// Iroh author ID - identifies who wrote to a document
typealias AuthorId = String
