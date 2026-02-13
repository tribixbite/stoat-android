# Antifeature & UX Audit

## Telemetry & Error Reporting

### Sentry (MEDIUM)
- SDK v8.13.2 with gradle plugin v4.12.0
- Auto-init disabled in manifest; manually initialized with DSN from `stoatbuild.properties`
- `Sentry.captureException()` called in: `StoatAPI.kt`, `ChatRouterScreen.kt`, `OverviewScreen.kt`
- Tracing instrumentation enabled with logcat capture (WARNING+)
- ProGuard mappings uploaded for release builds
- **Concern**: scope of data sent to Sentry unspecified (may include user context, message content in stack traces)

### Firebase Cloud Messaging (MEDIUM)
- Firebase BOM + Messaging SDK included
- FCM token retrieved and sent to `/push/subscribe` endpoint
- Token persisted locally in KVStorage as `fcmToken`
- `HandlerService` extends `FirebaseMessagingService`
- **Concern**: Google Firebase receives device push tokens

## Hardcoded Infrastructure

All API endpoints hardcoded in `StoatAPI.kt` with no user configuration:
- `https://api.stoat.chat/0.8` — main API
- `https://cdn.stoatusercontent.com` — file CDN
- `https://proxy.stoatusercontent.com` — proxy
- `https://events.stoat.chat` — WebSocket
- `https://geo.revolt.chat/?client=android` — geolocation (identifies client)
- `https://health.revolt.chat/api/health` — health check

Fallbacks point to `alpha.revolt.chat` subdomains.

## Privacy

### Permissions (OK)
- INTERNET, ACCESS_NETWORK_STATE — required
- POST_NOTIFICATIONS — push notifications
- MANAGE_OWN_CALLS — VoIP
- WRITE_EXTERNAL_STORAGE (maxSdk 29) — legacy camera
- RECORD_AUDIO, CAMERA — commented out (LiveKit disabled)

### Backup (MEDIUM)
- `allowBackup="true"` with empty backup/extraction rules
- All app data (session tokens, messages, preferences) can be backed up to Google Cloud
- **Fix**: exclude sensitive data from backup rules

### User-Agent Fingerprinting (LOW)
- Sends: app name, version, applicationId, Android SDK, manufacturer, device name, Kotlin version
- Sent with every API request

### Certificate Pinning (LOW)
- Only `stoatusercontent.com` has custom CA pinning (Cloudflare, SSL.com)
- Main API and WebSocket endpoints have no pinning

## UX Findings

### No Ads — confirmed
### No Analytics SDKs — confirmed (Sentry is error tracking, not behavioral analytics)

### Chucker HTTP Inspector (debug only)
- Shows full HTTP request/response in debug builds
- Redacts `x-session-token` header
- 1-day retention — debug only, not in release

### Notification Handling (OK)
- `NotificationRationaleDialog` shown when permissions not granted
- Users can explicitly reject (`markNotificationsRejected()`)
- Not aggressive

## Recommendations

| # | Item | Priority |
|---|------|----------|
| 1 | Make Sentry opt-in or add privacy controls | HIGH |
| 2 | Document FCM data practices in privacy policy | HIGH |
| 3 | Add backup exclusion rules for tokens/session data | MEDIUM |
| 4 | Add certificate pinning for API endpoints | MEDIUM |
| 5 | Reduce user-agent detail (remove device name/manufacturer) | LOW |
| 6 | Allow configurable API base URL for self-hosted instances | LOW |
