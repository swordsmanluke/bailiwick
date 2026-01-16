# Key Export Format Specification

This document describes the encrypted key export format used by Bailiwick for identity backup and transfer.

## File Extension

`.bwkey` - Bailiwick Key Export File

## File Structure

The export file is a binary format with the following structure:

```
+------------------+------------------+----------------------+
|    Salt (16B)    |     IV (12B)     |  Encrypted Payload   |
+------------------+------------------+----------------------+
```

### Components

| Offset | Length | Description |
|--------|--------|-------------|
| 0      | 16     | PBKDF2 salt for key derivation |
| 16     | 12     | AES-GCM initialization vector |
| 28     | varies | AES-256-GCM encrypted JSON payload |

## Encryption

### Key Derivation

- **Algorithm**: PBKDF2-HMAC-SHA256
- **Iterations**: 100,000
- **Key Length**: 256 bits
- **Salt Length**: 16 bytes (randomly generated)

### Payload Encryption

- **Algorithm**: AES-256-GCM
- **IV Length**: 12 bytes (randomly generated)
- **Tag Length**: 128 bits

## Payload Format

The decrypted payload is a JSON object:

```json
{
  "v": 1,
  "ts": 1705334400000,
  "sk": "base64-encoded-ed25519-secret-key",
  "un": "username",
  "dn": "Display Name",
  "av": "blob-hash-of-avatar-or-null"
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `v` | integer | Format version (currently 1) |
| `ts` | long | Export timestamp (Unix milliseconds) |
| `sk` | string | Base64-encoded 32-byte Ed25519 secret key |
| `un` | string | Account username |
| `dn` | string | User's display name |
| `av` | string? | Iroh blob hash of avatar image (nullable) |

## Security Considerations

1. **Password Requirements**: Minimum 8 characters recommended
2. **Key Material**: The Ed25519 secret key is the most sensitive data - it controls the user's Iroh NodeId
3. **No Password Export**: The account password is NOT exported; users must set a new password on import
4. **One-way Encryption**: There is no recovery mechanism if the export password is lost

## Import Process

When importing:

1. Read salt (first 16 bytes) and IV (next 12 bytes)
2. Derive key from user-provided password using PBKDF2
3. Decrypt remaining bytes using AES-256-GCM
4. Parse JSON payload
5. Verify version compatibility
6. Restore Ed25519 secret key to Iroh configuration
7. Recreate identity in database
8. Download avatar blob if hash is provided

## Version History

| Version | Changes |
|---------|---------|
| 1 | Initial format with Ed25519 key, username, display name, avatar hash |

## Example Filename

```
bailiwick-John_Doe-20240115.bwkey
```

Format: `bailiwick-{sanitized-display-name}-{YYYYMMDD}.bwkey`
