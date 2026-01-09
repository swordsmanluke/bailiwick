package com.perfectlunacy.bailiwick.storage

/**
 * Type aliases for Iroh-based content addressing.
 *
 * Iroh uses BLAKE3 hashes instead of IPFS's SHA-256 multihash.
 * These aliases provide type safety and documentation for the different
 * identifier types used throughout the application.
 *
 * Note: These are type aliases rather than value classes due to Room/KAPT
 * limitations. KAPT's Java stub generation doesn't properly handle Kotlin
 * value classes, causing "Cannot find getter/setter for field" errors.
 * Consider migrating to value classes when the project adopts KSP.
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
