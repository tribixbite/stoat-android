# Stoatally for Android — Build & Development Guide

## Project Overview
Stoatally is a native Android chat client (fork of Revolt/Stoat) built with Kotlin + Jetpack Compose.
- **Package**: `com.tribixbite.stoatally`
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
  src/main/java/com/tribixbite/stoatally/
    activities/         # MainActivity (nav host), media viewers
    api/                # StoatAPI client, realtime WebSocket, route functions
    composables/        # Reusable Compose components (chat, markdown, media)
    screens/            # Screen-level Composables (~68 screens)
    sheets/             # Bottom sheets (~21 sheets)
    ndk/                # Native library bindings (disabled)
core/model/             # Shared data models (schemas, constants)
tools/                  # Build tooling (aapt2 wrappers, x86_64 rootfs)
docs/                   # Astro documentation site (deployed to stoatcord.com)
```

## Key Dependencies
- **Networking**: Ktor 3.3.2 + OkHttp
- **DI**: Hilt 2.57
- **Data**: SQLDelight 2.0.1, DataStore
- **Media**: Glide 5.0.5, Media3/ExoPlayer 1.7.1
- **UI**: Material3 1.4.0-alpha15, Haze (blur), Shimmer
- **Push**: Firebase Cloud Messaging
- **Error Tracking**: Sentry 8.13.2

## API Coverage Status
All 121 Stoat API endpoints have route functions. All 6 Discord parity phases complete.
- **Full GUI**: ~110 endpoints have screens/sheets/composables wired to call them
- **API-only (no GUI trigger)**: ~11 endpoints — bot invite/public bot browse, webhook execute, webhook token-auth variants, resend email verification, password reset confirm, group DM add member, end voice ring, policy acknowledge, join invite by code
- **Backend limitations**: 39 Discord features (threads, forums, AutoMod, audit log, slash commands, polls, etc.) cannot be implemented — documented in `docs/specs/backend-required-features.md`

## Commands
- **`go`**: Continue working on next priority item. Check `git log --oneline -10` for recent progress, read fork-changes.md roadmap, pick next task. Build and test via ADB. Maintain conventional commits.

## Session Continuation
When starting a new session, if told `go`:
1. Read this file and `docs/src/content/docs/reference/fork-changes.md`
2. Check `git log --oneline -10` for recent progress
3. Identify bugs, UI gaps, or upstream issues to address
4. Build, test via ADB (screenshot key screens), commit, update docs
5. Focus areas: UI polish, bug fixes, missing GUI for API-only endpoints

## NEVER TOUCH UPSTREAM
- NEVER post comments, replies, or questions on upstream GitHub issues (stoatchat/for-android)
- NEVER open pull requests against upstream
- NEVER interact with upstream repositories in any way
- DO read upstream issues (e.g. https://github.com/stoatchat/for-android/issues) to identify bugs and feature requests to address in this fork
- All work stays in this fork (tribixbite/stoatally) only

## Notes
- LiveKit voice/video is temporarily disabled (commented out in build.gradle.kts)
- Debug builds use `.debug` applicationId suffix and custom app name
- Release builds enable R8 minification + resource shrinking
- stoatcord-bot deployed on Railway at api.stoatcord.com (bridge, migration, push relay)
- Astro docs site deployed at stoatcord.com via GitHub Pages + Cloudflare DNS
