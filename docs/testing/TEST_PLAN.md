# Bailiwick Test Plan

## Test Coverage Summary

| Test Type | Count | Coverage |
|-----------|-------|----------|
| JVM Unit Tests | 49 | Crypto, signatures, Iroh storage |
| Instrumentation Tests | 21 | Publishing, QR, database, device storage |
| **Total Automated** | **70** | Core business logic |
| Manual Tests | ~25 | UI flows, multi-device, hardware |

---

## Running Automated Tests

```bash
# JVM unit tests (no device needed)
./gradlew testDebugUnitTest

# Instrumentation tests (device required)
./gradlew connectedDebugAndroidTest

# Both
./gradlew test connectedDebugAndroidTest
```

---

## Test Environment

### Prerequisites

| Requirement | Details |
|-------------|---------|
| Android Device | API 29+ (Android 10 or higher), USB debugging enabled |
| Build Tools | Gradle via wrapper (`./gradlew`) |
| Debug Tools | ADB (`$ANDROID_HOME/platform-tools/adb`) |

### Device Setup

```bash
# Verify device connected
adb devices

# Install debug APK
./gradlew installDebug
```

---

## Automated Test Coverage

### What's Covered

| Area | Test Class | Key Tests |
|------|------------|-----------|
| Iroh Blob Storage | `InMemoryIrohNodeTest`, `IrohNodeTest` | Store/retrieve blobs, hash consistency |
| Iroh Documents | `InMemoryIrohNodeTest`, `IrohNodeTest` | Key-value ops, subscriptions |
| Iroh Collections | `InMemoryIrohNodeTest`, `IrohNodeTest` | Directory-like structures |
| Content Publishing | `ContentPublisherTest` | Identity, posts, feeds, files |
| Post Management | `BWickTest` | Create, retrieve, circle membership |
| QR Codes | `QREncoderTest` | Encode/decode with Base64 |
| Cryptographic Signing | `SignatureTest` | MD5, SHA1, RSA sign/verify |
| Encryption | `NoopEncryptorTest`, `RSAEncryptorTest` | Encrypt/decrypt operations |

### What's NOT Covered (Manual Testing Required)

| Area | Reason |
|------|--------|
| UI Navigation | Requires Espresso setup |
| Account Creation UI | Requires Espresso setup |
| Camera QR Scanning | Requires physical camera |
| Multi-device Sync | Requires 2+ devices |
| Network Error Handling | Requires network manipulation |
| Scroll Performance | Subjective/visual |

---

## Manual Test Categories

See [TEST_CASES.md](./TEST_CASES.md) for detailed test cases.

| Category | Tests | Priority | Automatable |
|----------|-------|----------|-------------|
| App Launch | 4 | Blocking | ✅ Espresso |
| Account Creation | 7 | Blocking | ✅ Espresso |
| Content Feed | 5 | Blocking | Mostly ✅ |
| Post Creation | 6 | Blocking | ✅ Espresso |
| QR Introduction | 5 | Important | Partial |
| Navigation | 5 | Important | ✅ Espresso |
| Error Handling | 3 | Important | Partial |
| Persistence | 4 | Important | ✅ Instrumentation |

---

## Quick Smoke Test

Run after any significant changes:

```bash
# 1. Run all automated tests
./gradlew test connectedDebugAndroidTest

# 2. Manual smoke test (5 minutes)
```

### Manual Smoke Checklist

```
[ ] App launches without crash
[ ] Can create new account (use debug "Lucas Taylor" button)
[ ] Can create text post
[ ] Post appears in feed after refresh
[ ] App survives rotation
[ ] QR code generates on Introduce screen
[ ] Data persists after app restart
```

---

## Recommended Next Steps for Automation

### High Priority
1. Add Espresso UI tests for account creation flow
2. Add Espresso UI tests for post creation/display
3. Add persistence tests (create data → kill app → verify)

### Medium Priority
4. Add navigation tests
5. Add QR image scanning test (with test asset)
6. Add rotation/configuration change tests

### Requires Special Setup
- Camera scanning (needs camera mock or skip)
- Multi-device sync (needs test harness)
- Network error handling (needs network simulation)

---

## Logcat Commands

```bash
# Full Bailiwick logging
adb logcat | grep -E "Iroh|Bailiwick|perfectlunacy"

# Errors only
adb logcat *:E | grep -E "perfectlunacy|iroh"

# Specific components
adb logcat -s IrohWrapper:* ContentFragment:* DeviceKeyring:*
```

---

## Related Documentation

- [Detailed Test Cases](./TEST_CASES.md)
- [Out of Scope Features](./OUT_OF_SCOPE.md)
- [Troubleshooting Guide](./TROUBLESHOOTING.md)
