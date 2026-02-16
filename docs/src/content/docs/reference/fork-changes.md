---
title: Fork Changes — Features & Fixes Not in Upstream
description: Features, bug fixes, and improvements in the tribixbite fork that are not present in the upstream Stoat/Revolt Android client.
template: doc
---

This page documents all features, bug fixes, and improvements added in the [tribixbite/stoat-android](https://github.com/tribixbite/stoat-android) fork that are **not present** in the upstream [stoatchat/for-android](https://github.com/stoatchat/for-android) repository.

## New Features

### Message Search (Full UI + API)
Channel and server-wide message search with comprehensive filtering, not available in upstream.

- **Search API route** (`POST /channels/{channelId}/search`) with full parameter support: `query`, `limit`, `before`, `after`, `sort`, `include_users`, `pinned`
- **Server-wide search** — iterates all text channels in a server, aggregates results with progress indicator showing current channel (X/Y), error count for inaccessible channels. Accessible from server context sheet (⋮ menu → "Search Server")
- **Search UI screen** with text input, debounced queries (400ms), and paginated results
- **Search filters**: sort order (Relevance/Latest/Oldest), pinned-only mode, date range (before/after), mention filter, attachment filter (client-side)
- **Collapsible filter panel** with "Clear All" button and result count display
- **Infinite scroll** pagination using cursor-based `before` parameter
- **Search result navigation**: tap a result to jump to the message in channel context
- **Channel labels** on server search results showing which channel each message is from
- MongoDB `$text` search syntax support: OR matching, `"exact phrases"`, `-negation`, stemming

### Moderation Tools
Server moderation features with full UI, only API stubs existed upstream.

- **Kick member** with confirmation dialog — `DELETE /servers/{id}/members/{userId}`
- **Ban member** with optional reason field — `PUT /servers/{id}/bans/{userId}`
- **Unban member** — `DELETE /servers/{id}/bans/{userId}`
- **Fetch ban list** — `GET /servers/{id}/bans`
- **Pin/unpin messages** — `POST /channels/{id}/messages/{msg}/pin` and `DELETE .../pin`
- **Bulk delete messages** API route — `DELETE /channels/{id}/messages/bulk`

### Server Management (Full UI + API)
Complete server administration screens with permission-gated access.

- **Server Settings screen** — edit name, description, icon upload (auto-crop to 1:1, resize to 1024px, WebP), banner upload (auto-crop to 5:2, resize to 2048px, WebP) via InlineMediaPicker with progress bar and Autumn upload
- **Role Management screen** — create, edit (name, colour with hex preview, hoist toggle, rank editing), delete roles
- **Ban Management screen** — view ban list with reasons, unban with confirmation
- **Create Channel screen** — Text/Voice type selection, name, description, NSFW toggle (passes `nsfw` param to API)
- **Channel Permissions screen** — full per-role permission overrides with tri-state toggles (Allow/Neutral/Deny), add role dialog, save to API
- **Member nickname edit** — edit own or others' nicknames (permission-gated)
- **Role assignment dialog** — toggle roles per member with visual checkmarks
- **Server Settings entry point** — accessible from server context sheet (long-press server)
- **14 API routes**: `PATCH /servers/{id}`, `POST/PATCH/DELETE roles`, `PATCH /servers/{id}/roles/ranks`, `GET/DELETE bans`, `PATCH members`, `POST channels`, `PUT /channels/{id}/permissions/{roleId}`, `PUT /channels/{id}/permissions/default`, plus `uploadToAutumn()` for icons and banners

### Permissions Editor
Full default and per-role permission editor matching the web client UI, for both server-level and channel-level permissions.

- **Default Permissions screen** — checkbox toggles for all 32 permission bits on the default role
- **Role Permissions screen** — tri-state segmented buttons (Allow / Neutral / Deny) per permission
- **Channel Permissions screen** — per-role permission overrides for individual channels, with add-role dialog and save to API
- **5 categories**: Admin (5), Members (8), Channels (6), Messaging (6), Voice (7)
- **API routes**: `PUT /servers/{id}/permissions/default`, `PUT /servers/{id}/permissions/{roleId}`, `PUT /channels/{id}/permissions/{roleId}`, `PUT /channels/{id}/permissions/default`

### Push Notification Relay via stoatcord-bot
Working push notifications via bot relay, bypassing the unconfigured Stoat backend FCM.

- **Push mode selector** in Notification Settings — radio group with four modes:
  - **Direct (Bot Relay)**: FCM via stoatcord-bot relay server (default, recommended)
  - **UnifiedPush**: Firebase-free push via distributors like ntfy — no Google dependency or signing key requirements
  - **Backend (Stoat Server)**: legacy direct path (disabled — backend FCM unconfigured)
  - **Off**: disable push notifications entirely
- **UnifiedPush support** (`org.unifiedpush.android:connector:3.2.0`):
  - `StoatPushService` extends UP `PushService` with full rich notification display (avatars, conversation style, bubbles)
  - Distributor picker UI — lists installed UP distributors with friendly app names
  - Endpoint registration with bot relay server (WebPush encryption when keys available, plain POST fallback for ntfy)
  - Registration status display with endpoint URL preview and error reporting
  - Handles `onNewEndpoint`, `onMessage`, `onUnregistered`, `onRegistrationFailed`
- **Bot relay architecture**: stoatcord-bot receives all Stoat WebSocket events, filters for mentions/DMs, sends FCM data-only messages or plain HTTP POST (UnifiedPush) to registered devices
- **Bot URL configuration** — editable text field for self-hosted bot instances (shown for both Bot FCM and UnifiedPush modes)
- **Registration status** — shows connected/error state with retry button
- **Seamless notification pipeline** — bot sends JSON payload matching `HandlerService`/`StoatPushService` format, so rich notifications (avatars, conversation style, bubbles, reply actions) work across both FCM and UP paths
- **Automatic re-registration** on app resume and FCM token refresh
- **Bot HTTP API** endpoints: `POST /api/push/register`, `DELETE /api/push/unregister`, `GET /api/push/status`
- **PushManager** — dedicated HTTP client for bot push API communication
- **PushMode enum** — type-safe push mode selection with KVStorage persistence

### Notification Controls
Granular notification management beyond upstream.

- **Mute/Unmute server** — toggle from server context sheet, synced to backend
- **Mute/Unmute channel** — toggle from channel context sheet, synced to backend
- **Notification filtering** — HandlerService checks mute state before displaying
- **Notification Settings screen** — permission status, push provider selection, FCM registration status with retry, muted server/channel lists (showing names from cache), unmute buttons, reset
- **Placeholder detection** — detects placeholder `google-services.json` at runtime and shows "Not available" instead of misleading retry loop
- **Notification tap → channel navigation** — tapping a notification opens the app directly to the relevant channel instead of just launching the home screen. Handles both cold start (via kvStorage destination) and foreground (via ActionChannel) cases. (`e48f41d`)

### Termux ARM64 Build System
Complete native Android build toolchain for ARM64 devices — enables building the app directly on Android phones.

- Custom `build-and-install.sh` script handling ARM64 AAPT2, JVM tuning, and ADB install
- x86\_64 AAPT2 wrapper using `proot` + `qemu-x86_64` for SDK 36 resource compilation
- Automatic ADB wireless device discovery and APK installation
- JVM memory limits tuned for mobile device constraints

### CI/CD Pipeline
Automated build and release system.

- GitHub Actions workflow builds debug APK on every push to `dev`
- Automatic GitHub Release creation with downloadable APK artifacts
- Placeholder `google-services.json` generation for CI builds
- SDK 36 + build-tools 35.0.0 setup

### Image Processing Pipeline
Automatic image optimization for all uploads, ensuring compatibility with Autumn file server limits.

- **ImageProcessor utility** (`ImageProcessor.kt`) — handles complete image preparation pipeline
- **EXIF rotation** — reads EXIF orientation tag and applies rotation/flip before processing (many phone cameras embed rotation in metadata rather than pixel data)
- **Center-crop to target aspect ratio** — icons/avatars crop to 1:1 square, banners crop to 5:2 wide, emojis preserve original aspect
- **Resize to max dimensions** — enforces per-type limits (avatars/icons 1024px, banners 2048px, emojis 512px). No upscaling
- **WebP compression** — all uploads converted to WebP lossy format. Iterative quality reduction from 90 to 5 until within file size limit
- **File size enforcement** — avatars 4MB, icons 2.5MB, banners 6MB, emojis 500KB
- **Downsampled decoding** — uses `BitmapFactory.Options.inSampleSize` for memory-safe decoding of large images. Threshold at 1.5x target dimension prevents OOM on 4000+ pixel camera photos
- **OOM protection** — catches `Throwable` (not just `Exception`) to handle `OutOfMemoryError` from large bitmap processing. Intermediate bitmaps recycled immediately after each step to minimize peak memory
- **Safe center-crop** — crop region bounds are coerced to prevent `IllegalArgumentException` when computed dimensions exceed bitmap bounds
- **Wired into**: server icon/banner upload (ServerSettingsScreen), emoji upload (EmojiManagementScreen), bot avatar upload (BotManagementScreen), profile avatar/background (ProfileSettingsScreen), channel icon (ChannelSettingsOverview)

### Bot Description & Avatar
Bot profile editing using the bot's own authentication token.

- **Description field** — text editor for bot bio/description, saved via `PATCH /users/@me` using the bot's token
- **Avatar picker** — InlineMediaPicker with ImageProcessor pipeline, uploads to `autumn/avatars`, then sets via bot-authenticated `PATCH /users/@me`
- **Avatar removal** — remove button sends `remove: ["Avatar"]` to clear the bot's avatar
- **Why bot token?** — The `PATCH /bots/{id}` endpoint only supports name/public/analytics/interactions_url. Avatar and description are on the bot's User object, requiring the bot's own token to `PATCH /users/@me`

## Bug Fixes

### WebSocket Reconnection
- **Fixed background disconnect**: app now properly reconnects WebSocket when resuming from background after Android kills the connection
- Reconnection uses exponential backoff to avoid hammering the server

### Search Reliability
- **Fixed socket timeout**: increased from 30s to 60s for large channel searches that take 3-30s+
- **Request timeout cap** at 75s prevents Ktor's 5x retry from hanging for 5+ minutes
- **Fixed wildcard/empty query bug** that sent malformed requests
- **Fixed serialization errors** when API returns bare `Message[]` vs `{messages, users, members}` response format
- **Added HttpTimeout per-request overrides** since search is much slower than other API calls

### Notification Fixes
- **FCM onNewToken ANR fix**: replaced `runBlocking` with `CoroutineScope(SupervisorJob() + Dispatchers.IO)` to prevent app-not-responding on token refresh
- **FCM error handling**: `subscribePush()` no longer silently swallows registration failures
- **Crash fix**: bitmap loading in `HandlerService` wrapped with timeout and fallback to default icon
- **Error message display**: push notification errors now surface to the user instead of silent failure
- **Manage Notifications button** added to settings for direct access to system notification settings
- **PushManager timeout**: added HttpTimeout (5s connect, 10s request/socket) to prevent indefinite hangs to unreachable bot URLs
- **Notification Settings threading**: moved IO operations to `Dispatchers.IO` via `withContext` while keeping Compose state updates on Main thread (was crashing from setting `mutableStateOf` on IO thread)

### Navigation ANR Fix
- **ChatRouter ActionChannel infinite loop**: the `while(true)` action handler was catching `CancellationException` (specifically `LeftCompositionCancellationException`) instead of rethrowing it. When navigating away from chat, the cancelled coroutine would enter a tight infinite loop of catching cancellation exceptions on the main thread, blocking UI for 10+ seconds and triggering Android's ANR dialog. Fixed by rethrowing `CancellationException` before the generic catch block.

### Account Management
Full account settings screen with API integration.

- **Account info display** — `GET /auth/account/` with email, MFA status
- **Change email** — `PATCH /auth/account/change/email` with password confirmation
- **Change password** — `PATCH /auth/account/change/password` with confirm + mismatch check
- **Disable account** — `POST /auth/account/disable` with confirmation dialog
- **Delete account** — `POST /auth/account/delete` with danger confirmation
- **Resend verification** — `POST /auth/account/reverify`
- **Session management** — list, rename, revoke sessions via `GET/PATCH/DELETE /auth/session`

### Custom Emoji Management
Server emoji administration with upload and delete support.

- **Emoji list** — shows all custom emoji for a server from cache, with names and creator info
- **Upload emoji** — pick image, auto-process (resize to 512px, compress as WebP within 500KB), preview with dimensions/size display, upload to `autumn/emojis`, create via `PUT /custom/emoji/{id}`
- **Delete emoji** — `DELETE /custom/emoji/{id}` with confirmation dialog
- **Permission-gated** — requires ManageCustomisation or ManageServer

### Invite Management
Server invite administration with list, copy, and delete.

- **List invites** — `GET /servers/{id}/invites` showing invite links, creator, channel
- **Copy invite link** — tap to copy `stt.gg/{code}` to clipboard
- **Delete invite** — `DELETE /invites/{code}` with confirmation dialog
- **Create invite** — `POST /channels/{id}/invites`

### Mutual Friends & Servers
Shows shared connections in member context sheets.

- **Mutual friends** — `GET /users/{id}/mutual` with resolved friend names from cache
- **Mutual servers** — shows shared server names
- **Non-intrusive** — displays between moderation actions and copy ID, only for other users

### MFA / TOTP Management
Full MFA setup and recovery code management, not available in upstream.

- **MFA Setup screen** — accessible from Account Settings, shows TOTP + recovery status
- **Enable TOTP** — multi-step dialog: password → secret display (copyable) → 6-digit verification → done
- **Disable TOTP** — password confirmation → MFA ticket → disable
- **Recovery codes** — view existing codes or regenerate new ones, copy-all button
- **7 API endpoints**: `PUT /auth/mfa/ticket`, `POST/PUT/DELETE /auth/mfa/totp`, `POST/PATCH /auth/mfa/recovery`

### Session Management
Active session management with full API integration.

- **List sessions** — `GET /auth/session/all`
- **Rename session** — `PATCH /auth/session/{id}` with friendly name
- **Revoke session** — `DELETE /auth/session/{id}` with confirmation
- **Revoke all others** — `DELETE /auth/session/all` danger zone action

### Member Search
Client-side member search in member list sheet.

- **Search field** at top of member list sheet
- Filters by **username, display name, and server nickname**
- Clear button to reset search
- Works for both server members and group DM participants

### Content Moderation
Advanced message management for moderators.

- **Remove all reactions** — `DELETE /channels/{id}/messages/{msg}/reactions`
- **Bulk delete messages** — dialog with preset counts (5/10/25/50/100), operates on cached messages
- Both gated by ManageMessages permission

### Collapsible Channel Categories (Upstream #51)
Tap category headers in the channel side drawer to collapse/expand.

- **Animated chevron indicator** — rotates 90° between collapsed (→) and expanded (↓) states
- **Chevron direction fix** — corrected rotation values so → means collapsed and ↓ means expanded (was inverted)
- **Client-side state** — collapse state tracked per-category in Compose state map
- **Filtered channel list** — collapsed categories hide their channels from the flat list

### Jump to Reply (Upstream #23)
Tap a reply quote to scroll to the original message in the chat.

- **ActionChannel.ScrollToMessage** action for cross-component communication
- **Animated scroll** to target message in LazyColumn
- Works for all loaded messages; messages not in view are scrolled to if in cache

### Send Button Debounce (Upstream #33/#30)
Prevents duplicate message sends from rapid tapping.

- **`isSendingMessage` flag** in ChannelScreenViewModel
- Guard at top of `sendPendingMessage()` rejects rapid taps
- Flag cleared in `finally` block after send completes or fails

### Navigation Restoration (Upstream #41/#21)
- **Fixed DM/Saved Notes stuck loading** — when app restarts on a DM or saved notes channel, the channel cache is empty until WebSocket Ready frame arrives
- `switchChannel()` now watches the cache with `snapshotFlow` and retries loading once the channel appears
- **30-second timeout** on cache watch — if the channel never appears (deleted, network issue), shows error with retry instead of infinite shimmer
- `retryLoadMessages()` handles both cached and pending channel states
- Previously left the screen permanently in Loading state

### Message Send Race Condition (Upstream #17)
- **Fixed channel ID capture** — `sendPendingMessage()` captured `channel?.id` inside async `viewModelScope.launch{}` block, but channel could change if user switched channels during slow attachment upload
- Channel ID now captured synchronously before the launch, preventing messages from being sent to the wrong channel

### DM Mention Autocomplete (Upstream #52)
- **Fixed DM/Group DM mentions** — autocomplete was blocked in DMs by an unnecessary `serverId != null` check; DM channels always have null serverId
- `Autocomplete.userOrRole()` already handled null serverId correctly, so the guard was simply removed

### Friends Screen Reactivity (Upstream #39)
- **Fixed friends list not updating** — friends screen called `FriendRequests.getXxx()` multiple times per section, creating different list snapshots between header count and item access
- Lists now computed once per recomposition above the LazyColumn, ensuring consistent data
- Added stable `key` lambdas to LazyColumn items for proper item diffing and animation
- Reads from `mutableStateMapOf` are automatically tracked by Compose — list updates reactively when `userCache` changes

### Blocked User Swipe Reply (Upstream #11)
- **Blocked swipe-to-reply** — `canReply` was hardcoded to `true` in `RegularMessage`
- Now checks `StoatAPI.userCache[author]?.relationship != "Blocked"` before enabling swipe reply gesture

### Swipe-to-Reply vs Code Block Scroll (Upstream #14)
- **Fixed gesture conflict** — swiping horizontally on a code block triggered swipe-to-reply instead of scrolling the code content
- Changed `supportSwipeReply` from `PointerEventPass.Main` to `PointerEventPass.Final` so child scrollable elements process events first
- Filters out consumed pointer changes before passing to swipe handler

### Duplicate Reply Banners (Upstream #57)
- **Fixed duplicate reply banners** — selecting "Reply" on the same message multiple times stacked multiple reply banners
- Both context menu and `ReplyToMessageWithContent` paths called `draftReplyTo.add()` directly, bypassing the duplicate check in `addReplyTo()`
- Now all reply paths use `addReplyTo()` which enforces both deduplication and max-5 limit

### Double-Tap Duplicate Requests (Upstream #30)
- **Comprehensive double-tap protection** across all critical actions:
  - **Server creation** (AddServerSheet) — `isCreating` guard prevents duplicate servers
  - **Login** (LoginScreen) — `isLoggingIn` guard prevents duplicate auth requests; also fixed `setSessionId(token)` → `setSessionId(id)` bug
  - **Registration** (RegisterDetailsScreen) — `isRegistering` guard prevents duplicate signup emails
  - **Group creation** (CreateGroupScreen) — `isCreating` guard prevents duplicate group DMs
  - **MFA TOTP** (MfaScreen) — `isSubmitting` guard prevents duplicate auth, auto-submit on 6 digits
- All guarded buttons show `CircularProgressIndicator` during API call and are disabled
- Each screen properly catches `Exception` (not just `Error`) for broader error handling

### Early Access Sheet Stuck UI (Upstream #63)
- **Fixed zombie scrim blocking all interaction** — the "Welcome to Early Access" ModalBottomSheet had `onDismissRequest = {}` (empty), intending to force button-only dismissal
- On some devices, system back gesture could visually dismiss the sheet while `showEarlyAccessSpark` remained `true`, leaving an invisible scrim blocking all touch input
- Now `onDismissRequest` properly calls `dismissEarlyAccessSpark()` so back press works as expected

### Send Button Active During Upload (Upstream #33)
- **Send button now shows `CircularProgressIndicator`** while message is being uploaded/sent
- Users can still type in the message field, but can't double-send
- Matches the loading indicator pattern used across all other action buttons

### @Mention Autocomplete in DMs (Upstream #52)
- **Fixed @mention autocomplete** to work in DMs and group DMs
- Now matches on both `username` and `displayName` (previously only `username`)
- Typing bare `@` in a DM/group shows all participants as suggestions
- Same fix applied to Saved Messages channel

### Friends List Not Refreshing (Upstream #39)
- **All relationship API responses now update the local user cache** immediately
- Previously, `friendUser()`, `acceptFriendRequest()`, `unfriendUser()`, `blockUser()`, `unblockUser()` discarded the API response — the UI only updated when a WebSocket event arrived (or not at all if the socket was slow)
- Now parses the response `User` object and writes to `StoatAPI.userCache`, triggering instant Compose recomposition

### Suspended User Login Recovery (Upstream #27)
- **App no longer gets stuck after platform suspension expires** — `checkSessionToken()` now validates `UserFlags` after `fetchSelf()`, rejecting Suspended/Deleted/Banned accounts instead of silently proceeding to a broken login state
- **WebSocket Error frame now handled** — server-sent error frames (e.g., auth rejection) close the socket gracefully instead of being silently logged
- **Logout now clears all state** — `logOut()` calls `StoatAPI.logout()` to clear in-memory caches AND persistent database, not just kvStorage tokens. Previously, stale suspended user data survived across restarts.

### WebSocket Real-Time Cache Sync
- **ChannelGroupJoin/Leave** — group DM recipient list updates in real-time when members join or leave (was silently dropped, requiring app restart to see changes)
- **EmojiCreate/Delete** — custom emoji cache syncs when emojis are added or removed server-side (was silently dropped)
- **MessageRemoveReaction** — clears all reactions for a specific emoji when a moderator bulk-removes them (was silently dropped)

### System Message Visual Distinction
- **Type-specific background and foreground colors** for system messages:
  - Green tint for joins and user additions
  - Amber tint for leaves and removals
  - Red tint for bans and kicks
  - Blue tint (Material primary) for channel edits
- Previously all system messages used a single generic color

### Spoiler Text (Upstream #54)
- **Implemented `||spoiler||` syntax** — double-pipe delimiters render as hidden text
- New `SpoilerParser` sequential parser recognizes `||..||` in the markdown pipeline
- Hidden state: text foreground matches dark background (invisible)
- Tap to reveal: toggles spoiler visibility, re-tapping hides again
- Per-spoiler state tracked independently in each text block

### Copy Text/ID Outside Share (Upstream #40)
- **Added "Copy" and "Copy ID" buttons** to the top level of the message context sheet
- Previously buried inside the "Share" sub-menu requiring two taps
- "Copy" copies message text content, only shown when message has text
- "Copy ID" copies message ULID, always available

### Long-Press Server Icon Context Menu (Upstream #20)
- **Added long-press to server icons** in the sidebar drawer
- Opens the existing ServerContextSheet (Mark Read, Mute, Settings, Leave)
- Changed from `.clickable` to `.combinedClickable`, matching the pattern used for channel items

### Profile Card Tap-to-Copy (Upstream #19)
- **Fixed tap-to-copy** on user profile cards — previously failed silently
- Added try-catch around `copyCard()` so errors are displayed instead of swallowed
- Always show "Copied" toast (system clipboard notification doesn't work well for image URIs on Android 13+)
- Always show Share/Copy buttons (previously hidden on Android 13+, leaving only invisible tap)

### Discover Tab
- **Fixed server clicks** — discover page links to `app.revolt.chat/invite/CODE` which was silently blocked; now handles all known Revolt/Stoat domains (stoat.chat, stt.gg, rvlt.gg, app.revolt.chat)

### Mark as Unread
- **Implemented mark-as-unread** — replaced "coming soon" toast with working implementation
- Generates synthetic ULID just before the selected message, acks channel to that ID
- Channel appears unread from the selected message onwards in the channel list

### Search Result Navigation (Jump to Message)
- **Fixed search result click** — previously just closed the search screen; now navigates to the correct channel and scrolls to the target message
- Uses the Revolt API's `nearby` parameter to fetch messages centered on the target when it isn't in the loaded set
- New `SwitchChannelAndScrollToMessage` action enables cross-screen channel-switch-and-scroll in one step
- 5-second timeout with graceful fallback for deleted/unavailable messages
- Works for both single-channel search and server-wide search results

### Inline Text File Preview
- **Text attachments now show an inline code preview** — first 12 lines displayed in a monospace code block with horizontal scroll, directly in the message
- File header shows filename and size above the preview
- Files over 100KB skip the preview and show the standard download card
- Falls back to the standard file attachment card if the fetch fails
- Replaces the generic file icon card that previously showed no content preview

### Upload Error Display
- **Fixed silent upload failure** — attachment upload errors now display an error banner above the message field instead of failing silently
- Tap to dismiss the error banner

### User Info Sheet Cache Miss
- **Fixed "user not found"** for users not yet in local cache when opening their profile
- Now fetches from `GET /users/{id}` on cache miss, populating the cache for subsequent access

### README Dead Links (Upstream #42)
- Replaced broken `revoltchat.github.io` links with fork documentation
- Updated development setup instructions with Termux ARM64 build info

### API Robustness
- **HTTP status code checks** on all API responses (previously some routes ignored error status)
- **Pin endpoint method fix**: corrected from `PUT` to `POST` per OpenAPI spec
- **WebP upload support**: fixed content type detection for WebP image uploads
- **Search submit button** properly triggers search on keyboard action
- **Reduced HTTP retry** from 5 to 2 for server errors: 502 Bad Gateway caused ~62s exponential backoff hangs
- **Autumn upload double body read** — `uploadToAutumn()` was calling `bodyAsText()` twice (once for success, once for error). Now reads body once and checks status code
- **ActionChannel crash protection** — receive loop in ChatRouterScreen now wrapped in try-catch; a single action handler exception no longer kills the entire action dispatch loop
- **MemberListSheet off-main-thread** — member categorization/sorting for large servers (1000+ members) moved to `Dispatchers.Default` to avoid main thread blocking
- **Safe attachment aspect ratio** — image/video attachments with missing metadata dimensions no longer crash with `NullPointerException`; defaults to 1:1 aspect ratio via `safeAspectRatio()` helper instead of 4 chained `!!` assertions

## Performance Optimizations

### Eliminate Double Deserialization (All API Routes)
Every API route was parsing responses twice — first attempting `decodeFromString(StoatAPIError)` which throws `SerializationException` on every successful response, then parsing the actual type. Fixed across 16 route files to check HTTP status code first, only parsing error body on non-2xx responses. Eliminates ~50-100ms cumulative overhead per screen from redundant parse + exception throw/catch on every API call.

- **Files fixed**: User.kt, Server.kt, Channel.kt, Bots.kt, Webhooks.kt, DirectMessaging.kt, Relationships.kt, GroupDM.kt, Voice.kt, Reporting.kt, Login.kt, Register.kt, Invites.kt, Onboarding.kt

### Parallel Startup Sequence
App launch made 4 sequential API calls: `canReachStoat()` → `checkSessionToken()` → `needsOnboarding()` → `loginAs()`. Pre-startup also ran sequentially: `Experiments.hydrate()` → `healthCheck()` → `updateGeoState()`.

- **Health check + geo update** now run in parallel via `coroutineScope { launch {} launch {} }`
- **Reachability + token validation** now run in parallel via `coroutineScope { async {} async {} }`
- **Estimated savings**: 200-400ms at app launch

### Fix runBlocking on Main Thread
WebSocket ping used `mainHandler.post(Runnable { runBlocking { RealtimeSocket.sendPing() } })` every 30 seconds, blocking the main thread and causing UI jank. Replaced with `CoroutineScope(Dispatchers.IO)` coroutine loop.

### Parallel Webhook Fetches
`WebhookManagementScreen` looped through text channels sequentially, making N serial API calls (~200ms each). Replaced with `async/awaitAll` pattern — all channels fetched concurrently. Estimated savings: 800-1800ms on servers with many channels.

### Remove Redundant Member Fetch
`ChannelScreenViewModel.switchChannel()` called `ensureSelfHasMember()` then `denyMessageFieldIfNeeded()` sequentially, but the latter already fetches the member internally. Removed the redundant call, saving ~200ms per channel switch.

### Parallel User Info Sheet Fetches
User fetch and profile fetch in `UserInfoSheet` ran sequentially. Wrapped in `coroutineScope { launch {} launch {} }` to run in parallel. Saves ~200ms on user info sheet open.

### Chucker Interceptor Optimization
`ChuckerInterceptor` with `alwaysReadResponseBody(true)` and `maxContentLength(250_000L)` was intercepting and buffering every HTTP response body. Changed to `alwaysReadResponseBody(false)` and `maxContentLength(50_000L)` — Chucker only reads body when the collector UI needs it, not on every request.

## Documentation

Comprehensive technical documentation added (not present in upstream):

- **[Feature Gap Analysis](/stoat-android/reference/fork-changes)** — tracks all 121 API endpoints, 121 implemented (100%)
- **[Discord Parity Plan](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/discord-parity-plan.md)** — 6-phase plan for all 56 remaining endpoints
- **[Backend Required Features](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/backend-required-features.md)** — 39 Discord features impossible without API changes
- **[Revolt API Reference](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/revolt-api-reference.md)** — exhaustive 113+ endpoint reference with schemas, rate limits, WebSocket events
- **[API Discovery](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/api-discovery.md)** — DNS enumeration, domain map, infrastructure analysis
- **[Message Search Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/message-search.md)** — search feature architecture and API integration
- **[Notification Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/notifications.md)** — push notification system, FCM, mute controls
- **[Build System Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/build-system.md)** — Termux ARM64 build process documentation
- **[Antifeature Audit](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/audit-antifeatures.md)** — telemetry, privacy, UX findings
- **[Search API Benchmarks](https://github.com/tribixbite/stoat-android/blob/dev/docs/search-api-benchmark.md)** — performance testing results
- **[Discord Bridge Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/discord-bridge.md)** — stoatcord-bot architecture, import wizard, bridge management

## API Coverage Summary

Cross-referenced against [stoatchat/stoatchat](https://github.com/stoatchat/stoatchat) backend (96 delta routes) and [stoatchat/javascript-client-api](https://github.com/stoatchat/javascript-client-api) (205 endpoints in routes.ts).

| Category | Upstream | This Fork |
|----------|----------|-----------|
| Backend delta routes | ~55 | 96 total, 96 implemented (100%) |
| Auth routes (authifier) | ~10 | 27 implemented (login, MFA, sessions, account, logout) |
| Bots | 0 | 7 endpoints — create, list, fetch, edit, delete, invite, public info |
| Webhooks | 0 | 10 endpoints — create, list, fetch, edit, delete (auth + token), execute |
| Search | None | Full (API + UI + filters + server-wide, MongoDB $text syntax) |
| Moderation UI | Partial | Kick, ban, pin/unpin, reporting (full UI) |
| Server admin UI | None | Settings, roles (hoist/rank), bans, channels (NSFW), permissions (channel-level), emoji, invites, banner, system messages |
| Account management | None | View, edit email/password, delete/disable, MFA/TOTP, session CRUD |
| Social features | Basic | Mutual friends/servers, user profiles, mark-as-unread, block/friend, group DMs |
| Notification controls | Basic | Mute/unmute, FCM management, push unsub on logout, notification-only FCM handling |
| Discord bridge | None | Import wizard, bridge settings, stoatcord-bot integration |
| Sync | None | Settings sync (fetch/set), unread sync |
| Documentation | Minimal | 11 spec documents, API reference |

## Roadmap to Discord Parity

Target: 121/121 API endpoints (100%). 96/96 backend delta routes + 27 auth routes + voice end ring + experimental member query. All 6 phases complete including Discord bridge integration. Search result navigation now scrolls to the target message in-channel. 39 Discord features require backend changes (documented in [backend-required-features.md](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/backend-required-features.md)).

### Phase 1: Account & Security (High Priority) — Complete
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Account info display | `GET /auth/account` | **Done** |
| Change email | `PATCH /auth/account/change/email` | **Done** |
| Change password | `PATCH /auth/account/change/password` | **Done** |
| Delete/disable account | `POST /auth/account/delete`, `disable` | **Done** |
| Email verification | `POST /auth/account/reverify` | **Done** |
| MFA setup (TOTP) | 7 endpoints | **Done** |
| Push unsubscribe on logout | `POST /push/unsubscribe` | **Done** |
| Server-side session logout | `POST /auth/session/logout` | **Done** |

### Phase 2: Server Admin Polish (High Priority) — Complete
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Default permissions editor | `PUT /servers/{id}/permissions/default` | **Done** |
| Role permissions editor | `PUT /servers/{id}/permissions/{roleId}` | **Done** |
| Role hoist toggle + rank editing | `PATCH /servers/{id}/roles/{roleId}`, `PATCH /servers/{id}/roles/ranks` | **Done** |
| Channel permissions (per-role overrides) | `PUT /channels/{id}/permissions/{roleId}`, default | **Done** (tri-state Allow/Neutral/Deny) |
| Server banner upload/remove | `PATCH /servers/{id}` + Autumn upload | **Done** (InlineMediaPicker) |
| Create channel NSFW toggle | `POST /servers/{id}/channels` | **Done** |
| Server invite management | `GET /servers/{id}/invites`, `DELETE /invites/{id}` | **Done** |
| Custom emoji management | `PUT/DELETE /custom/emoji/{id}` | **Done** |
| Member search | `GET /servers/{id}/members` with query | **Done** (client-side) |
| System messages settings | `PATCH /servers/{id}` (system\_messages) | **Done** |
| Fetch single role | `GET /servers/{id}/roles/{role_id}` | **Done** |

### Phase 3: Social Features (Medium Priority) — Complete
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Mutual friends/servers | `GET /users/{id}/mutual` | **Done** |
| DM channel listing | `GET /users/dms` | Existing |
| User profile display + editing | `GET /users/{id}/profile`, `PATCH /users/@me` | **Done** (avatar, background, bio with Autumn upload) |
| Block/unblock users | `PUT/DELETE /users/{id}/block` | **Done** |
| Friend requests | `POST /users/friend`, `PUT/DELETE /users/{id}/friend` | **Done** (send, accept, unfriend) |
| Open DM channel | `GET /users/{id}/dm` | **Done** |
| Group DM management | `POST /channels/create`, `PUT/DELETE /channels/{id}/recipients/{userId}` | **Done** |
| Content reporting | `POST /safety/report` | **Done** (messages, servers, users) |

### Phase 4: Content Management (Medium Priority) — Complete
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Remove all reactions | `DELETE /channels/{id}/messages/{msg}/reactions` | **Done** |
| Bulk delete UI | `DELETE /channels/{id}/messages/bulk` | **Done** |
| Server emoji listing | `GET /servers/{id}/emojis` | **Done** (via cache) |
| Pin/unpin messages | `POST/DELETE /channels/{id}/messages/{msg}/pin` | **Done** |
| Message reactions | `PUT/DELETE /channels/{id}/messages/{msg}/reactions/{emoji}` | **Done** |
| Message search | `POST /channels/{id}/search` | **Done** (text, pinned, sort, pagination) |
| Channel invite creation | `POST /channels/{id}/invites` | **Done** |
| Settings sync | `POST /sync/settings/fetch`, `POST /sync/settings/set` | **Done** |
| Unread sync | `GET /sync/unreads` | **Done** |
| Voice call join | `POST /channels/{id}/join_call` | **Done** (LiveKit integration) |

### Discord Bridge Integration (Fork-Exclusive)
| Feature | Component | Status |
|---------|-----------|--------|
| Discord import wizard | `DiscordImportScreen` + stoatcord-bot API | **Done** |
| Bridge settings (channel linking) | `BridgeSettingsScreen` + bot API | **Done** |
| Discord guild/channel fetch | `GET /api/guilds`, `GET /api/guilds/{id}/channels` | **Done** |
| Bridge link CRUD | `POST /api/links`, `DELETE /api/links/{id}`, `GET /api/links/guild/{id}` | **Done** |
| Migration wizard (roles, channels, emoji) | stoatcord-bot `/migrate` command | **Done** |
| Stoat↔Discord message relay | WebSocket listener + Discord webhooks | **Done** |

### Phase 5: Bots & Webhooks — Complete
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Bot create | `POST /bots/create` | **Done** |
| Bot fetch/edit/delete | `GET/PATCH/DELETE /bots/{id}` | **Done** (3) |
| Bot owned list | `GET /bots/@me` | **Done** |
| Bot invite | `GET/POST /bots/{id}/invite` | **Done** (2) |
| Webhook create | `POST /channels/{id}/webhooks` | **Done** |
| Webhook list | `GET /channels/{id}/webhooks` | **Done** |
| Webhook CRUD (auth) | `GET/PATCH/DELETE /webhooks/{id}` | **Done** (3) |
| Webhook CRUD (token) | `GET/PATCH/DELETE /webhooks/{id}/{token}` | **Done** (3) |
| Webhook execute | `POST /webhooks/{id}/{token}` | **Done** |
| Bot management UI | `BotManagementScreen` | **Done** (create, edit, delete, token copy, avatar, description) |
| Webhook management UI | `WebhookManagementScreen` | **Done** (create, edit, delete, URL copy) |

### Phase 6: Polish & Edge Cases (Low Priority) — Complete
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Session management (rename, revoke, revoke all) | `PATCH/DELETE /auth/session/{id}`, `DELETE /auth/session/all` | **Done** |
| User flags display | Bitmask (Suspended/Deleted/Banned/Spam) | **Done** (UserFlagList + UserInfoSheet) |
| Default avatar | `GET /users/{id}/default_avatar` | Existing (Glide handles) |
| Policy acknowledge | `POST /policy/acknowledge` | **Done** |
| Voice end ring | `PUT /channels/{id}/end_ring/{userId}` | **Done** |
| Push unsubscribe | `POST /push/unsubscribe` | **Done** |
| FCM notification-only handling | `HandlerService` fallback | **Done** |
| Robust push registration | `ChatRouterScreen` retry logic | **Done** |
| Push relay via bot | `PushManager` + bot HTTP API | **Done** (FCM via stoatcord-bot) |
| Password reset | `POST/PATCH /auth/account/reset_password` | **Done** (2 endpoints) |
| Email verify (code) | `POST /auth/account/verify/{code}` | **Done** |
| Mass mention parsing | `@everyone`/`@here` in markdown | **Done** (MassMentionParser + renderer) |
| Webhook info sheet | `WebHookUserSheet` | **Done** (replaces stub) |
| Feedback link | OverviewScreen → GitHub Issues | **Done** |
| Members experimental query | `GET /servers/{id}/members_experimental_query` | **Done** |

### Not Achievable (Backend Limitations)
39 features require backend API changes: threads/forums, scheduled events, stage channels, AutoMod, audit log, slash commands, interactive components, polls, stickers, screen sharing, rich presence, server templates, vanity URLs, per-user permission overrides, slow mode. Full list in [backend-required-features.md](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/backend-required-features.md).

## Commit History

All changes from upstream divergence point:

| Commit | Type | Description |
|--------|------|-------------|
| `321f8ee` | feat | x86\_64 AAPT2 wrapper using proot + qemu |
| `3174bed` | feat | Termux ARM64 build system |
| `19cddc8` | fix | Native ARM64 AAPT2, graceful native lib loading |
| `7c78ec5` | ci | APK build and dev release on every push |
| `40214dc` | docs | Antifeature and UX audit |
| `dd06b7e` | feat | Notification fixes + message search |
| `beb286e` | fix | Search serialization, notification state, webp upload, filters |
| `a756278` | fix | Search submit button, HTTP status checks, push error display |
| `fecea65` | fix | WebSocket reconnection on resume after background disconnect |
| `469af7c` | fix | Search timeout, crash, FCM error messages, manage notifications |
| `9f46e35` | fix | Search wildcard bug, HttpTimeout, mention filter |
| `9101558` | feat | Collapsible search filters, clear all, result count |
| `40e859e` | feat | Moderation (kick/ban), pin messages, server management routes |
| `465b458` | fix | Search socket timeout increased to 60s with 75s total cap |
| `f48c580` | docs | Enable GitHub Pages, update URLs to fork, add fork changes reference |
| `4ec7457` | feat | Server management UI, member moderation, FCM fix |
| `7b23120` | docs | Server management spec, fork changes reference |
| `32f89d9` | feat | Permissions editor (default + per-role allow/deny/neutral) |
| `01c82b9` | fix | Show server/channel names in notification settings |
| `ca3025a` | docs | Permissions editor and notification improvement specs |
| `dc66ccf` | fix | Discover tab clicks, FCM placeholder detection, project memory |
| `6dd4cc0` | feat | Account management, invite management screens |
| `44699c7` | feat | Emoji management, mutual friends/servers display |
| `e54433f` | docs | Update specs for emoji, invites, mutual, account |
| `c0bfb6b` | feat | Session management, remove all reactions |
| `9216ff4` | feat | Member search, session management, remove all reactions |
| `ee92622` | feat | Bulk delete messages UI with count selector |
| `bb8740d` | docs | Update fork-changes with session, member search, bulk delete |
| `856b45e` | docs | Update CLAUDE.md with real Firebase config |
| `5374c5a` | feat | MFA TOTP setup, recovery codes management |
| `f2ad4d0` | fix | Message send race condition (#17), DM mention autocomplete (#52) |
| `a3d8969` | docs | Update fork-changes with MFA, upstream bug fixes |
| `e7e8101` | feat | Collapsible categories, reply jump, send debounce, retry fix |
| `c51e360` | fix | Navigation restoration on app restart for DM/saved notes (#41/#21) |
| `8768da8` | fix | Friends list reactivity (#39), blocked user swipe reply (#11) |
| `a444479` | fix | Swipe-to-reply no longer hijacks code block scroll (#14) |
| `4a29c1d` | fix | README dead links, add fork docs URLs (#42) |
| `1f7a26f` | feat | Mark-as-unread in message context menu |
| `65d18d3` | fix | Fetch user from API when not in cache for info sheet |
| `43d10db` | docs | API coverage cross-reference with backend and JS client |
| `f1919b0` | fix | Duplicate reply banners (#57), spoiler text (#54), copy buttons (#40) |
| `df30163` | docs | Add duplicate reply, spoiler, copy fixes to fork-changes |
| `16cb2d6` | feat | Long-press server icon opens context menu (#20) |
| `c72565d` | fix | Profile card tap-to-copy error handling and buttons (#19) |
| `07b052c` | docs | Add server icon long-press and profile card fixes to fork-changes |
| `104bed3` | fix | Category chevron direction (expanded=down, collapsed=right) |
| `5d0a7e4` | docs | Rewrite README with clear UNOFFICIAL fork disclaimer |
| `94b62ce` | feat | Server-wide message search via context sheet |
| `9f7d001` | docs | Server-wide search, chevron fix, README update to fork-changes |
| `e1fc408` | feat | Discord import wizard in server settings |
| `c28356d` | docs | Privacy policy and terms of service pages |
| `59dc49e` | feat | Bridge settings screen for Discord-Stoat channel linking |
| `8c02958` | docs | Discord bridge architecture spec |
| `604fe31` | fix | Allow cleartext HTTP in debug builds, fix bot OAuth permissions |
| `76bb98b` | feat | Role hoist/rank editing, NSFW toggle, channel perms, server banner |
| `dec3d10` | feat | Notification debug logging, system messages settings, server banner |
| `38170e8` | fix | Robust FCM push registration and notification-only message handling |
| `beeaf35` | feat | Push unsubscribe and proper server-side session logout |
| `a66e966` | docs | Update roadmap with push, notifications, system messages progress |
| `f1f15a4` | docs | Comprehensive roadmap audit — add 20+ untracked features |
| `22d22ba` | docs | F-Droid anti-features audit and degoogling roadmap |
| `99730c3` | feat | Bot management and webhook CRUD (Phase 5) |
| `4a8d96a` | docs | Update roadmap — Phase 5 complete, 96/96 delta routes (100%) |
| `9abb26a` | feat | Finish remaining stubs and add missing endpoints (Phase 6) |
| `5717ca1` | docs | Update roadmap — Phase 2+6 complete, all stubs resolved |
| `9542704` | perf | Eliminate double deserialization and parallelize startup |
| `b656e54` | fix | Prevent OOM crash in image processing for banners/backgrounds |
| `ae2499e` | fix | Use ImageProcessor for profile avatar/background and channel icon |
| `f4fa15c` | feat | Image processing, bot profile editing, PAL review fixes |
| `bbb79b3` | docs | Update roadmap — 121/121 API endpoints, search scroll-to-message |
| `d9071bc` | docs | Add performance optimizations to fork-changes |
| `66c5b1e` | feat | Search result scroll-to-message, remaining API endpoints, fix TODOs |
| `3b08e36` | feat | Push notification relay via stoatcord-bot (FCM + mode selector) |
| `d57c734` | feat | UnifiedPush support (connector 3.2.0, StoatPushService, distributor picker) |
| `d43a168` | fix | ANR on settings navigation (ActionChannel CancellationException loop), notification settings threading |
| `77e5eda` | fix | UnifiedPush WAKE\_LOCK permission (override maxSdkVersion=25), Glide main-thread crash, ULID validation guard |
| `c44886b` | fix | Show error with retry button when message fetch fails instead of infinite shimmer |
| `83b1580` | fix | Channels not marking as read when last message was deleted (#62) |
| `c0110dd` | fix | Prevent double-tap duplicate requests (#30/#57), cache watch timeout for stuck loading (#21) |
| `fc30a84` | fix | Early access sheet dismissal allows back press to prevent stuck UI (#63) |
| `30fad61` | feat | MFA screen auto-submit on 6 digits, loading indicator, double-tap guard |
| `d2beeaa` | fix | Login double-tap guard, loading state, session ID bug (was storing token as ID) |
| `52b85a0` | fix | Registration double-tap guard prevents duplicate signup emails (#30) |
| `2362e9e` | fix | Group creation double-tap guard (#30) |
| `e48f41d` | feat | Notification tap navigates to relevant channel (cold start + foreground) |
| `9e26d75` | fix | Send button shows progress indicator during upload/send (#33) |
| `0eeafeb` | fix | @mention autocomplete matches displayName in DMs and groups (#52) |
| `50f7a72` | fix | Friends list updates immediately after relationship changes (#39) |
| `4f275fa` | fix | CatchUpScreen uses actual newest message ID from channel cache |
| `48b2a86` | fix | Handle suspended/banned user state at login to prevent stuck app (#27) |
| `7894fd4` | fix | Type-specific system message colors, verify permission calculation |
| `a7c3636` | fix | Handle missing WebSocket frame types for real-time cache sync |
| `32353e7` | feat | Inline text file preview in message attachments |
| `e7dec5c` | fix | Prevent NullPointerException crash on image/video with missing dimensions |
