# Build System — Termux ARM64

## Overview
Stoat for Android is built on Termux (ARM64 Android) using a custom build pipeline
that works around the lack of x86_64 host tools normally provided by Android SDK.

## Components

### build-and-install.sh
Entry point for all builds. Handles:
- Environment setup (JAVA_HOME, ANDROID_HOME, PATH)
- AAPT2 selection (bundled static ARM64 preferred, Termux pkg fallback)
- Gradle invocation with ARM64-specific flags (`-Pandroid.aapt2FromMavenOverride`)
- APK installation via ADB wireless or termux-open

### AAPT2 (native ARM64)
- Static ARM64 binary from lzhiyong/android-sdk-tools (v35.0.2, supports SDK 36)
- Located at `tools/aapt2-arm64/aapt2` (~6.5MB, statically linked ELF)
- Used via Gradle flag: `-Pandroid.aapt2FromMavenOverride=<path>`
- Fallback: Termux `pkg install aapt2` (only supports SDK ≤34)

### Native Libraries
- **stendal**: cmark JNI wrapper for markdown rendering
  - Source: `app/src/main/cpp/stendal/stendal.cpp`
  - Depends on cmark (source expected at `app/src/main/cpp/external/cmark/`)
- **finalmarkdown**: experimental renderer — no C++ source exists yet
  - Kotlin interface only (`chat.stoat.ndk.FinalMarkdown`)
  - Loading is optional; guarded by try/catch in `NativeLibraries.init()`
- Both libraries load gracefully: app falls back to JBM (Kotlin markdown renderer)
  when native libs are unavailable

### NDK / Native Code
- NDK 27.0.12077973 installed but host tools are x86_64 (can't run on ARM64)
- Native builds (cmark/stendal) currently disabled in build.gradle.kts
- **Cross-compilation solution researched and verified** (see below)

### Native Cross-Compilation (Termux clang + NDK sysroot)

The NDK's x86_64 compiler (`toolchains/llvm/prebuilt/linux-x86_64/bin/clang`) cannot
run on ARM64 Termux. However, Termux's native clang can target Android arm64-v8a by
using the NDK's sysroot for headers and libraries.

**Approach**: Termux clang 20.x + `--target=aarch64-linux-android26` + `--sysroot=<NDK>` + `-resource-dir=<NDK>/lib/clang/18`

**Key flags** (all verified working):
- `--target=aarch64-linux-android26` — target triple with API level
- `--sysroot=<NDK>/toolchains/llvm/prebuilt/linux-x86_64/sysroot` — NDK headers & libs
- `-resource-dir=<NDK>/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/18` — use NDK's
  compiler-rt builtins and libunwind instead of Termux's (critical for consistency)
- `-fPIC -DANDROID` — required for Android shared libraries

**CMake toolchain** (`app/src/main/cpp/termux-ndk-toolchain.cmake`):
- Uses `CMAKE_SYSTEM_NAME Linux` (not `Android`) to avoid CMake's built-in Android
  platform module which would try to invoke NDK's x86_64 toolchain
- Sets `ANDROID 1` cache variable so `if(ANDROID)` checks still work in CMakeLists.txt
- Uses `-resource-dir` to resolve both libunwind.a and compiler-rt builtins from NDK

**libunwind.a issue**: Termux clang v20 auto-links `-l:libunwind.a` but this file
isn't in the NDK sysroot search path. The `-resource-dir` flag solves this by making
clang look in `<NDK>/lib/clang/18/lib/linux/aarch64/` for runtime libraries.

**libc++ options**:
- Shared (default): output .so links `libc++_shared.so` — must copy NDK's
  `sysroot/usr/lib/aarch64-linux-android/libc++_shared.so` to `jniLibs/arm64-v8a/`
- Static: use `-nostdlib++` + link `libc++_static.a` + `libc++abi.a` explicitly —
  self-contained .so, no extra deployment, ~500KB larger

**Output verification**: Built .so files are correctly tagged as
`ELF 64-bit LSB shared object, ARM aarch64, for Android 26, built by NDK r27`

### SDK Setup
- Android SDK at `~/android-sdk`
- Platforms: 34, 35, 36 (36 downloaded manually)
- Build-tools: 34.0.0, 35.0.0
- Java: OpenJDK 21 (Termux)
- Gradle: 8.13 (via wrapper)

## Compromises / Known Issues
1. **Native libs not yet integrated**: cross-compilation approach verified but not yet
   wired into build-and-install.sh; cmark source not yet in external/cmark/
   - Pre-build step needed: cmake + make before Gradle, copy .so to jniLibs/arm64-v8a/
   - App runs without native libs using JBM Kotlin fallback renderer
2. **finalmarkdown has no source**: only a Kotlin JNI interface exists; library loads
   optionally and is gated behind the `useFinalMarkdownRenderer` experiment flag
3. **google-services.json placeholder**: Push notifications won't work without real Firebase config
4. **Sentry DSN empty**: Error tracking disabled in local builds
