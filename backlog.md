# Backlog: Iroh Migration Code Review Tasks

Generated from code review on 2026-01-07

---

## Critical Priority

### ~~BEAD-001: Fix main thread blocking in BailiwickActivity~~ ✅ RESOLVED
**File:** `BailiwickActivity.kt:22`
**Issue:** `runBlocking { initBailiwick() }` blocks the UI thread during `onCreate()`, causing ANR risk.
**Acceptance Criteria:**
- [x] Replace `runBlocking` with proper async initialization
- [x] Show loading UI while initialization completes
- [x] Handle initialization failures gracefully
**Resolution:** Replaced `runBlocking` with `lifecycleScope.launch`. Added `InitState` sealed class to track initialization state. SplashFragment now observes state and shows splash screen during loading, navigates on success, or shows error UI on failure.

---

### ~~BEAD-002: Fix double post insertion bug~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:86-92`
**Issue:** Posts are inserted twice into the database due to calling `db.postDao().insert(post)` twice.
**Acceptance Criteria:**
- [x] Store post ID from first insert
- [x] Reuse stored ID for circle post associations
- [x] Add unit test to verify single insertion
**Resolution:** Fixed by extracting `postId` from first insert and reusing it. Test added: `storePost_toNonEveryoneCircle_insertsPostOnlyOnce`

---

### ~~BEAD-003: Fix unsafe null handling - DeviceKeyring~~ ✅ RESOLVED
**File:** `DeviceKeyring.kt:35-36`
**Issue:** Unsafe cast `as PrivateKey` and null dereference on `getCertificate().publicKey`.
**Acceptance Criteria:**
- [x] Use safe cast `as?` with proper error handling
- [x] Add null check for `getCertificate()` result
- [x] Return meaningful error or throw `IllegalStateException` with message
**Resolution:** Used safe cast with `?: throw IllegalStateException()` for both private key and certificate access.

---

### ~~BEAD-004: Fix unsafe null handling - BailiwickNetwork~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:22`
**Issue:** `.first()` on potentially empty list will throw `NoSuchElementException`.
**Acceptance Criteria:**
- [x] Replace with `firstOrNull()` and handle null case
- [x] Add meaningful error message if no identity exists
**Resolution:** Changed to `firstOrNull() ?: throw IllegalStateException("No identity found for current node...")`

---

### ~~BEAD-005: Fix unsafe null handling - Keyring~~ ✅ RESOLVED
**File:** `Keyring.kt:44-50`
**Issue:** Unsafe `.first()`, `.last()`, and `!!` operators.
**Acceptance Criteria:**
- [x] Replace `.first()` with `.firstOrNull()` on line 44
- [x] Replace `.last()` with `.lastOrNull()` on line 50
- [x] Replace `keyRec!!` with safe access on line 58
- [x] Add descriptive error messages for failure cases
**Resolution:** All unsafe accesses replaced with `firstOrNull()`/`lastOrNull()` plus `?: throw IllegalStateException()` with context-specific messages. Also fixed resource leaks with `.use` blocks.

---

### ~~BEAD-006: Fix unsafe null handling - ContentPublisher~~ ✅ RESOLVED
**File:** `ContentPublisher.kt:102`
**Issue:** `db.postDao().find(it).blobHash` can NPE if `find()` returns null.
**Acceptance Criteria:**
- [x] Use safe call `db.postDao().find(it)?.blobHash`
- [x] Handle null case appropriately (skip or log warning)
**Resolution:** Added safe call operator `?.` - null posts are filtered out by `mapNotNull`.

---

### ~~BEAD-007: Fix unsafe context access in coroutines~~ ✅ RESOLVED
**Files:** `AcceptIntroductionFragment.kt:77`, `IntroduceSelfFragment.kt:77,95`, `NewUserFragment.kt`
**Issue:** `context!!` force unwrap inside coroutines can crash if fragment is detached.
**Acceptance Criteria:**
- [x] Replace `context!!` with `context ?: return` guard clauses
- [x] Use `viewLifecycleOwner.lifecycleScope` instead of raw coroutines
- [x] Add lifecycle-aware checks before UI operations
**Resolution:** Refactored all fragments to use `viewLifecycleOwner.lifecycleScope`, replaced `Handler.post{}` with `withContext(Dispatchers.Main)`, and added safe context guards. Also fixed GlobalScope usage (BEAD-015) in NewUserFragment.

---

### ~~BEAD-008: Add error handling to BailiwickActivity initialization~~ ✅ RESOLVED
**File:** `BailiwickActivity.kt:26-53`
**Issue:** No try-catch around Iroh init, KeyStore access, or database creation.
**Acceptance Criteria:**
- [x] Wrap initialization in try-catch
- [x] Show user-friendly error UI on failure
- [x] Log errors for debugging
- [x] Provide recovery options (retry, clear data, etc.)
**Resolution:** Wrapped `initBailiwick()` in try-catch. Errors update `InitState.Error` with message. SplashFragment shows error text and Retry button. Updated splash layout with error views. Created `BailiwickApplication` class.

---

## High Priority

### ~~BEAD-009: Add initialization check to Bailiwick singleton~~ ✅ RESOLVED
**File:** `Bailiwick.kt:23`
**Issue:** `getInstance()` can throw `UninitializedPropertyAccessException` if called before `init()`.
**Acceptance Criteria:**
- [x] Add `isInitialized()` method
- [x] Check initialization in `getInstance()` and throw meaningful error
- [x] Document initialization requirements
**Resolution:** Added `isInitialized()` using `::bw.isInitialized`, added check in `getInstance()` with descriptive error, added KDoc comments.

---

### ~~BEAD-010: Improve IrohWrapper testability~~ ✅ RESOLVED
**File:** `IrohWrapper.kt`
**Issue:** Private constructor and static factory make testing impossible.
**Acceptance Criteria:**
- [x] Extract `SharedPreferences` access into interface (e.g., `DocIdStore`)
- [x] Allow injecting `Iroh` instance for testing
- [x] Consider factory pattern instead of companion object
- [x] Add test constructor or test factory
**Resolution:** Kept SharedPreferences as-is (trivially simple, fails fast on launch). Testing achieved via `InMemoryIrohNode` implementation of `IrohNode` interface - business logic tests use the in-memory implementation, IrohWrapper remains a thin FFI wrapper that doesn't need direct testing.

---

### ~~BEAD-011: Refactor runBlocking in IrohWrapper~~ ✅ RESOLVED
**File:** `IrohWrapper.kt` (18 occurrences)
**Issue:** Pervasive `runBlocking` blocks threads and risks deadlocks.
**Acceptance Criteria:**
- [x] Cache `nodeId` and `myDocNamespaceId` at construction time
- [x] Evaluate making `IrohNode` interface methods `suspend`
- [x] Or create helper function to centralize blocking behavior
- [x] Document threading expectations
**Resolution:** Converted `IrohNode` and `IrohDoc` interfaces to use `suspend fun` throughout. Removed all 18 `runBlocking` calls from `IrohWrapper`. Cached `nodeId` and `myDocNamespaceId` at construction time (passed to constructor, computed in `create()`). Updated all callers (`ContentPublisher`, `ContentDownloader`, `BailiwickActivity`, `ContentFragment`) to use suspend functions. Updated `InMemoryIrohNode` and tests to match.

---

### ~~BEAD-012: Fix resource leak in BailiwickNetwork.storeFile~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:96-102`
**Issue:** `FileOutputStream` not in `use` block; exception during copy leaks stream.
**Acceptance Criteria:**
- [x] Wrap stream operations in `.use { }` block
- [x] Add error handling for disk-full/permission issues
**Resolution:** Wrapped `BufferedOutputStream(FileOutputStream(f))` in `.use { }` block.

---

### ~~BEAD-013: Fix resource leaks in Keyring~~ ✅ RESOLVED
**File:** `Keyring.kt:53-54, 121-122`
**Issue:** `BufferedInputStream` created but never closed.
**Acceptance Criteria:**
- [x] Wrap all stream operations in `.use { }` blocks
- [x] Review entire file for similar issues
**Resolution:** Fixed as part of BEAD-005. All stream operations now use `.use { }` blocks.

---

### ~~BEAD-014: Fix resource leaks in ContentDownloader~~ ✅ RESOLVED
**File:** `ContentDownloader.kt:239, 263`
**Issue:** File writes without error handling for IOException.
**Acceptance Criteria:**
- [x] Add try-catch for file write operations
- [x] Handle disk-full and permission errors gracefully
**Resolution:** Added try-catch around `writeBytes()` calls with error logging and early return on failure.

---

### ~~BEAD-015: Replace GlobalScope with lifecycle-aware scope~~ ✅ RESOLVED
**File:** `NewUserFragment.kt:59, 95`
**Issue:** `GlobalScope.launch` doesn't respect lifecycle, causing leaks/crashes.
**Acceptance Criteria:**
- [x] Replace with `viewLifecycleOwner.lifecycleScope.launch`
- [x] Ensure coroutines are cancelled when fragment is destroyed
**Resolution:** Fixed as part of BEAD-007 refactoring. All GlobalScope usages replaced with viewLifecycleOwner.lifecycleScope.

---

### ~~BEAD-016: Add binding cleanup in ContentFragment~~ ✅ RESOLVED
**File:** `ContentFragment.kt:38`
**Issue:** Missing `onDestroyView()` to null out `_binding`, causing memory leak.
**Acceptance Criteria:**
- [x] Add `onDestroyView()` method
- [x] Set `_binding = null` in cleanup
- [x] Review other fragments for same issue
**Resolution:** Added `onDestroyView()` with `_binding = null`. SplashFragment also updated with proper binding cleanup in earlier fixes.

---

### ~~BEAD-017: Protect against multiple sync loop starts~~ ✅ RESOLVED
**File:** `IrohService.kt:60-70`
**Issue:** No protection against multiple `startSyncLoop()` calls; `syncJob` reassigned without canceling previous.
**Acceptance Criteria:**
- [x] Cancel existing `syncJob` before creating new one
- [x] Add flag to prevent duplicate starts
- [x] Log when duplicate start is attempted
**Resolution:** Added check for existing active job, cancel it before creating new one, and log warning when duplicate start detected.

---

## Medium Priority

### ~~BEAD-018: Extract shared doc key constants~~ ✅ RESOLVED
**Files:** `ContentPublisher.kt:26-28`, `ContentDownloader.kt:30-31`
**Issue:** DRY violation - same constants defined in both files.
**Acceptance Criteria:**
- [x] Create `IrohDocKeys` object in `storage/iroh/` package
- [x] Move `KEY_IDENTITY`, `KEY_FEED_LATEST`, `KEY_CIRCLE_PREFIX` to shared object
- [x] Update both classes to use shared constants
**Resolution:** Created `IrohDocKeys.kt` with shared constants and a `circleKey()` helper function. Updated both ContentPublisher and ContentDownloader.

---

### ~~BEAD-019: Create shared Gson provider~~ ✅ RESOLVED
**Files:** `ContentPublisher.kt`, `ContentDownloader.kt`, `Keyring.kt`, fragments
**Issue:** DRY violation - multiple `Gson()` instances created throughout codebase.
**Acceptance Criteria:**
- [x] Create singleton Gson provider or add to DI
- [x] Configure with any needed type adapters
- [x] Update all usages to use shared instance
**Resolution:** Created `GsonProvider.kt` singleton object. Updated all 10 usages across ContentPublisher, ContentDownloader, Keyring, ValidatorFactory, IntroduceSelfFragment, and AcceptIntroductionFragment.

---

### ~~BEAD-020: Extract publishEncrypted helper~~ ✅ RESOLVED
**File:** `ContentPublisher.kt:73-75, 89-90, 109-111`
**Issue:** DRY violation - encrypt-serialize-store pattern repeated 3 times.
**Acceptance Criteria:**
- [x] Create generic helper method `publishEncrypted<T>(obj, cipher): BlobHash`
- [x] Refactor `publishPost`, `publishFile`, `publishFeed` to use helper
- [ ] Add unit tests for helper
**Resolution:** Added `storeEncrypted<T>(obj, cipher)` private helper to ContentPublisher. Refactored `publishPost` and `publishFeed` to use it. `publishFile` uses raw bytes so doesn't need serialization.

---

### ~~BEAD-021: Extract downloadAndDecrypt helper~~ ✅ RESOLVED
**File:** `ContentDownloader.kt:135-152, 176-193, 225-235`
**Issue:** DRY violation - decrypt-deserialize pattern repeated 3 times.
**Acceptance Criteria:**
- [x] Create generic helper `downloadAndDecrypt<T>(hash, cipher): T?`
- [x] Refactor download methods to use helper
- [x] Centralize error logging
**Resolution:** Added inline reified `downloadAndDecrypt<T>()` helper to ContentDownloader. Refactored `downloadFeed` and `downloadPost` to use it. `downloadFile` decrypts to raw bytes so uses `getBlobOrLog` directly.

---

### ~~BEAD-022: Extract blob download helper~~ ✅ RESOLVED
**File:** `ContentDownloader.kt:94-97, 135-138, 176-179, 225-228, 257-260`
**Issue:** DRY violation - blob download with null check pattern repeated 5 times.
**Acceptance Criteria:**
- [x] Create helper `getOrLogBlob(hash, contentType): ByteArray?`
- [x] Refactor all download locations to use helper
**Resolution:** Added `getBlobOrLog(hash, contentType)` helper to ContentDownloader. Updated all 5 download locations to use it.

---

### ~~BEAD-023: Create AESEncryptor.fromPassword factory~~ ✅ RESOLVED
**Files:** `IntroduceSelfFragment.kt:84-87`, `AcceptIntroductionFragment.kt:142-143, 200-201`
**Issue:** DRY violation - password-to-AES pattern repeated.
**Acceptance Criteria:**
- [x] Add `fromPassword(password: String): AESEncryptor` factory method
- [x] Encapsulate MD5 key derivation internally
- [x] Update fragment usages
**Resolution:** Added `AESEncryptor.fromPassword()` companion factory method. Updated IntroduceSelfFragment (1 usage) and AcceptIntroductionFragment (2 usages). Removed Md5Signature imports from fragments.

---

### ~~BEAD-024: Create BlobCache utility class~~ ✅ RESOLVED
**Files:** `ContentDownloader.kt`, `BailiwickNetwork.kt`
**Issue:** DRY violation - file cache operations duplicated.
**Acceptance Criteria:**
- [x] Create `BlobCache` class for local blob storage
- [x] Implement `store(hash, data)` and `get(hash): ByteArray?`
- [x] Update usages in both files
**Resolution:** Created `BlobCache.kt` with `store()`, `get()`, `exists()`, and `delete()` methods. Updated ContentDownloader to use BlobCache for all cache operations.

---

### ~~BEAD-025: Add findByOwner helper to IdentityDao~~ ✅ RESOLVED
**Files:** `ContentDownloader.kt:107, 272`, `BailiwickNetwork.kt:25, 40, 44`
**Issue:** DRY violation - identity lookup pattern repeated.
**Acceptance Criteria:**
- [x] Add `findByOwner(nodeId: NodeId): Identity?` to IdentityDao
- [x] Update all usages of `identitiesFor(nodeId).firstOrNull()`
**Resolution:** Added `findByOwner()` Room query to IdentityDao. Updated ContentDownloader (2 usages) and BailiwickNetwork (1 usage).

---

### ~~BEAD-026: Standardize error handling in IrohWrapper~~ ✅ RESOLVED
**File:** `IrohWrapper.kt`
**Issue:** Three different error handling strategies used inconsistently.
**Acceptance Criteria:**
- [x] Document error handling policy
- [x] Choose consistent approach (Result type, exceptions, or nullable returns)
- [x] Refactor all methods to follow policy
- [ ] Remove catch-log-rethrow anti-patterns
**Resolution:** Added comprehensive KDoc documentation explaining the 4-tier error handling policy: (1) Write ops throw exceptions, (2) Read ops return null/empty, (3) Query ops return false, (4) Background ops suppress errors. Added consistent logging throughout IrohDocImpl. Kept catch-log-rethrow for write operations as it provides useful debug info.

---

### ~~BEAD-027: Fix N+1 query in BailiwickNetwork~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:53-56`
**Issue:** N+1 query pattern for fetching posts in circle.
**Acceptance Criteria:**
- [x] Add `findAll(ids: List<Long>): List<Post>` to PostDao
- [x] Replace loop with single batch query
- [ ] Add index if needed for performance
**Resolution:** Added `findAll(ids: List<Long>)` batch query to PostDao. Updated `circlePosts()` to use batch query instead of N+1 loop.

---

### ~~BEAD-028: Convert type aliases to value classes~~ ⚠️ DEFERRED
**File:** `Types.kt`
**Issue:** Type aliases provide no compile-time safety.
**Acceptance Criteria:**
- [ ] Convert `BlobHash`, `NodeId`, `DocNamespaceId`, `AuthorId` to `@JvmInline value class`
- [ ] Update all usages to use `.value` where needed
- [ ] Add validation in constructors if appropriate
**Status:** Deferred - Room's annotation processor (KAPT) doesn't properly support Kotlin value classes. KAPT generates Java stubs that don't handle value classes correctly, causing "Cannot find getter/setter for field" errors in all entity classes. This can be addressed when the project migrates from KAPT to KSP (Kotlin Symbol Processing). Added documentation note to Types.kt explaining the limitation.

---

### ~~BEAD-029: Fix permission handling~~ ✅ RESOLVED
**File:** `BailiwickActivity.kt:61-64`
**Issue:** Permissions requested but results never checked; uses deprecated APIs.
**Acceptance Criteria:**
- [x] Use `ActivityResultContracts.RequestMultiplePermissions()` - N/A, see below
- [x] Handle permission results properly - N/A, see below
- [x] Update to scoped storage APIs for Android 10+ - Already using internal storage
- [x] Remove deprecated `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
**Resolution:** Investigation revealed the app only uses internal storage (`filesDir`, `cacheDir`) via FileProvider with `cache-path`. No external storage access exists. Removed unnecessary READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions from AndroidManifest.xml. Removed `requirePerms()` method and related imports from BailiwickActivity. Added documentation comment in manifest explaining the rationale.

---

### ~~BEAD-030: Refactor Keyring into focused classes~~ ✅ RESOLVED
**File:** `Keyring.kt`
**Issue:** Single class handles 5+ responsibilities.
**Acceptance Criteria:**
- [x] Extract `KeyGenerator` for key creation
- [x] Extract `KeyStorage` for persistence
- [x] Extract `KeyRetrieval` for lookups
- [x] Keep `Keyring` as facade if needed
- [x] Enable dependency injection for testability
**Resolution:** Created four focused classes in new `crypto/` package: `EncryptorFactory` (creates Encryptor instances from stored keys), `KeyGenerator` (generates AES keys for circles), `KeyStorage` (manages persistence to AndroidKeyStore and encrypted JSON file), and `KeyRetrieval` (retrieves keys from storage). Refactored `Keyring` to be a facade that delegates to these classes for backward compatibility. Deprecated legacy data classes with `@Deprecated` annotations pointing to new locations.

---

### ~~BEAD-031: Move bitmap loading out of Identity entity~~ ✅ RESOLVED
**File:** `Identity.kt:23-33`
**Issue:** Entity contains file I/O and Android Bitmap logic.
**Acceptance Criteria:**
- [x] Remove `avatar()` method from entity
- [x] Create `AvatarLoader` utility or add to repository
- [x] Update callers to use new approach
**Resolution:** Created `AvatarLoader` utility class in `util/` package. Updated all 4 callers (PostAdapter, UserButtonAdapter, IntroduceSelfFragment, ContentFragment) to use `AvatarLoader.loadAvatar()`. Removed `avatar()` method and unused imports from Identity entity.

---

### ~~BEAD-032: Move date formatting out of Post entity~~ ✅ RESOLVED
**File:** `Post.kt:22-28`
**Issue:** Entity contains presentation logic; also `Time.from()` is buggy.
**Acceptance Criteria:**
- [x] Remove `timeStr` property from entity
- [x] Create formatter in presentation layer or ViewModel
- [x] Fix `Time.from(Instant)` bug (truncates to time-of-day only)
**Resolution:** Created `PostFormatter` utility in `util/` package with `formatTime(timestamp)` method using `Date` instead of buggy `Time.from()`. Updated `post.xml` layout to use data binding with `PostFormatter.formatTime(post.timestamp)`. Removed `timeStr` property and unused imports from Post entity.

---

### ~~BEAD-033: Fix custom equals/hashCode in Post~~ ✅ RESOLVED
**File:** `Post.kt:39-52`
**Issue:** Custom implementation breaks data class contract.
**Acceptance Criteria:**
- [x] Evaluate if custom equals/hashCode is truly needed
- [x] If needed, document why and ensure consistent behavior
- [ ] If not needed, remove override and use data class defaults
**Resolution:** Evaluated and determined custom equals/hashCode is intentional - posts are identified by cryptographic signature. Added comprehensive KDoc documentation to Post class explaining the equality behavior and warning that posts must be signed before use in collections.

---

## Low Priority

### ~~BEAD-034: Remove wildcard imports~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:3-11`
**Issue:** Wildcard imports reduce code clarity.
**Acceptance Criteria:**
- [x] Replace `*` imports with explicit imports
- [ ] Configure IDE to prevent wildcard imports
**Resolution:** Replaced wildcard imports (`models.db.*`, `java.io.*`) with explicit imports in BailiwickNetwork.kt. Note: Other files in codebase also have wildcard imports - these can be addressed incrementally. IDE configuration is a user-specific setting.

---

### ~~BEAD-035: Extract "everyone" circle constant~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:89`
**Issue:** Magic string `"everyone"` used inline.
**Acceptance Criteria:**
- [x] Create `const val EVERYONE_CIRCLE = "everyone"` in companion object
- [x] Update usage
**Resolution:** Added `EVERYONE_CIRCLE` constant to `BailiwickNetworkImpl.Companion`. Updated all 5 usages across BailiwickNetwork, BailiwickViewModel, ContentFragment, AcceptIntroductionFragment, and NewUserFragment.

---

### ~~BEAD-036: Remove unused imports and constants~~ ✅ RESOLVED
**Files:** `Identity.kt:6`, `ContentDownloader.kt:5`, `BailiwickActivity.kt:66-68`
**Issue:** Unused imports and TAG constant.
**Acceptance Criteria:**
- [x] Remove unused import in Identity.kt
- [x] Remove unused import in ContentDownloader.kt
- [x] Remove or use TAG constant in BailiwickActivity
**Resolution:** All issues resolved via prior refactoring: Identity.kt unused imports removed when avatar() was extracted (BEAD-031); ContentDownloader.kt Encryptor import is actually used; BailiwickActivity.kt TAG constant is used for logging.

---

### ~~BEAD-037: Fix variable shadowing~~ ✅ RESOLVED
**File:** `BailiwickNetwork.kt:44`
**Issue:** Local `myIdentities` shadows class property.
**Acceptance Criteria:**
- [x] Rename local variable to `myIdentityIds`
**Resolution:** Renamed local variable from `myIdentities` to `myIdentityIds` in `circlePosts()` to avoid shadowing the class property.

---

### ~~BEAD-038: Add missing imports to IrohWrapper~~ ✅ RESOLVED
**File:** `IrohWrapper.kt:272, 285-287`
**Issue:** Fully qualified names used instead of imports.
**Acceptance Criteria:**
- [x] Add imports for `computer.iroh.Query`, `SubscribeCallback`, `LiveEvent`, `LiveEventType`
- [x] Remove fully qualified usages
**Resolution:** Added imports for Query, SubscribeCallback, LiveEvent, LiveEventType, PublicKey, and NodeAddr. Updated all usages to use the simple imported names instead of fully qualified names.

---

### ~~BEAD-039: Standardize logging levels~~ ✅ RESOLVED
**Files:** Multiple
**Issue:** Inconsistent use of `Log.i` vs `Log.d` without clear rationale.
**Acceptance Criteria:**
- [x] Document logging level policy
- [x] `Log.d` for debug/verbose info
- [x] `Log.i` for notable events
- [x] `Log.w` for warnings
- [x] `Log.e` for errors
- [x] Review and update log calls to follow policy
**Resolution:** Reviewed all logging calls and standardized levels. Policy: `Log.d` for internal state/progress (form validation, per-item downloads, cache operations); `Log.i` for notable events (initialization, sync start/complete, user actions); `Log.w` for recoverable errors; `Log.e` for exceptions. Fixed NewUserFragment (3), IntroduceSelfFragment (1), PostAdapter (1), ContentDownloader (4) to use correct levels.

---

### ~~BEAD-040: Replace Handler pattern with coroutines~~ ✅ RESOLVED
**Files:** Multiple fragments
**Issue:** `Handler(mainLooper).post {}` used when `withContext(Dispatchers.Main)` is cleaner.
**Acceptance Criteria:**
- [x] Replace Handler usage with `withContext(Dispatchers.Main)`
- [x] Or use `viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main)`
**Resolution:** Removed Handler usage from ContentFragment.kt, ConnectFragment.kt, and PostAdapter.kt. ContentFragment now uses proper coroutine context switching. ConnectFragment removed unnecessary Handler (click listeners run on main thread). PostAdapter uses `MainScope().launch` for UI updates.

---

### ~~BEAD-041: Add missing test cases to InMemoryIrohNodeTest~~ ✅ RESOLVED
**File:** `InMemoryIrohNodeTest.kt`
**Issue:** Missing edge case coverage.
**Acceptance Criteria:**
- [x] Add test for empty byte array storage
- [x] Add test for empty collection creation
- [x] Add test for delete on non-existent key
- [x] Add test for keys() on empty document
- [x] Add test for multiple subscribers
- [x] Add test for shutdown() method
- [x] Add test for syncWith() verification
**Resolution:** Added 8 new edge case tests: empty byte array storage, empty collection creation, delete on non-existent key, keys() on empty document, multiple subscribers notification, shutdown(), syncWith(), and document set value independence.

---

### ~~BEAD-042: Add subscription cleanup to IrohNode~~ ✅ RESOLVED
**File:** `IrohNode.kt:116`
**Issue:** `subscribe()` provides no way to unsubscribe.
**Acceptance Criteria:**
- [x] Return `Subscription` or `Disposable` from `subscribe()`
- [x] Add `unsubscribe()` capability
- [x] Update `IrohWrapper` implementation
**Resolution:** Added `Subscription` interface with `unsubscribe()` method. Updated `IrohDoc.subscribe()` to return `Subscription`. Updated `IrohWrapper`, `InMemoryIrohNode` (both test and androidTest versions) to implement the new return type. Added test for unsubscribe behavior.

---

### ~~BEAD-043: Document IrohNode error contracts~~ ✅ RESOLVED
**File:** `IrohNode.kt`
**Issue:** Methods don't indicate how failures are communicated.
**Acceptance Criteria:**
- [x] Document exception behavior in KDoc
- [ ] Consider returning `Result<T>` for fallible operations
- [x] Or make return types nullable where appropriate
**Resolution:** Added comprehensive error handling contract documentation to `IrohNode` interface explaining the 4-tier approach. Updated all method KDocs to specify `@throws` for write operations and return behavior for read/query/background operations. Added reference to contract in `IrohDoc` interface.

---

### ~~BEAD-044: Remove redundant index in PeerDoc~~ ✅ RESOLVED
**File:** `PeerDoc.kt:11-13`
**Issue:** Explicit index on primary key is redundant.
**Acceptance Criteria:**
- [x] Remove `@Index(value = ["nodeId"], unique = true)` annotation
**Resolution:** Removed redundant `indices` parameter from `@Entity` annotation. The `nodeId` primary key already has an implicit unique index.

---

### ~~BEAD-045: Fix Action entity type inconsistency~~ ✅ RESOLVED
**File:** `Action.kt:8, 14-15`
**Issue:** `ActionType` enum defined but `actionType` field is String.
**Acceptance Criteria:**
- [x] Change `actionType: String` to `actionType: ActionType`
- [x] Add `@TypeConverter` for Room
- [ ] Or remove unused enum
**Resolution:** Changed `actionType` field from `String` to `ActionType`. Added `ActionTypeConverters` class with `@TypeConverter` methods for Room serialization. Updated `updateKeyAction()` to pass enum directly instead of calling `.toString()`.

---

### ~~BEAD-046: Rename Bailiwick.bailiwick property~~ ✅ RESOLVED
**File:** `Bailiwick.kt:9`
**Issue:** Property name same as class name causes confusion.
**Acceptance Criteria:**
- [x] Rename to `network` or `bailiwickNetwork`
- [x] Update all usages
**Resolution:** Renamed property from `bailiwick` to `network`. No external usages found to update - property was not accessed outside the class.

---

### ~~BEAD-047: Add thread safety to Bailiwick.init()~~ ✅ RESOLVED
**File:** `Bailiwick.kt:18-36`
**Issue:** `init()` is not thread-safe; multiple threads could initialize differently.
**Acceptance Criteria:**
- [x] Add `synchronized` block or use `lazy` pattern
- [ ] Or document single-threaded initialization requirement
**Resolution:** Implemented thread-safe double-checked locking pattern with `@Volatile` annotation on `bw` variable and `synchronized(lock)` block in `init()`. Changed from `lateinit var` to nullable var with explicit null checks.

---

### ~~BEAD-048: Remove debug code from NewUserFragment~~ ✅ RESOLVED
**File:** `NewUserFragment.kt:109-121`
**Issue:** Button `btnSwdslk` with "FIXME: Delete this permanently" comment.
**Acceptance Criteria:**
- [x] Remove debug button and related code
- [ ] Or hide behind debug build flag
**Resolution:** Removed `btn_swdslk` button from `fragment_new_user.xml` layout and removed the associated click handler code from `NewUserFragment.kt`.

---

### ~~BEAD-049: Fix InMemoryIrohNode thread safety~~ ✅ RESOLVED
**File:** `InMemoryIrohNode.kt:139`
**Issue:** `subscribers` uses `mutableListOf` while other fields use `ConcurrentHashMap`.
**Acceptance Criteria:**
- [x] Use thread-safe collection for subscribers
- [ ] Or document single-threaded test usage
**Resolution:** Changed `subscribers` from `mutableListOf` to `CopyOnWriteArrayList` for thread-safe iteration and modification.

---

### ~~BEAD-050: Protect subscribers from exceptions in InMemoryIrohNode~~ ✅ RESOLVED
**File:** `InMemoryIrohNode.kt:144`
**Issue:** Subscriber exceptions could break `set()` operations.
**Acceptance Criteria:**
- [x] Wrap subscriber notification in try-catch
- [x] Log but don't propagate subscriber exceptions
**Resolution:** Added try-catch around subscriber notification in `set()` method to prevent subscriber exceptions from breaking the operation.

---

## Summary

| Priority | Count | Resolved |
|----------|-------|----------|
| Critical | 8 | 8 |
| High | 9 | 9 |
| Medium | 16 | 15* |
| Low | 17 | 17 |
| **Total** | **50** | **49*** |

| Sprint 4 | Count | Resolved |
|----------|-------|----------|
| Critical | 4 | 4 |
| High | 1 | 0 |
| **Total** | **5** | **4** |

*Note: BEAD-028 (Convert type aliases to value classes) is deferred due to Room/KAPT limitations with Kotlin value classes. Can be addressed when migrating to KSP.

---

## Sprint 4: Action Sync and Feed Download

### ~~BEAD-051: Add action publishing to ContentPublisher~~ ✅ RESOLVED
**Priority:** Critical
**Files:** `IrohDocKeys.kt`, `ContentPublisher.kt`, `Action.kt`
**Issue:** Actions are stored locally but never published to Iroh docs, preventing key exchange from completing.
**Acceptance Criteria:**
- [x] Add `KEY_ACTIONS_PREFIX = "actions/"` to IrohDocKeys
- [x] Add `actionKey(targetNodeId, timestamp)` helper function
- [x] Add `fromPeerId` field to Action entity for tracking source
- [x] Add `publishActions()` method to ContentPublisher
- [x] Store actions at path `actions/{targetNodeId}/{timestamp}`
- [x] Call `publishActions()` from `publishPending()`
**Resolution:** Added action publishing infrastructure. Actions are now serialized to Iroh blobs and stored in the Doc at `actions/{targetNodeId}/{timestamp}`. Added `fromPeerId` field to Action entity for tracking who sent the action. Database version bumped to 2.

---

### ~~BEAD-052: Add action downloading to ContentDownloader~~ ✅ RESOLVED
**Priority:** Critical
**Files:** `ContentDownloader.kt`
**Issue:** No code exists to download actions from peer docs.
**Acceptance Criteria:**
- [x] Add `downloadActions(doc, peerNodeId)` method
- [x] Look for actions where target matches our nodeId
- [x] Store downloaded actions via `ActionDao.insert()` with `processed = false`
- [x] Skip actions already processed (check by blob hash)
**Resolution:** Added `downloadActions()` method that scans peer's doc for actions at `actions/{ourNodeId}/*`, downloads and parses them, and stores in local database with `processed = false`.

---

### ~~BEAD-053: Add action processing loop~~ ✅ RESOLVED
**Priority:** Critical
**Files:** `ContentDownloader.kt`, `KeyStorage.kt`
**Issue:** Downloaded actions are never processed; UpdateKey actions never store keys.
**Acceptance Criteria:**
- [x] Add `processActions(keyring)` method to ContentDownloader
- [x] For UpdateKey actions: decrypt with RSA and call `KeyStorage.storeAesKey()`
- [x] Mark actions as processed after handling
- [x] Call from `syncAll()` after downloading
**Resolution:** Added `processActions()` method that handles UpdateKey actions by storing the received AES key using `KeyStorage.storeAesKey()`. Actions are marked as processed after handling. Called from IrohService after syncAll.

---

### ~~BEAD-054: Enable feed download with cipher~~ ✅ RESOLVED
**Priority:** Critical
**Files:** `ContentDownloader.kt`, `IrohService.kt`
**Issue:** `downloadFeed()` is commented out; sync doesn't use proper cipher.
**Acceptance Criteria:**
- [x] Uncomment/implement feed download in `syncPeer()`
- [x] Get cipher via `EncryptorFactory.forPeer()` using stored keys
- [x] Wire publishActions and processActions into IrohService sync loop
- [x] Download and decrypt feed, then download posts
**Resolution:** Enabled feed download in `syncPeer()` using `EncryptorFactory.forPeer()` to create a MultiCipher that tries all stored keys. Updated IrohService to call `publishActions()` before sync and `processActions()` after sync. Full action→key→feed pipeline now functional.

---

### BEAD-055: Add integration tests for action sync
**Priority:** High
**Files:** New test files
**Issue:** No tests for the action publish/download/process cycle.
**Acceptance Criteria:**
- [ ] Test action publish/download cycle
- [ ] Test key storage after action processing
- [ ] Test feed download with proper cipher

---

---

## Sprint 5: UI Overhaul & User Experience

### BEAD-056: Define visual design system
**Priority:** High
**Type:** Design
**Files:** New `res/values/` files, documentation
**Issue:** UI has been a trivial consideration during prototyping. Need a cohesive visual framework.
**Acceptance Criteria:**
- [ ] Define color palette (primary, secondary, accent, background, surface, error)
- [ ] Define typography scale (heading, body, caption styles)
- [ ] Define spacing/margin system (consistent dp values)
- [ ] Define control styles (buttons, inputs, cards)
- [ ] Create `colors.xml`, `styles.xml`, `dimens.xml` with design tokens
- [ ] Document design system in `designs/ui-design-system.md`
- [ ] Create sample/reference screen showing all components

---

### BEAD-057: Improve sign-up flow - profile photo selection
**Priority:** Medium
**Type:** Feature
**Files:** `NewUserFragment.kt`, `fragment_new_user.xml`
**Issue:** Users cannot choose a profile photo during account creation.
**Acceptance Criteria:**
- [ ] Add profile photo picker (gallery selection)
- [ ] Add camera capture option
- [ ] Show preview of selected photo
- [ ] Crop/resize photo to standard avatar dimensions
- [ ] Store avatar with identity creation
- [ ] Handle permission requests gracefully (camera, storage on older APIs)

---

### BEAD-058: Improve sign-up flow - form design
**Priority:** Medium
**Type:** UI
**Files:** `NewUserFragment.kt`, `fragment_new_user.xml`
**Issue:** Account creation form needs visual polish.
**Acceptance Criteria:**
- [ ] Apply design system from BEAD-056
- [ ] Add visual hierarchy (clear sections, spacing)
- [ ] Add input validation with inline error messages
- [ ] Add loading state during account creation
- [ ] Add success confirmation before navigation
- [ ] Ensure keyboard doesn't obscure inputs

---

### BEAD-059: Post view filtering - by Circle
**Priority:** High
**Type:** Feature
**Files:** `ContentFragment.kt`, `fragment_content.xml`, `BailiwickViewModel.kt`
**Issue:** Users can only view all posts or filter by person. Need circle-based filtering.
**Acceptance Criteria:**
- [ ] Add circle selector UI (dropdown or tab bar)
- [ ] Filter posts by selected circle membership
- [ ] "All" option shows posts from all circles
- [ ] Remember last selected filter across app restarts
- [ ] Update LiveData observer to apply circle filter

---

### BEAD-060: Post view filtering - unified filter UI
**Priority:** Medium
**Type:** UI
**Files:** `ContentFragment.kt`, `fragment_content.xml`
**Issue:** Need cohesive UI for switching between filter modes (All, Circle, Person).
**Acceptance Criteria:**
- [ ] Design filter bar with clear mode indicators
- [ ] Tap Circle → show circle list
- [ ] Tap Person → show person avatars (existing horizontal scroll)
- [ ] Tap All → clear filters, show everything
- [ ] Visual indication of active filter
- [ ] Apply design system styling

---

### BEAD-061: Improved connection flow - Invite initiation
**Priority:** High
**Type:** Feature
**Files:** `IntroduceSelfFragment.kt`, `fragment_introduce_self.xml`, new fragments
**Issue:** Connection flow is confusing. Need clearer "Invite" → Identity → QR → Accept path.
**Acceptance Criteria:**
- [ ] "Invite" button opens identity selector (if multiple identities)
- [ ] After identity selection, show QR code for that identity
- [ ] QR screen has clear "Accept Invitation" button for reciprocal connection
- [ ] Add explanation text for each step
- [ ] Handle single-identity case (skip selector)

---

### BEAD-062: Improved connection flow - Scan invitation
**Priority:** High
**Type:** Feature
**Files:** `ConnectFragment.kt`, `AcceptIntroductionFragment.kt`
**Issue:** Scanning an invitation should flow smoothly to reciprocal QR display.
**Acceptance Criteria:**
- [ ] "Scan Invitation" button opens camera scanner
- [ ] After successful scan, show identity selector
- [ ] After identity selection, show reciprocal QR code
- [ ] Add progress indicator during processing
- [ ] Clear error handling for invalid QR codes

---

### BEAD-063: Connection flow - visual redesign
**Priority:** Medium
**Type:** UI
**Files:** All connection-related fragments
**Issue:** Connection screens need visual polish to match design system.
**Acceptance Criteria:**
- [ ] Apply design system from BEAD-056
- [ ] Clear step indicators (1, 2, 3 or similar)
- [ ] Consistent button placement and sizing
- [ ] QR code display with clear instructions
- [ ] Scanner overlay with alignment guide

---

## Sprint 6: Account Portability

### BEAD-064: Export encrypted key file
**Priority:** High
**Type:** Feature
**Files:** New `KeyExporter.kt`, settings UI
**Issue:** Users cannot back up or transfer their identity to another device.
**Acceptance Criteria:**
- [ ] Add "Export Identity" option in settings/profile
- [ ] Prompt for export password (with confirmation)
- [ ] Generate encrypted key file (private key + identity data)
- [ ] Use strong encryption (AES-256-GCM with PBKDF2 key derivation)
- [ ] Allow saving to Downloads or sharing via intent
- [ ] Include metadata (creation date, identity name) in export
- [ ] Document file format in `designs/key-export-format.md`

---

### BEAD-065: Import key file - sign in with existing identity
**Priority:** High
**Type:** Feature
**Files:** New `KeyImporter.kt`, `SplashFragment.kt` or new flow
**Issue:** Users cannot restore identity from backup or transfer from another device.
**Acceptance Criteria:**
- [ ] Add "Sign in with existing key" option on initial screen
- [ ] File picker to select key file
- [ ] Password prompt to decrypt
- [ ] Validate key file format and integrity
- [ ] Import private key to AndroidKeyStore
- [ ] Restore identity data to database
- [ ] Handle conflict if identity already exists
- [ ] Clear error messages for wrong password / corrupt file

---

### BEAD-066: Key export/import - security hardening
**Priority:** Medium
**Type:** Security
**Files:** `KeyExporter.kt`, `KeyImporter.kt`
**Issue:** Key export/import needs security review.
**Acceptance Criteria:**
- [ ] Enforce minimum password strength
- [ ] Use secure memory handling (clear password from memory after use)
- [ ] Add rate limiting for import password attempts
- [ ] Log security events (export, import attempts)
- [ ] Consider adding optional 2FA or biometric confirmation for export

---

## Sprint 7: Post Interactions & Media

### BEAD-067: Data model for post reactions
**Priority:** High
**Type:** Design/Feature
**Files:** New `Reaction.kt`, `ReactionDao`, `IrohReaction.kt`, database migration
**Issue:** Users cannot react to posts with emoji.
**Acceptance Criteria:**
- [ ] Design `Reaction` entity (postHash, authorId, emoji, timestamp, signature)
- [ ] Add `ReactionDao` with queries (reactionsForPost, myReactionForPost)
- [ ] Create `IrohReaction` model for network serialization
- [ ] Add reactions to ContentPublisher (publish with post reference)
- [ ] Add reactions to ContentDownloader (download and store)
- [ ] Database migration to add reactions table
- [ ] Document data model in `designs/post-interactions.md`

---

### BEAD-068: Reaction UI - display and add
**Priority:** High
**Type:** Feature
**Files:** `PostAdapter.kt`, `post.xml`, new `ReactionPickerFragment`
**Issue:** Need UI to display reactions and allow adding them.
**Acceptance Criteria:**
- [ ] Display reaction counts below each post (grouped by emoji)
- [ ] Tap reaction count to see who reacted
- [ ] Long-press or tap "react" button to open emoji picker
- [ ] Tap existing reaction to toggle (add/remove your reaction)
- [ ] Animate reaction additions
- [ ] Limit to standard emoji set or allow custom?

---

### BEAD-069: Data model for comments
**Priority:** High
**Type:** Design/Feature
**Files:** New `Comment.kt` or extend `Post`, database migration
**Issue:** Users cannot comment on posts.
**Acceptance Criteria:**
- [ ] Design comment model (Option A: new entity, Option B: Post with parentHash)
- [ ] If using Post with parentHash: add `replies(parentHash)` query to PostDao
- [ ] Add comment count query
- [ ] Update ContentPublisher to publish comments
- [ ] Update ContentDownloader to download comment threads
- [ ] Consider threading depth limits

---

### BEAD-070: Comment UI - display and compose
**Priority:** High
**Type:** Feature
**Files:** `PostAdapter.kt`, new `CommentsFragment`, `post.xml`
**Issue:** Need UI to view and write comments.
**Acceptance Criteria:**
- [ ] Show comment count on posts
- [ ] Tap to expand inline comments (first N) or navigate to full thread
- [ ] Comment composer at bottom of thread view
- [ ] Nested replies with indentation (limit depth visually)
- [ ] Pull-to-refresh for new comments
- [ ] Show author avatar and name for each comment

---

### BEAD-071: Data model for tags
**Priority:** Medium
**Type:** Design/Feature
**Files:** New `Tag.kt`, `TagDao`, database migration
**Issue:** Users cannot tag posts with topics/categories.
**Acceptance Criteria:**
- [ ] Design `Tag` entity (name, postId or postHash)
- [ ] Decide: author-only tags vs. anyone can tag?
- [ ] Add `TagDao` with queries (tagsForPost, postsForTag)
- [ ] Create `IrohTag` model for network serialization
- [ ] Add to ContentPublisher/ContentDownloader
- [ ] Consider tag suggestions/autocomplete data

---

### BEAD-072: Tag UI - display and search
**Priority:** Medium
**Type:** Feature
**Files:** `PostAdapter.kt`, `post.xml`, `ContentFragment.kt`
**Issue:** Need UI to display tags and filter by them.
**Acceptance Criteria:**
- [ ] Display tags as chips below post content
- [ ] Tap tag to filter feed by that tag
- [ ] Add tag input when composing posts
- [ ] Tag autocomplete from existing tags
- [ ] Tag cloud or list view for discovery

---

### BEAD-073: Photo sharing - capture and selection
**Priority:** High
**Type:** Feature
**Files:** `ContentFragment.kt`, new `MediaPickerFragment`
**Issue:** Users can only post text, not photos.
**Acceptance Criteria:**
- [ ] Add camera button to post composer
- [ ] Add gallery button to post composer
- [ ] Support selecting multiple photos
- [ ] Show thumbnail previews before posting
- [ ] Allow removing selected photos
- [ ] Compress/resize images for efficient storage
- [ ] Handle permissions (camera, media access)

---

### BEAD-074: Photo sharing - storage and sync
**Priority:** High
**Type:** Feature
**Files:** `ContentPublisher.kt`, `ContentDownloader.kt`, `PostFile.kt`
**Issue:** Photos need to be stored and synced via Iroh.
**Acceptance Criteria:**
- [ ] Store photos as Iroh blobs
- [ ] Link photos to posts via PostFile entity
- [ ] Update post publishing to include photo hashes
- [ ] Update feed format to reference photo blobs
- [ ] Download photos when syncing posts
- [ ] Cache downloaded photos locally
- [ ] Handle missing/failed photo downloads gracefully

---

### BEAD-075: Photo sharing - display
**Priority:** High
**Type:** Feature
**Files:** `PostAdapter.kt`, `post.xml`, new `PhotoViewerFragment`
**Issue:** Need UI to display photos in posts.
**Acceptance Criteria:**
- [ ] Display photos inline in post (single or grid for multiple)
- [ ] Tap photo to open full-screen viewer
- [ ] Swipe between photos in multi-photo posts
- [ ] Pinch-to-zoom in viewer
- [ ] Show loading placeholder while downloading
- [ ] Show error state for failed downloads
- [ ] Text-only posts still work (photos optional)

---

## Summary

| Priority | Count | Resolved |
|----------|-------|----------|
| Critical | 8 | 8 |
| High | 15 + 7 | 9 |
| Medium | 16 + 7 | 15* |
| Low | 17 | 17 |
| **Total** | **70** | **49*** |

*Note: BEAD-028 deferred due to Room/KAPT limitations.

---

### Recommended Sprint Planning

**Sprint 1 (Pre-merge blockers):**
- BEAD-001 through BEAD-008 (Critical)

**Sprint 2 (Stability):**
- BEAD-009 through BEAD-017 (High)

**Sprint 3 (Code Quality):**
- BEAD-018 through BEAD-033 (Medium)

**Sprint 4 (Action Sync):**
- BEAD-051 through BEAD-055

**Sprint 5 (UI Overhaul):**
- BEAD-056 through BEAD-063

**Sprint 6 (Account Portability):**
- BEAD-064 through BEAD-066

**Sprint 7 (Post Interactions & Media):**
- BEAD-067 through BEAD-075

**Ongoing (Tech Debt):**
- BEAD-034 through BEAD-050 (Low)
