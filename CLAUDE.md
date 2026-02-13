# Stoat for Android — Build & Development Guide

## Project Overview
Stoat is a native Android chat client (fork of Revolt) built with Kotlin + Jetpack Compose.
- **Package**: `chat.revolt` (namespace: `chat.stoat`)
- **Version**: 1.3.9a (code: 001003409)
- **SDK**: compile 36 / min 26 / target 36
- **Stack**: Kotlin 2.2, Compose BOM 2025.03, Hilt, Ktor, SQLDelight, Firebase

## Build Commands (Termux ARM64)

**ALWAYS use the build script — never run gradle directly:**
```bash
./build-and-install.sh              # Debug build (default)
./build-and-install.sh debug        # Explicit debug
./build-and-install.sh release      # Release build
./build-and-install.sh debug clean  # Clean + debug build
```

The build script handles:
- ARM64 AAPT2 override (x86_64 via proot+qemu for SDK 36 support)
- JVM memory limits for mobile device
- ADB wireless auto-discovery and install
- Fallback to termux-open

## AAPT2 Setup (Critical for SDK 35+)
Termux's native `aapt2` (v2.19) only supports up to SDK 34. For SDK 35+:
1. Run `tools/x86_64-rootfs/setup-x86_64-rootfs.sh` to download x86_64 glibc libs
2. The build script auto-detects and uses `tools/aapt2-arm64/aapt2` (x86_64 via qemu)
3. Falls back to native aapt2 if x86_64 wrapper unavailable

## Native Code (Currently Disabled)
The `stendal` (cmark) and `finalmarkdown` native libraries are disabled in this Termux build.
NDK 27 host tools are x86_64 and don't run on ARM64.
- TODO: Set up cross-compilation with Termux's native clang + NDK sysroot
- TODO: Or pre-build .so files and include in jniLibs/

## Required Local Files (gitignored)
- `stoatbuild.properties` — Sentry DSN, flavour ID, debug app name
- `app/google-services.json` — Firebase config (extracted from official app via ADB, real FCM works)
- `local.properties` — SDK path

## Architecture
```
app/                    # Main Android app (Kotlin + Compose)
  src/main/java/chat/stoat/
    activities/         # MainActivity, media viewers
    api/                # StoatAPI client, realtime WebSocket, routes
    composables/        # Reusable Compose components
    screens/            # Screen-level Composables
    sheets/             # Bottom sheets
    ndk/                # Native library bindings
core/model/             # Shared data models (schemas, constants)
tools/                  # Build tooling (aapt2 wrappers, x86_64 rootfs)
docs/                   # Astro documentation site
```

## Key Dependencies
- **Networking**: Ktor 3.3.2 + OkHttp
- **DI**: Hilt 2.57
- **Data**: SQLDelight 2.0.1, DataStore
- **Media**: Glide 5.0.5, Media3/ExoPlayer 1.7.1
- **UI**: Material3 1.4.0-alpha15, Haze (blur), Shimmer
- **Push**: Firebase Cloud Messaging
- **Error Tracking**: Sentry 8.13.2

## Commands
- **`go`**: Continue adding missing features toward Discord parity (excluding documented backend restrictions in `docs/specs/backend-required-features.md`). Update `docs/src/content/docs/reference/fork-changes.md` roadmap and `docs/specs/` after each round. Build, test via ADB if available. Maintain conventional commits.

## Feature Roadmap
See `docs/specs/discord-parity-plan.md` for the 6-phase plan. Progress tracked in `docs/src/content/docs/reference/fork-changes.md` under the Roadmap section. Currently at 74% API coverage (89/121 endpoints). Target: 97% (117/121).

### Remaining Work (Priority Order)
1. **MFA TOTP setup** — 7 endpoints (Phase 1 completion)
2. **Bot management** — 7 endpoints: create, edit, delete, fetch, invite bots (Phase 5)
3. **Webhook management** — 4 endpoints: create, edit, delete, execute webhooks (Phase 5)
4. **User profile editing** — enhance existing profile display with edit capabilities
5. **Comprehensive UI testing** — screenshot every screen via ADB, verify all features work end-to-end

## Session Continuation
When starting a new session, if told `go`:
1. Read this file and `docs/src/content/docs/reference/fork-changes.md` roadmap section
2. Check `git log --oneline -10` for recent progress
3. Pick the next unfinished phase/feature from the roadmap
4. Build, test via ADB (screenshot key screens), commit, update docs
5. Repeat until all phases complete and all screens verified

## NEVER TOUCH UPSTREAM
- NEVER post comments, replies, or questions on upstream GitHub issues (stoatchat/for-android)
- NEVER open pull requests against upstream
- NEVER interact with upstream repositories in any way
- DO read upstream issues (e.g. https://github.com/stoatchat/for-android/issues) to identify bugs and feature requests to address in this fork
- All work stays in this fork (tribixbite/stoat-android) only

## Notes
- LiveKit voice/video is temporarily disabled (commented out in build.gradle.kts)
- Debug builds use `.debug` applicationId suffix and custom app name
- Release builds enable R8 minification + resource shrinking
