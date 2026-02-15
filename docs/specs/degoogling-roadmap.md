# Degoogling & F-Droid Readiness Roadmap

**Date**: 2026-02-15
**Goal**: Create an `fdroid` build flavor that removes all proprietary dependencies, enabling F-Droid submission while maintaining full functionality in the `gms` (default) flavor.
**Prerequisite reading**: `docs/specs/fdroid-antifeatures-audit.md`

---

## Architecture: Build Flavors

The standard Android approach (used by Element, Molly, Tusky, etc.) is **product flavors** that swap implementations at compile time.

```
productFlavors {
    gms {        // Google Play Store build (current behavior)
        dimension = "distribution"
    }
    fdroid {     // F-Droid build (no proprietary deps)
        dimension = "distribution"
    }
}
```

Each flavor gets its own source set directory:
```
app/src/
├── main/           # Shared code
├── gms/            # GMS-only: Firebase, Sentry, hCaptcha
│   └── java/chat/stoat/
│       ├── push/FcmPushProvider.kt
│       ├── telemetry/SentryTelemetry.kt
│       └── captcha/HCaptchaProvider.kt
├── fdroid/         # FOSS-only: UnifiedPush or WS, no telemetry
│   └── java/chat/stoat/
│       ├── push/FossPushProvider.kt
│       ├── telemetry/NoopTelemetry.kt
│       └── captcha/BrowserCaptchaProvider.kt
```

Shared interfaces in `main/` with flavor-specific implementations.

---

## Phase 1: Push Notification Abstraction (Priority: Critical)

**Goal**: Replace direct Firebase imports with an interface, implement WebSocket fallback.

### 1.1 Define PushProvider Interface

Create `app/src/main/java/chat/stoat/push/PushProvider.kt`:
```kotlin
interface PushProvider {
    /** Register for push notifications. Called after login. */
    suspend fun register(sessionToken: String): Result<Unit>
    /** Unregister. Called on logout. */
    suspend fun unregister(): Result<Unit>
    /** Whether this provider requires Google Play Services. */
    val requiresGms: Boolean
}
```

### 1.2 GMS Flavor: FCM Implementation

Move current `HandlerService.kt` (`app/src/main/java/chat/stoat/c2dm/HandlerService.kt`) into `app/src/gms/`. Wrap the existing FCM token retrieval from `ChatRouterScreen.kt` into `FcmPushProvider.kt`.

No behavioral change for the Play Store build.

### 1.3 F-Droid Flavor: WebSocket Foreground Service

Adopt the alexjyong approach (`NotificationForegroundService.kt`) with improvements:
- Port `NotificationForegroundService.kt`, `NotificationHelper.kt`, `NotificationServiceManager.kt` from `~/git/alexjyong-android/app/src/main/java/chat/stoat/services/`
- Key architecture points from alexjyong's implementation:
  - Ktor WebSocket to `STOAT_WEBSOCKET` with auth frame (`AuthorizationFrame`)
  - Exponential backoff reconnection (1s, 2s, 4s... max 30s)
  - Filters: active channel suppression, per-channel/server mute, mention detection
  - `FOREGROUND_SERVICE_DATA_SYNC` type, `IMPORTANCE_LOW` notification
  - Gated by `notification_background_service_enabled` KVStorage key + `POST_NOTIFICATIONS` permission
- Improvements over alexjyong:
  - Add periodic heartbeat/ping to detect stale connections
  - Add battery optimization guidance in settings UI
  - Consider `WorkManager` periodic sync as lightweight alternative for users who don't want foreground service

### 1.4 Future: UnifiedPush Support

[UnifiedPush](https://unifiedpush.org/) is the standard FOSS push protocol supported by F-Droid ecosystem apps. It requires:
- Server-side: a UnifiedPush distributor endpoint (e.g., ntfy, Gotify, NextPush)
- Client-side: `org.unifiedpush.android:connector:2.x` library (Apache 2.0)
- Backend changes: Stoat/Revolt backend would need to send push via UnifiedPush in addition to FCM

This is **backend-dependent** — the Stoat backend currently only supports FCM push registration (`/push/register` endpoint). UnifiedPush requires either:
- A proxy service that receives FCM and re-dispatches via UnifiedPush (like [ntfy](https://ntfy.sh/))
- Native backend support for UnifiedPush distributors

**Recommendation**: Ship with WebSocket foreground service first (client-only change), add UnifiedPush when/if backend support exists.

### Effort: ~2-3 days

---

## Phase 2: Sentry Removal / Abstraction (Priority: High)

**Goal**: Remove mandatory crash reporting. Make telemetry opt-in or absent in fdroid flavor.

### 2.1 Define Telemetry Interface

Create `app/src/main/java/chat/stoat/telemetry/Telemetry.kt`:
```kotlin
interface Telemetry {
    fun init(context: Context, dsn: String, release: String)
    fun captureException(throwable: Throwable)
    fun captureMessage(message: String)
}
```

### 2.2 GMS Flavor: Sentry Implementation

Move Sentry initialization from `MainActivity` into `SentryTelemetry.kt` in `app/src/gms/`. Wire existing `Sentry.captureException()` calls through the interface.

### 2.3 F-Droid Flavor: No-op Implementation

`NoopTelemetry.kt` — all methods are empty. Zero network calls, zero data collection.

### 2.4 Gradle Changes

```kotlin
// In gms flavor only:
"gmsImplementation"(libs.sentry.android)
"gmsImplementation"(libs.sentry.compose.android)

// fdroid flavor: no sentry dependencies at all
```

Remove `alias(libs.plugins.sentry.android)` from fdroid builds. The Sentry Gradle plugin (`io.sentry.android.gradle`) is only needed for GMS:
```kotlin
plugins {
    // ...
    if (isGmsBuild) alias(libs.plugins.sentry.android)
}
```

Or conditionally apply via `build.gradle.kts`:
```kotlin
if (gradle.startParameter.taskNames.any { it.contains("Gms", ignoreCase = true) }) {
    apply(plugin = "io.sentry.android.gradle")
}
```

### Effort: ~1 day

---

## Phase 3: hCaptcha Abstraction (Priority: Medium)

**Goal**: Remove hCaptcha SDK from fdroid flavor. Defer captcha to system browser.

### 3.1 Define CaptchaProvider Interface

```kotlin
interface CaptchaProvider {
    /** Solve captcha and return token. */
    suspend fun solve(context: Context, siteKey: String): Result<String>
}
```

### 3.2 GMS Flavor: In-App hCaptcha

Current behavior. `RegisterDetailsScreen.kt` uses `HCaptcha.getClient(activity)` with DARK/INVISIBLE config.

### 3.3 F-Droid Flavor: Browser-Based Captcha

Two options:

**Option A: Custom Tab / WebView** — Open a lightweight HTML page (hosted or bundled as asset) that loads hCaptcha via JavaScript, returns token via deep link or JavaScript interface. This avoids the proprietary SDK while still solving captcha in-flow.

**Option B: Defer to web registration** — Like Clerotri (`src/lib/auth/` defers to `https://old.stoat.chat/login/create`). The F-Droid flavor's registration screen shows a "Create account on stoat.chat" button that opens the system browser. Login (email/password) works without captcha.

**Recommendation**: Option B is simpler and proven. Captcha is only needed for registration, not login. Most users on F-Droid-compatible devices will already have an account.

### Effort: ~0.5 days

---

## Phase 4: Build System Changes (Priority: High)

### 4.1 Conditional google-services Plugin

The `com.google.gms.google-services` plugin processes `google-services.json` and injects Firebase config as Android resources. It must not run for fdroid builds.

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ... other always-on plugins
}

// Apply GMS-only plugins conditionally
val isGmsBuild = gradle.startParameter.taskNames.any {
    it.contains("Gms", ignoreCase = true)
}
if (isGmsBuild) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.sentry.android.get().pluginId)
}
```

### 4.2 Flavor-Specific Dependencies

```kotlin
dependencies {
    // GMS-only
    "gmsImplementation"(platform(libs.firebase.bom))
    "gmsImplementation"(libs.firebase.messaging)
    "gmsImplementation"(libs.sentry.android)
    "gmsImplementation"(libs.sentry.compose.android)
    "gmsImplementation"(libs.hcaptcha)

    // F-Droid — no proprietary deps
    // (WebSocket notification service uses existing Ktor dependency)
}
```

### 4.3 Remove google-services.json Requirement for fdroid

The fdroid flavor does not need `google-services.json`. A stub file or conditional Gradle logic eliminates this:
```kotlin
android {
    productFlavors {
        create("fdroid") {
            // No google-services.json processing
        }
    }
}
```

### 4.4 Manifest Merging

Create `app/src/fdroid/AndroidManifest.xml` that removes Firebase-specific entries:
```xml
<manifest xmlns:android="..."
    xmlns:tools="...">
    <application>
        <!-- Remove FCM service -->
        <service android:name=".c2dm.HandlerService"
            tools:node="remove" />
        <!-- Remove Firebase metadata -->
        <meta-data android:name="com.google.firebase.messaging.default_notification_icon"
            tools:node="remove" />
        <meta-data android:name="com.google.firebase.messaging.default_notification_color"
            tools:node="remove" />
        <!-- Remove Sentry auto-init metadata -->
        <meta-data android:name="io.sentry.auto-init"
            tools:node="remove" />
        <!-- Remove GMS photo picker backport -->
        <service android:name="com.google.android.gms.metadata.ModuleDependencies"
            tools:node="remove" />
        <!-- Add WS notification foreground service -->
        <service android:name=".services.NotificationForegroundService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>
```

### Effort: ~1 day

---

## Phase 5: Security Hardening (Priority: Medium)

These aren't F-Droid blockers but are good practice for any privacy-focused build.

### 5.1 Disable ADB Backup

Change `AndroidManifest.xml:28`:
```xml
android:allowBackup="false"
```
This prevents extraction of session tokens, cached messages, and preferences via `adb backup`.

### 5.2 Encrypted DataStore for Tokens

Replace plaintext `stringPreferencesKey` storage of session tokens with AndroidX Security `EncryptedSharedPreferences` or Jetpack DataStore with encryption:
```kotlin
// Use EncryptedSharedPreferences for sensitive values
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
```

### 5.3 Extend Certificate Pinning

`network_security_config.xml` currently only pins `stoatusercontent.com` (CDN). Extend to:
- `api.stoat.chat` (REST API)
- `events.stoat.chat` (WebSocket)

### 5.4 SQLCipher for Database

Encrypt the SQLDelight database at rest using [SQLCipher for Android](https://github.com/nicknisi/nicknisi) (BSD license, FOSS-compatible):
```kotlin
// Replace standard SQLite driver
val driver = AndroidSqlCipherDriver(
    schema = Database.Schema,
    context = context,
    name = "stoat.db",
    passphrase = derivedKey
)
```

### Effort: ~2 days

---

## Phase 6: F-Droid Submission (Priority: Low, After Phases 1-4)

### 6.1 Fastlane Metadata

Create `fastlane/metadata/android/en-US/`:
```
short_description.txt    # Max 80 chars
full_description.txt     # Store description
changelogs/
  VERSIONCODE.txt        # Per-version changelog
images/
  phoneScreenshots/      # At least 1 screenshot
  icon.png               # 512x512
  featureGraphic.png     # 1024x500
```

### 6.2 F-Droid Build Metadata

Submit to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with:
```yaml
Categories:
  - Internet
License: AGPL-3.0-only
AuthorName: tribixbite
SourceCode: https://github.com/tribixbite/stoat-android
IssueTracker: https://github.com/tribixbite/stoat-android/issues

AutoName: Stoat
Description: |
  Native Android client for the Stoat (Revolt) chat platform.

RepoType: git
Repo: https://github.com/tribixbite/stoat-android.git

Builds:
  - versionName: 1.3.9a
    versionCode: 001003409
    commit: <tag>
    subdir: app
    gradle:
      - fdroid
    ndk: <version>
```

### 6.3 IzzyOnDroid as Intermediate Step

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) has looser requirements than official F-Droid — it accepts APKs from GitHub Releases. Submit there first for broader testing before official F-Droid.

### Effort: ~1 day

---

## Implementation Order & Dependencies

```
Phase 1 (Push abstraction)     ←── Critical path, enables fdroid flavor
    ↓
Phase 4 (Build system)         ←── Wires up flavors, conditional plugins
    ↓
Phase 2 (Sentry removal)       ←── Simple interface + noop
    ↓
Phase 3 (hCaptcha abstraction) ←── Simplest change (browser fallback)
    ↓
Phase 5 (Security hardening)   ←── Optional, can be done in parallel
    ↓
Phase 6 (F-Droid submission)   ←── After all blockers resolved
```

**Total estimated effort**: ~7-8 days for Phases 1-4 (F-Droid eligible), ~2 additional days for Phase 5 (security hardening), ~1 day for Phase 6 (submission).

---

## Open Questions

1. **Backend UnifiedPush support**: Does the Stoat backend team have plans for UnifiedPush? If so, we should design the `PushProvider` interface to accommodate it from the start.
2. **WebSocket foreground service battery impact**: The alexjyong implementation is functional but users report significant battery drain. Need to benchmark and possibly implement `WorkManager` periodic polling as a lighter alternative.
3. **Self-hosted instance support**: Should the fdroid flavor support arbitrary instance URLs? If so, certificate pinning needs to be configurable or disabled for non-default instances.
4. **Google Sans Flex font**: Replace with Inter (already bundled, OFL licensed) for the fdroid flavor, or for all flavors?
5. **GMS Photo Picker backport** (`AndroidManifest.xml:181-192`): The `ModuleDependencies` service for photo picker requires Google Play Services. The fdroid flavor should use the standard Android photo picker (available since API 33) or a FOSS file picker.
