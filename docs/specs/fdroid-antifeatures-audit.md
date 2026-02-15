# F-Droid Compatibility & Anti-Features Audit

**Date**: 2026-02-15
**Scope**: Stoat for Android (`chat.stoat`, this fork), alexjyong/android ("Refork"), Clerotri (React Native client)
**Verdict**: Stoat is **not eligible** for F-Droid in its current form. The barriers are dependency choices, not architectural limitations.

---

## 1. Anti-Features in Stoat (This Fork)

Per [F-Droid Anti-Features definitions](https://f-droid.org/docs/Anti-Features/) and [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/).

### 1.1 Non-Free Dependencies (Blocker)

| Dependency | Version | Location | Purpose |
|---|---|---|---|
| `com.google.firebase:firebase-bom` | 33.15.0 | `libs.versions.toml:96` | Firebase platform BOM |
| `com.google.firebase:firebase-messaging` | (BOM-managed) | `libs.versions.toml:97`, `build.gradle.kts:268-269` | FCM push notifications |
| `com.google.gms:google-services` | 4.4.2 | `libs.versions.toml:116`, `build.gradle.kts:16` | Gradle plugin, processes `google-services.json` |
| `io.sentry:sentry-android` | 8.13.2 | `libs.versions.toml:79`, `build.gradle.kts:225` | Crash reporting to Sentry servers |
| `io.sentry:sentry-compose-android` | 8.13.2 | `libs.versions.toml:80`, `build.gradle.kts:226` | Compose integration for Sentry tracing |
| `io.sentry.android.gradle` | 4.12.0 | `libs.versions.toml:117`, `build.gradle.kts:14` | ProGuard mapping upload, logcat instrumentation |
| `com.github.hcaptcha:hcaptcha-android-sdk` | 3.8.1 | `libs.versions.toml:81`, `build.gradle.kts:242` | Anti-bot captcha during registration |

**Firebase specifics:**
- `HandlerService.kt` extends `FirebaseMessagingService` (line 47)
- `onNewToken()` registers with backend via `/push/register` (line 51-65)
- `onMessageReceived()` parses FCM data payloads into rich notifications (line 68-310)
- `AndroidManifest.xml:61-67` declares FCM intent filter
- `AndroidManifest.xml:54-59` sets default FCM notification icon/color
- `google-services.json` is **required at build time** — build fails without it

**Sentry specifics:**
- Auto-init disabled in manifest (`AndroidManifest.xml:50-52`) but manually initialized in `MainActivity.onFirstFrameCreated()`
- `StoatAPI.kt` and `ChatRouterScreen.kt` call `Sentry.captureException()` for error logging
- `build.gradle.kts:161-173` enables tracing instrumentation with logcat collection at WARNING+ level
- Sends data to Sentry's proprietary servers — not opt-in, not user-controllable

**hCaptcha specifics:**
- `RegisterDetailsScreen.kt:46-49` imports hCaptcha SDK
- `RegisterDetailsScreenViewModel.initCaptcha()` (line 58) configures DARK theme + INVISIBLE size
- Only triggered during account creation, but the SDK itself is proprietary

### 1.2 Non-Free Network Services (Blocker)

| Service | Protocol | Dependency |
|---|---|---|
| Google Firebase Cloud Messaging | Proprietary push (GCM/FCM) | Requires Google Play Services on device |
| Sentry (sentry.io) | HTTPS telemetry | Sends crash reports, traces, logcat to Sentry cloud |
| hCaptcha (hcaptcha.com) | JavaScript challenge | Loaded during registration flow |

FCM is the **only** push notification mechanism. There is no WebSocket polling fallback, no UnifiedPush support. On degoogled devices, the app will function but **never receive push notifications**.

### 1.3 Tracking (Blocker)

Sentry collects and transmits:
- Stack traces with device model, OS version, app version
- Logcat output (WARNING level and above) via instrumentation (`build.gradle.kts:168-171`)
- Compose navigation traces (via `sentry-compose-android`)
- Release name/version attached to every event

This is not opt-in. There is no user-facing toggle to disable it.

### 1.4 Non-Free Build Requirements (Blocker)

- `google-services` Gradle plugin (`build.gradle.kts:16`) is proprietary Google code
- `app/google-services.json` must exist at build time — contains Firebase project config
- These cannot be satisfied by F-Droid's reproducible build infrastructure

### 1.5 Additional Issues (Non-Blocking)

| Issue | Severity | Details |
|---|---|---|
| `android:allowBackup="true"` | Security concern | `AndroidManifest.xml:28` — tokens extractable via ADB backup |
| Session tokens in plaintext DataStore | Security concern | `KVStorage.kt` — no encryption at rest |
| Unencrypted SQLDelight database | Security concern | All cached messages/channels readable from app data |
| Certificate pinning only on CDN | Gap | `network_security_config.xml` pins `stoatusercontent.com` only, not `api.stoat.chat` or `events.stoat.chat` |
| Google Sans Flex font | Non-free asset | Proprietary Google font bundled in assets |
| Chucker HTTP inspector | Debug-only | `build.gradle.kts:273-274` — noop in release, but debug APKs log all HTTP traffic |
| GMS Photo Picker backport | Non-free service | `AndroidManifest.xml:181-192` — registers for Google Play Services photo picker module |

---

## 2. Comparative Analysis: Community Forks

### 2.1 alexjyong/android ("Refork")

**Repository**: https://github.com/alexjyong/android
**Base**: Same Kotlin + Compose codebase as Stoat
**License**: AGPL-3.0
**F-Droid eligible**: No

**What it adds:**
- WebSocket foreground service (`NotificationForegroundService.kt`) as a **secondary** notification path
  - Maintains persistent Ktor WebSocket connection to `STOAT_WEBSOCKET`
  - Runs as `FOREGROUND_SERVICE_DATA_SYNC` with `IMPORTANCE_LOW` notification
  - Exponential backoff reconnection (max 30s)
  - Filters by channel mute state, active channel, mentions, role mentions
  - `NotificationServiceManager.kt` gates on user opt-in (`notification_background_service_enabled` key) + `POST_NOTIFICATIONS` permission
- Voice message recording
- Role mention detection in notifications
- Jump-to-replied-message

**What it does NOT fix:**
- Firebase FCM still present and primary (`HandlerService.kt` unchanged, `AndroidManifest.xml:64-70`)
- Sentry still integrated (`AndroidManifest.xml:53-55`)
- hCaptcha still integrated
- `google-services.json` still required at build time
- `google-services` Gradle plugin still applied
- No build flavors (debug/release only)
- No F-Droid metadata

**README claims "works on de-Googled phones"** — this is misleading. The app will launch and the WebSocket service can deliver notifications while running, but:
- FCM push (the primary path) requires Google Play Services
- The WebSocket service is battery-intensive and requires explicit user opt-in
- No push notifications when the app is killed/stopped on degoogled devices

**Useful code to adopt**: The `NotificationForegroundService.kt` WebSocket approach is a functional fallback for non-GMS devices. The architecture (separate service, `NotificationServiceManager` gating, `NotificationHelper` display logic) is clean and could serve as the foundation for an F-Droid flavor.

### 2.2 Clerotri (formerly RVMob)

**Repository**: https://github.com/upryzing/clerotri
**Stack**: React Native + TypeScript + revolt.js
**License**: AGPL-3.0
**F-Droid eligible**: Yes (available on [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/app.upryzing.clerotri))

**Notification approach:**
- Uses `@notifee/react-native` for **local** notifications — no FCM dependency
- `src/lib/notifications/notifee.ts` creates Android notification channel "clerotri"
- `sendNotifeeNotification()` builds rich notifications from revolt.js `Message` objects
- `src/lib/notifications/utils.ts` contains `handleMessageNotification()` which checks mute/mention settings
- Real-time delivery via revolt.js WebSocket client
- Limitation: notifications only work while the React Native JS runtime is active

**Zero proprietary dependencies:**
- No Firebase, no Google Play Services, no google-services plugin
- No Sentry, no crash reporting
- No hCaptcha (registration deferred to web browser)
- No analytics enabled by default (optional self-hosted Deno analytics server, opt-in)
- `android/app/src/main/AndroidManifest.xml` declares only `INTERNET` permission (removes `WRITE_EXTERNAL_STORAGE`, `READ_PHONE_STATE`, `READ_EXTERNAL_STORAGE` via `tools:node="remove"`)

**F-Droid metadata present:**
- `fastlane/metadata/android/en-US/short_description.txt`
- `fastlane/metadata/android/en-US/full_description.txt`
- Screenshots directory for store listing

**Limitations:**
- React Native, not native Kotlin — different tech stack, can't directly share code
- No voice/video support (placeholder screen)
- Beta quality, smaller feature set
- Notifications stop when JS runtime is killed by Android

**Useful patterns to adopt:**
- Registration flow that defers captcha to system browser (avoids hCaptcha SDK)
- Zero-proprietary-dependency architecture as proof of concept
- Fastlane metadata structure for F-Droid submission

---

## 3. F-Droid Inclusion Requirements

Per the [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/):

> "The software in the repository must be Free, Libre, and Open Source Software, and must have build tools, source code, and related files... all available as free software."

Specific blockers for Stoat:

1. **Binary dependencies**: Firebase client libraries are proprietary (not in Maven Central's FOSS subset)
2. **Build plugin**: `com.google.gms.google-services` is proprietary
3. **Build-time secrets**: `google-services.json` cannot be generated by F-Droid
4. **Telemetry**: Sentry sends data without user consent — violates F-Droid Tracking anti-feature policy
5. **Proprietary captcha**: hCaptcha SDK is closed-source

Apps like [Molly](https://github.com/nicknisi/nicknisi) (Signal fork), [Element](https://github.com/element-hq/element-android), and [Tusky](https://github.com/tuskyapp/Tusky) solve this with **build flavors** — a `foss`/`fdroid` variant that excludes proprietary dependencies and uses FOSS alternatives.

---

## 4. Summary

| Client | Firebase | Sentry | hCaptcha | GMS Plugin | WS Fallback | UnifiedPush | F-Droid Ready |
|---|---|---|---|---|---|---|---|
| **Stoat (this fork)** | Required | Required | Required | Required | No | No | No |
| **alexjyong/Refork** | Required | Required | Required | Required | Yes (opt-in) | No | No |
| **Clerotri** | None | None | None (browser) | None | Yes (primary) | No | Yes (IzzyOnDroid) |

The gap between "current state" and "F-Droid eligible" is well-defined and bounded. See `docs/specs/degoogling-roadmap.md` for the implementation plan.
