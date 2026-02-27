# Performance Delta Report: Current Workspace

*Analysis of `~/git/for-android/` — last updated 2026-02-27.*

## Status: Critical Issues Resolved, Medium-Term Items Remain

All CRITICAL and HIGH items from the original audit are fixed. Two MEDIUM-term architectural improvements remain for future work.

### Resolved Issues (Audit Fixes 1-9)

1. **Double Deserialization** — API routes check HTTP status before deserializing error bodies. ~50 instances fixed across 15 files.
2. **Parallel Login** — `StoatAPI.kt:loginAs` runs `startSocketOps()` and `unreads.sync()` concurrently via `coroutineScope`.
3. **Transactional Ready Frame** — `RealtimeSocket.kt` wraps all Ready frame DB writes in a single SQLDelight transaction.
4. **Redundant Member Fetches** — `ChannelScreenViewModel.kt` guards `fetchMember` with cache check.
5. **Bulk Frame Optimization** — `RealtimeSocket.kt` extracts sub-frame types directly from `JsonObject` instead of re-serializing.
6. **Bounded WS Frame Buffer** — `wsFrameChannel` bounded to 1000 items with `DROP_OLDEST` overflow.
7. **Glide Memory** — `RemoteImage.kt` uses `.override(width, height)` to constrain bitmap decoding.
8. **Member List Spam** — `MemberListSheet.kt` fetches once per sheet open, no longer observes entire userCache.
9. **Bounded Caches** — `messageCache` (2000), `userCache` (2000), `emojiCache` (3000) all use `SnapshotStateLruMap`. `serverCache`/`channelCache`/`voiceStateCache` left as `mutableStateMapOf` (naturally bounded by joined servers).

### Additional Fixes (Feb 27, 2026)

10. **ANR: UserOverview.kt** — `runBlocking(Dispatchers.IO)` in `LaunchedEffect` replaced with `withContext(Dispatchers.IO)`.
11. **ANR: SettingsScreen.kt** — `runBlocking` logout replaced with `viewModelScope.launch`. Network calls fire-and-forget on IO, local cleanup async on Main.
12. **ConversationsScreen placeholder** — `items(1000)` fake text replaced with real DM/group channel list using `dmAbleChannels`, with avatars, presence indicators, unread badges, and message previews.

### Remaining (Medium-Term, Not Blocking)

| # | Item | Impact | Effort | Notes |
|---|------|--------|--------|-------|
| M1 | **Incremental `updateItems()` for single messages** | -50% recomposition on message receive | 3/5 | Currently rebuilds full grouped list on every message. Single-message prepend with local grouping would halve work. |
| M2 | **Replace `mutableStateMapOf` with `ConcurrentHashMap` + Flow** | Eliminate recomposition storms from global snapshot maps | 4/5 | High-effort architectural change. Current LRU bounds mitigate the worst memory issues but snapshot notification overhead remains. |

### Non-Performance TODOs Found

| File | Issue |
|------|-------|
| `ChannelRegistrator.kt:10` | Incomplete notification channel groups — only 2 of N registered |
| `VoiceSheet.kt:274,302` | Camera track + screen share blocked on LiveKit Compose API stabilization |
| `ChannelScreen.kt:834` | Context menu for cancel-send on pending/failed messages |
| `DiscordImportScreen.kt:69` | Discord bot OAuth URL hardcoded (move to build config) |
| `RegularMessage.kt:216` | `onSizeChanged` workaround for LazyColumn intrinsic sizing |

## Conclusion

The fork is substantially more optimized than the upstream codebase. All critical cold-start, memory, and ANR issues are resolved. The two remaining medium-term items (incremental message list updates, Flow-based caches) are architectural improvements that would further reduce jank in high-traffic servers but are not blocking for normal use.
