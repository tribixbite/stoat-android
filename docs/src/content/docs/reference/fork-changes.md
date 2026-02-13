---
title: Fork Changes — Features & Fixes Not in Upstream
description: Features, bug fixes, and improvements in the tribixbite fork that are not present in the upstream Stoat/Revolt Android client.
template: doc
---

This page documents all features, bug fixes, and improvements added in the [tribixbite/stoat-android](https://github.com/tribixbite/stoat-android) fork that are **not present** in the upstream [stoatchat/for-android](https://github.com/stoatchat/for-android) repository.

## New Features

### Message Search (Full UI + API)
Channel message search with comprehensive filtering, not available in upstream.

- **Search API route** (`POST /channels/{channelId}/search`) with full parameter support: `query`, `limit`, `before`, `after`, `sort`, `include_users`, `pinned`
- **Search UI screen** with text input, debounced queries (400ms), and paginated results
- **Search filters**: sort order (Relevance/Latest/Oldest), pinned-only mode, date range (before/after), mention filter, attachment filter (client-side)
- **Collapsible filter panel** with "Clear All" button and result count display
- **Infinite scroll** pagination using cursor-based `before` parameter
- **Search result navigation**: tap a result to jump to the message in channel context
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

- **Server Settings screen** — edit name, description, icon upload with progress indicator
- **Role Management screen** — create, edit (name + colour with hex preview), delete roles
- **Ban Management screen** — view ban list with reasons, unban with confirmation
- **Create Channel screen** — Text/Voice type selection, name, description
- **Member nickname edit** — edit own or others' nicknames (permission-gated)
- **Role assignment dialog** — toggle roles per member with visual checkmarks
- **Server Settings entry point** — accessible from server context sheet (long-press server)
- **11 API routes**: `PATCH /servers/{id}`, `POST/PATCH/DELETE roles`, `GET/DELETE bans`, `PATCH members`, `POST channels`, plus `uploadToAutumn()` for icons

### Permissions Editor
Full default and per-role permission editor matching the web client UI.

- **Default Permissions screen** — checkbox toggles for all 32 permission bits on the default role
- **Role Permissions screen** — tri-state segmented buttons (Allow / Neutral / Deny) per permission
- **5 categories**: Admin (5), Members (8), Channels (6), Messaging (6), Voice (7)
- **API routes**: `PUT /servers/{id}/permissions/default`, `PUT /servers/{id}/permissions/{roleId}`

### Notification Controls
Granular notification management beyond upstream.

- **Mute/Unmute server** — toggle from server context sheet, synced to backend
- **Mute/Unmute channel** — toggle from channel context sheet, synced to backend
- **Notification filtering** — HandlerService checks mute state before displaying
- **Notification Settings screen** — permission status, FCM registration status with retry, muted server/channel lists (showing names from cache), unmute buttons, reset
- **Placeholder detection** — detects placeholder `google-services.json` at runtime and shows "Not available" instead of misleading retry loop

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
- **Upload emoji** — pick image, upload to `autumn/emojis`, create via `PUT /custom/emoji/{id}`
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

- **Animated chevron indicator** — rotates 90° between collapsed/expanded states
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
- Previously left the screen permanently in Loading state

### Message Send Race Condition (Upstream #17)
- **Fixed channel ID capture** — `sendPendingMessage()` captured `channel?.id` inside async `viewModelScope.launch{}` block, but channel could change if user switched channels during slow attachment upload
- Channel ID now captured synchronously before the launch, preventing messages from being sent to the wrong channel

### DM Mention Autocomplete (Upstream #52)
- **Fixed DM/Group DM mentions** — autocomplete was blocked in DMs by an unnecessary `serverId != null` check; DM channels always have null serverId
- `Autocomplete.userOrRole()` already handled null serverId correctly, so the guard was simply removed

### Discover Tab
- **Fixed server clicks** — discover page links to `app.revolt.chat/invite/CODE` which was silently blocked; now handles all known Revolt/Stoat domains (stoat.chat, stt.gg, rvlt.gg, app.revolt.chat)

### API Robustness
- **HTTP status code checks** on all API responses (previously some routes ignored error status)
- **Pin endpoint method fix**: corrected from `PUT` to `POST` per OpenAPI spec
- **WebP upload support**: fixed content type detection for WebP image uploads
- **Search submit button** properly triggers search on keyboard action
- **Reduced HTTP retry** from 5 to 2 for server errors: 502 Bad Gateway caused ~62s exponential backoff hangs

## Documentation

Comprehensive technical documentation added (not present in upstream):

- **[Feature Gap Analysis](/stoat-android/reference/fork-changes)** — tracks all 121 API endpoints, 65 implemented (54%), 56 remaining
- **[Discord Parity Plan](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/discord-parity-plan.md)** — 6-phase plan for all 56 remaining endpoints
- **[Backend Required Features](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/backend-required-features.md)** — 39 Discord features impossible without API changes
- **[Revolt API Reference](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/revolt-api-reference.md)** — exhaustive 113+ endpoint reference with schemas, rate limits, WebSocket events
- **[API Discovery](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/api-discovery.md)** — DNS enumeration, domain map, infrastructure analysis
- **[Message Search Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/message-search.md)** — search feature architecture and API integration
- **[Notification Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/notifications.md)** — push notification system, FCM, mute controls
- **[Build System Spec](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/build-system.md)** — Termux ARM64 build process documentation
- **[Antifeature Audit](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/audit-antifeatures.md)** — telemetry, privacy, UX findings
- **[Search API Benchmarks](https://github.com/tribixbite/stoat-android/blob/dev/docs/search-api-benchmark.md)** — performance testing results

## API Coverage Summary

| Category | Upstream | This Fork |
|----------|----------|-----------|
| Total endpoints tracked | ~55 | 121 (full OpenAPI) |
| Implemented | ~55 | 96 (79%) |
| Search | None | Full (API + UI + filters) |
| Moderation UI | Partial | Kick, ban, pin (full UI) |
| Server admin UI | None | Settings, roles, bans, channels, permissions, emoji, invites |
| Account management | None | View, edit email/password, delete/disable account |
| Social features | Basic | Mutual friends/servers, user profiles |
| Notification controls | Basic | Mute/unmute, FCM management, placeholder detection |
| Documentation | Minimal | 10 spec documents, API reference |

## Roadmap to Discord Parity

Target: 117/121 endpoints (97%). 39 Discord features require backend changes (documented in [backend-required-features.md](https://github.com/tribixbite/stoat-android/blob/dev/docs/specs/backend-required-features.md)).

### Phase 1: Account & Security (High Priority)
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Account info display | `GET /auth/account` | **Done** |
| Change email | `PATCH /auth/account/change/email` | **Done** |
| Change password | `PATCH /auth/account/change/password` | **Done** |
| Delete/disable account | `POST /auth/account/delete`, `disable` | **Done** |
| Email verification | `POST /auth/account/reverify` | **Done** |
| MFA setup (TOTP) | 7 endpoints | **Done** |

### Phase 2: Server Admin Polish (High Priority)
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Default permissions editor | `PUT /servers/{id}/permissions/default` | **Done** |
| Role permissions editor | `PUT /servers/{id}/permissions/{roleId}` | **Done** |
| Server invite management | `GET /servers/{id}/invites`, `DELETE /invites/{id}` | **Done** |
| Custom emoji management | `PUT/DELETE /custom/emoji/{id}` | **Done** |
| Member search | `GET /servers/{id}/members` with query | **Done** (client-side) |

### Phase 3: Social Features (Medium Priority)
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Mutual friends/servers | `GET /users/{id}/mutual` | **Done** |
| DM channel listing | `GET /users/dms` | Existing |
| User profile display | `GET /users/{id}/profile` | Existing (partial) |

### Phase 4: Content Management (Medium Priority)
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Remove all reactions | `DELETE /channels/{id}/messages/{msg}/reactions` | **Done** |
| Bulk delete UI | `DELETE /channels/{id}/messages/bulk` | **Done** |
| Server emoji listing | `GET /servers/{id}/emojis` | **Done** (via cache) |

### Phase 5: Bots & Webhooks (Low Priority)
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Bot management | 7 endpoints | Planned |
| Webhook management | 4 endpoints | Planned |

### Phase 6: Polish & Edge Cases (Low Priority)
| Feature | Endpoints | Status |
|---------|-----------|--------|
| Session rename | `PATCH /auth/session/{id}` | **Done** |
| User flags | `GET /users/{id}/flags` | Planned |
| Default avatar | `GET /users/{id}/default_avatar` | Planned |

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
