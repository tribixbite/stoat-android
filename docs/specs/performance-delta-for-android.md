# Performance Delta Report: Current Workspace

*Analysis of `~/git/for-android/` compared to the original performance audit.*

## All Issues Resolved

Every issue from the original audit and subsequent delta analysis has been addressed
across 4 commits (`1d8e8e5`..`5feab87`).

### Original Audit — Previously Resolved
- **Double Deserialization:** Routes check `res.status` before error decoding.
- **Main Thread Blocking:** WS ping loop moved to IO coroutine, no more `runBlocking`.

### Commit 1 (`1d8e8e5`) — Pure Optimizations
- **Bulk Frame Inefficiency:** Extract type from `JsonObject` via `jsonPrimitive` instead of re-serializing through `AnyFrame`. Single `toString()` per sub-frame.
- **Redundant User Cache Logic:** `ChannelScreenViewModel.kt` now guards `fetchMember` with `hasMember()` check, skipping network calls for already-cached authors.
- **Glide Memory:** `RemoteImage.kt` now calls `.override(width, height)` to constrain decoded bitmap size.

### Commit 2 (`514bbff`) — Concurrency & Buffer
- **Sequential Login:** `loginAs()` now runs `startSocketOps()` and `unreads.sync()` in parallel via `coroutineScope { launch {} }` after `fetchSelf()` completes.
- **Unbounded WS Frame Buffer:** `wsFrameChannel` bounded to 1000 entries with `DROP_OLDEST` overflow policy.

### Commit 3 (`6e822af`) — DB Transactions, LRU Cache, Member List
- **Non-Transactional Ready Frame:** All 4 Ready frame DB write loops wrapped in a single `database.transaction {}` block.
- **Unbounded messageCache:** Replaced with `SnapshotStateLruMap(maxSize = 2000)` — LRU eviction backed by `LinkedHashMap(accessOrder=true)` with Compose snapshot versioning.
- **MemberList Spam:** Replaced `snapshotFlow`-based fetch (fired on every `userCache` mutation) with single `LaunchedEffect(serverId, channelId)` per sheet open.

### Commit 4 (`5feab87`) — Error Handling
- **Broken Error Handling in Routes:** Added HTTP status checks to ~30 functions across 12 files. Critical correctness fix: `kickMember`, `banMember`, `removeAllReactions`, `pinMessage`, `unpinMessage`, `bulkDeleteMessages` now validate server response *before* mutating local caches.
- `ackChannel` intentionally left unchecked (high-volume, non-critical).

### Issue Not Present
- **Wasteful Side-Effect Iteration (`.map` for side effects):** Investigation confirmed all `.map` calls in `RealtimeSocket.kt` already use return values; no `List<Unit>` allocations exist.

## Remaining Considerations (Non-Critical)
- `userCache` remains unbounded (`mutableStateMapOf`). Unlike messages, users are finite per session and don't grow unboundedly in practice. Consider LRU-bounding if memory profiling shows growth in very large servers.
- `Unreads.hasAnyUnreads()` and `countChannelsWithUnreads()` iterate entire `channelCache`. Acceptable for current server sizes but could be optimized with a derived counter if needed.
