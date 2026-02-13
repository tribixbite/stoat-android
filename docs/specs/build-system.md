# Build System — Termux ARM64

## Overview
Stoat for Android is built on Termux (ARM64 Android) using a custom build pipeline
that works around the lack of x86_64 host tools normally provided by Android SDK.

## Components

### build-and-install.sh
Entry point for all builds. Handles:
- Environment setup (JAVA_HOME, ANDROID_HOME, PATH)
- AAPT2 selection (x86_64 via qemu preferred, native ARM64 fallback)
- Gradle invocation with ARM64-specific flags
- APK installation via ADB wireless or termux-open

### AAPT2 (x86_64 via proot+qemu)
- Google's AAPT2 8.11.1-12782657 (x86_64 ELF) at `tools/aapt2-x86_64/aapt2`
- Minimal x86_64 glibc rootfs at `tools/x86_64-libs/` (~5.5MB)
  - Debian bookworm amd64: libc6, libstdc++6, zlib1g, libgcc-s1
- Wrapper at `tools/aapt2-arm64/aapt2` delegates to `tools/aapt2-x86_64/aapt2-wrapper`
- Wrapper runs: `proot -q qemu-x86_64 -r x86_64-libs ... aapt2 "$@"`
- Setup script: `tools/x86_64-rootfs/setup-x86_64-rootfs.sh`

### NDK / Native Code
- NDK 27.0.12077973 installed but host tools are x86_64 (can't run on ARM64)
- Native builds (cmark/stendal) currently disabled in build.gradle.kts
- TODO: Cross-compile using Termux's native clang + NDK sysroot

### SDK Setup
- Android SDK at `~/android-sdk`
- Platforms: 34, 35, 36 (36 downloaded manually)
- Build-tools: 34.0.0, 35.0.0
- Java: OpenJDK 21 (Termux)
- Gradle: 8.13 (via wrapper)

## Compromises / Known Issues
1. **Native libs missing**: stendal (cmark) and finalmarkdown .so not built
   - App will crash at markdown rendering until these are provided
   - Workaround: pre-build on x86_64 host or set up cross-compilation
2. **AAPT2 slower via qemu**: ~2-5x slower than native, adds ~30s to resource processing
3. **google-services.json placeholder**: Push notifications won't work without real Firebase config
4. **Sentry DSN empty**: Error tracking disabled in local builds
