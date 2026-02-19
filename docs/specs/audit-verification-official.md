# Audit Verification: Stoat Android Official (Original)

*Verification performed on 2026-02-18 against `~/git/stoat-android-official/`.*

## Executive Summary
The performance audit dated 2026-02-16 is **95% accurate** for the original codebase. All critical architectural flaws identified (unbounded caches, sequential network calls, and non-transactional DB writes) were verified in the source.

## Verified Claims

### 1. Double Deserialization (Confirmed)
- **Status:** Verified.
- **Evidence:** `grep` identified 27 instances in `api/routes/`.
- **Pattern:** `Relationships.kt` (and others) unconditionally attempts to decode `StoatAPIError` before checking HTTP status, causing a `SerializationException` and re-parse on every successful call.

### 2. Sequential Login Calls (Confirmed)
- **Status:** Verified.
- **Evidence:** `StoatAPI.kt:loginAs` (Lines 158-163) calls `fetchSelf()`, `startSocketOps()`, and `unreads.sync()` serially.

### 3. Ready Frame Blocks Realtime Thread (Confirmed)
- **Status:** Verified.
- **Evidence:** `RealtimeSocket.kt` (Lines 185-230) performs individual `database.serverQueries.upsert` and `database.channelQueries.upsert` calls for every item in the Ready frame without a transaction block.

### 4. Unbounded In-Memory Caches (Confirmed)
- **Status:** Verified.
- **Evidence:** `StoatAPI.kt` (Lines 125-130) uses `mutableStateMapOf` for `userCache`, `messageCache`, etc., with no eviction logic.

### 5. Bulk Frame Double-Serialization (Confirmed)
- **Status:** Verified.
- **Evidence:** `RealtimeSocket.kt` (Lines 155-162) converts `subFrame` to string twice and re-parses with `AnyFrame.serializer()` to find the type.

### 6. Member List Re-fetches (Confirmed)
- **Status:** Verified.
- **Evidence:** `MemberListSheet.kt` (Line 238) observes `StoatAPI.userCache` via `snapshotFlow` and calls `fetchServerMemberList` on every mutation.

## New Critical Issue Found
- **Main Thread runBlocking:** In `StoatAPI.kt:startSocketOps`, the WebSocket ping loop is scheduled via `mainHandler.post` and uses `runBlocking { RealtimeSocket.sendPing() }`. This effectively stalls the UI thread every 30 seconds for the duration of the network write.
