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

### API Robustness
- **HTTP status code checks** on all API responses (previously some routes ignored error status)
- **Pin endpoint method fix**: corrected from `PUT` to `POST` per OpenAPI spec
- **WebP upload support**: fixed content type detection for WebP image uploads
- **Search submit button** properly triggers search on keyboard action

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
| Implemented | ~55 | 65 (54%) |
| Search | None | Full (API + UI + filters) |
| Moderation UI | Partial | Kick, ban, pin (full UI) |
| Server admin UI | None | Settings, roles, bans, channels, member edit (full UI) |
| Server admin API | Partial | Roles, permissions, channels, members |
| Documentation | Minimal | 10 spec documents, API reference |

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
