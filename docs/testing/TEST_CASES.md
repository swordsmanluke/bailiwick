# Detailed Test Cases

## Automated Test Coverage

The following functionality is now covered by automated tests (70 total):

### JVM Unit Tests (49 tests)
| Test Class | Coverage |
|------------|----------|
| `InMemoryIrohNodeTest` (22) | Blob storage, collections, documents, subscriptions |
| `SignatureTest` (21) | MD5, SHA1, RSA signing and verification |
| `NoopEncryptorTest` (5) | Pass-through encryption |
| `RSAEncryptorTest` (1) | RSA encryption with BouncyCastle |

### Instrumentation Tests (21 tests)
| Test Class | Coverage |
|------------|----------|
| `ContentPublisherTest` (6) | Identity/post/feed/file publishing to Iroh |
| `IrohNodeTest` (10) | Blob/doc/collection operations on device |
| `BWickTest` (3) | Post creation, retrieval, circle membership |
| `QREncoderTest` (2) | QR code encode/decode with Base64 |

---

## Manual Test Cases

The following tests require manual validation or UI interaction:

### 1. App Launch & Initialization

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 1.1 | Cold Start | Fresh install, launch app | Splash screen appears within 3s | ✅ Espresso |
| 1.4 | Splash Navigation (New) | First launch after fresh install | Navigates to NewUserFragment | ✅ Espresso |
| 1.5 | Splash Navigation (Existing) | Launch with existing account | Navigates to ContentFragment | ✅ Espresso |
| 1.6 | Warm Start | Background app, reopen | Resumes without re-init | ✅ Espresso |

**Removed (covered by automated tests):**
- ~~1.2 Iroh Init~~ → `IrohNodeTest.nodeIdIs64Characters()`
- ~~1.3 Keyring Init~~ → `KeyringAndroidTest`

---

### 2. Account Creation

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 2.1 | Name Too Short | Enter "abc" (3 chars) | Go button disabled | ✅ Espresso |
| 2.2 | Name Valid | Enter "Test User" | Partial enable (need password) | ✅ Espresso |
| 2.3 | Password Too Short | Enter "1234567" (7 chars) | Go button disabled | ✅ Espresso |
| 2.4 | Password Mismatch | Enter different passwords | Go button disabled | ✅ Espresso |
| 2.5 | All Valid | Name 4+, matching passwords 8+ | Go button enabled | ✅ Espresso |
| 2.6 | Avatar Tap | Tap avatar image | Random avatar loads | ✅ Espresso |
| 2.7 | Account Created | Tap Go with valid inputs | Toast shown, navigates to Content | ✅ Espresso |

---

### 3. Content Feed

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 3.1 | Node ID Display | View Content screen | 64-char hex Node ID shown | ✅ Espresso |
| 3.2 | Avatar Display | View Content screen | Your avatar in top-left | ✅ Espresso |
| 3.3 | Empty State | New account, no posts | Empty list, no crash | ✅ Espresso |
| 3.4 | Refresh Button | Tap refresh icon | Triggers refresh, no crash | ✅ Espresso |
| 3.6 | Post List Scroll | Many posts | Smooth scrolling, no jank | Manual only |

**Removed (covered by automated tests):**
- ~~Post retrieval logic~~ → `BWickTest.allPostsAreFound()`
- ~~Circle filtering~~ → `BWickTest.postsAreInTheirExpectedCircles()`

---

### 4. Post Creation

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 4.1 | Empty Post | Leave text empty, tap Post | Nothing happens or button disabled | ✅ Espresso |
| 4.2 | Create Simple Post | Enter "Hello World", tap Post | Text clears, post saved | ✅ Espresso |
| 4.5 | Post Appears | Create, tap Refresh | Post visible in feed | ✅ Espresso |
| 4.6 | Post Order | Create multiple posts | Newest first (descending time) | ✅ Espresso |
| 4.7 | Long Text | Enter 500+ characters | Post created without truncation | ✅ Espresso |
| 4.8 | Special Characters | Enter emoji, unicode | Characters preserved correctly | ✅ Espresso |

**Removed (covered by automated tests):**
- ~~4.3 Post Has Signature~~ → `BWickTest.buildPost()` uses RSA signing
- ~~4.4 Post Has Timestamp~~ → `ContentPublisherTest.publishPostStoresBlobAndUpdatesDb()`
- ~~Post storage logic~~ → `BWickTest.creatingAPostWorks()`
- ~~Post publishing~~ → `ContentPublisherTest`

---

### 5. QR Introduction Flow

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 5.1 | Navigate to Introduce | Tap "+" button | IntroduceSelfFragment loads | ✅ Espresso |
| 5.4 | QR Generated | Identity selected | QR code image visible | ✅ Espresso |
| 5.7 | Camera Scan | Tap Scan, point at QR | QR decoded, intro processed | ❌ Requires camera |
| 5.8 | Image Scan | Tap Images, select QR file | QR decoded from file | ✅ Espresso + test asset |
| 5.11 | Full Introduction | A→B request, B→A response | Both see "Introduction Made" | ❌ Requires 2 devices |

**Removed (covered by automated tests):**
- ~~QR encode/decode~~ → `QREncoderTest.encode()`, `QREncoderTest.decode()`
- ~~Base64 handling~~ → `QREncoderTest.decode()` validates Base64 round-trip

---

### 6. Navigation

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 6.1 | Content to Connect | Tap "+" subscription | Navigate to Connect | ✅ Espresso |
| 6.2 | Back from Connect | Press back | Return to Content | ✅ Espresso |
| 6.3 | Rotation Survival | Rotate device | No crash, state preserved | ✅ Espresso |
| 6.4 | Deep Navigation | Go 3+ screens deep, back | Returns correctly | ✅ Espresso |
| 6.5 | Home Press | Press home, return | App resumes correctly | ✅ Espresso |

---

### 7. Error Handling

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 7.1 | No Network Launch | Airplane mode, launch | App starts, graceful degradation | ❌ Requires network control |
| 7.3 | Invalid QR | Scan random QR code | Error toast, no crash | ✅ Espresso + test asset |
| 7.7 | Process Death | Force stop, reopen | App starts fresh, data intact | ✅ Espresso |

---

### 8. Persistence

| ID | Test Case | Steps | Expected Result | Automatable? |
|----|-----------|-------|-----------------|--------------|
| 8.1 | Post Persistence | Create post, kill app, reopen | Post still visible | ✅ Instrumentation |
| 8.2 | Account Persistence | Create account, kill app | Account still exists | ✅ Instrumentation |
| 8.4 | Node ID Persistence | Note ID, restart | Same Node ID | ✅ Instrumentation |
| 8.5 | Clear Data | Clear app data, launch | Fresh start, new keys | Manual only |

---

## Recommended Automation Priority

### High Priority (Core User Flows)
1. **Account Creation Flow** - Tests 2.1-2.7
2. **Post Creation & Display** - Tests 4.1, 4.2, 4.5, 4.6
3. **Persistence** - Tests 8.1, 8.2, 8.4

### Medium Priority (Navigation & Error Handling)
4. **Navigation** - Tests 6.1-6.5
5. **QR from Image** - Test 5.8
6. **Error Handling** - Tests 7.3, 7.7

### Manual Only (Hardware/Multi-device Required)
- Camera scanning (5.7)
- Multi-device introduction handshake (5.11)
- Network connectivity changes (7.1, 7.2)
- Scroll performance (3.6)
- Clear data behavior (8.5)

---

## Quick Smoke Test Checklist

For rapid validation, these core tests should always pass:

```
[AUTO] Iroh blob storage works         → InMemoryIrohNodeTest
[AUTO] Posts can be created            → BWickTest.creatingAPostWorks
[AUTO] Posts are signed with RSA       → BWickTest uses RsaSignature
[AUTO] QR codes encode/decode          → QREncoderTest
[AUTO] Content publishes to Iroh       → ContentPublisherTest
[    ] App launches without crash      → Manual or Espresso
[    ] Can create new account          → Manual or Espresso
[    ] Post appears in feed            → Manual or Espresso
[    ] Data persists after restart     → Manual or Espresso
```
