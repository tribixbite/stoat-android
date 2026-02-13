#!/data/data/com.termux/files/usr/bin/bash

# Build script for Stoat Android on Termux ARM64
# Adapted from cleverkeys/build-on-termux.sh
# Usage: ./build-and-install.sh [debug|release] [clean]
#   debug   - Build debug APK (default)
#   release - Build release APK
#   clean   - Add as second arg to force clean build

BUILD_TYPE="${1:-debug}"
BUILD_TYPE_LOWER=$(echo "$BUILD_TYPE" | tr '[:upper:]' '[:lower:]')
CLEAN_BUILD=false
if [[ "$2" == "clean" || "$1" == "clean" ]]; then
    CLEAN_BUILD=true
    if [[ "$1" == "clean" ]]; then
        BUILD_TYPE_LOWER="debug"
    fi
fi

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ID="chat.revolt"
APP_NAME="Stoat"

echo "=== $APP_NAME Termux Build Script ==="
echo "Building $BUILD_TYPE_LOWER APK on Termux ARM64"
echo

# Validate build type
if [[ "$BUILD_TYPE_LOWER" != "debug" && "$BUILD_TYPE_LOWER" != "release" ]]; then
    echo "Error: Invalid build type '$BUILD_TYPE_LOWER'. Use 'debug' or 'release'"
    echo "Usage: $0 [debug|release] [clean]"
    exit 1
fi

# --- Environment setup ---
export ANDROID_HOME="/data/data/com.termux/files/home/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# Use Java 21 (default Termux JDK, avoids native lib conflicts)
export JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"
# NDK for native C++ builds (cmark)
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.1.10909125"

echo "Step 1: Checking prerequisites..."

# Check Java
if ! java -version &>/dev/null; then
    echo "Error: Java not found. Install with: pkg install openjdk-21"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -1)
echo "  Java: $JAVA_VER"

# Check Android SDK
if [ ! -d "$ANDROID_HOME" ]; then
    echo "Error: Android SDK not found at $ANDROID_HOME"
    exit 1
fi
echo "  Android SDK: $ANDROID_HOME"

# Check for required SDK platform
if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
    echo "Error: Android SDK Platform 36 not found."
    echo "Download from: https://dl.google.com/android/repository/platform-36_r02.zip"
    echo "Extract to: $ANDROID_HOME/platforms/"
    exit 1
fi
echo "  Platform 36: OK"

# Determine aapt2 binary to use
# Prefer bundled static ARM64 aapt2 (supports SDK 36) over Termux pkg aapt2 (SDK 34 only)
AAPT2_BUNDLED="$PROJECT_DIR/tools/aapt2-arm64/aapt2"
if [ -x "$AAPT2_BUNDLED" ] && "$AAPT2_BUNDLED" version &>/dev/null; then
    AAPT2_BIN="$AAPT2_BUNDLED"
    echo "  AAPT2: $AAPT2_BIN (native ARM64, supports SDK 36)"
elif command -v aapt2 &>/dev/null; then
    AAPT2_BIN="$(which aapt2)"
    echo "  AAPT2: $AAPT2_BIN (Termux pkg, SDK 34 max)"
    echo "  Warning: Termux aapt2 may not support SDK 35+. Place a static ARM64 aapt2 at tools/aapt2-arm64/aapt2"
else
    echo "Error: No aapt2 found. Place a static ARM64 aapt2 at tools/aapt2-arm64/aapt2"
    echo "  or install (SDK 34 only): pkg install aapt2"
    exit 1
fi

# Check NDK
if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Warning: NDK not found at $ANDROID_NDK_HOME"
    echo "Native code (cmark) may fail to build."
    echo "Install NDK with: sdkmanager 'ndk;26.1.10909125'"
fi

# Check CMake
if ! command -v cmake &>/dev/null; then
    echo "Warning: cmake not found. Install with: pkg install cmake"
    echo "Native code builds may fail."
fi

# Check stoatbuild.properties
if [ ! -f "$PROJECT_DIR/stoatbuild.properties" ]; then
    echo "Warning: stoatbuild.properties not found, creating defaults..."
    cat > "$PROJECT_DIR/stoatbuild.properties" << 'PROPS'
sentry.dsn=
sentry.upload_mappings=false
build.flavour_id=local
build.debug.app_name=Stoat Debug
PROPS
fi

# Check google-services.json
if [ ! -f "$PROJECT_DIR/app/google-services.json" ]; then
    echo "Warning: app/google-services.json not found."
    echo "Firebase push notifications will not work without a valid config."
    echo "Creating placeholder for build..."
    cat > "$PROJECT_DIR/app/google-services.json" << 'GSJSON'
{"project_info":{"project_number":"000000000000","project_id":"stoat-local","storage_bucket":"stoat-local.appspot.com"},"client":[{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000000","android_client_info":{"package_name":"chat.revolt"}},"oauth_client":[],"api_key":[{"current_key":"placeholder"}],"services":{"appinvite_service":{"other_platform_oauth_client":[]}}},{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000001","android_client_info":{"package_name":"chat.revolt.debug"}},"oauth_client":[],"api_key":[{"current_key":"placeholder"}],"services":{"appinvite_service":{"other_platform_oauth_client":[]}}}],"configuration_version":"1"}
GSJSON
fi

# Check local.properties
if [ ! -f "$PROJECT_DIR/local.properties" ]; then
    echo "Creating local.properties..."
    cat > "$PROJECT_DIR/local.properties" << LPROP
sdk.dir=$ANDROID_HOME
ndk.dir=$ANDROID_NDK_HOME
LPROP
fi

echo
echo "All prerequisites OK."
echo

# --- Clean if requested ---
if [ "$CLEAN_BUILD" = true ]; then
    echo "Step 2: Cleaning previous builds..."
    "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" clean \
        -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=512m" \
        -Pandroid.aapt2FromMavenOverride="$AAPT2_BIN" \
        --no-daemon --warning-mode=none --console=plain 2>&1 || {
        echo "Warning: Clean failed, continuing..."
    }
    echo
else
    echo "Step 2: Skipping clean (use '$0 $BUILD_TYPE_LOWER clean' to force)"
    echo
fi

# --- Build ---
if [ "$BUILD_TYPE_LOWER" = "release" ]; then
    GRADLE_TASK="assembleRelease"
    APK_DIR="$PROJECT_DIR/app/build/outputs/apk/release"
    echo "Step 3: Building Release APK..."
else
    GRADLE_TASK="assembleDebug"
    APK_DIR="$PROJECT_DIR/app/build/outputs/apk/debug"
    echo "Step 3: Building Debug APK..."
fi

echo "Gradle task: $GRADLE_TASK"
echo "This may take several minutes on first run..."
echo

# Build with Termux-specific configuration
# - Native ARM64 aapt2 (no QEMU emulation)
# - Constrained memory for mobile device
# - No daemon to save RAM
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" $GRADLE_TASK \
    -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=512m" \
    -Pandroid.aapt2FromMavenOverride="$AAPT2_BIN" \
    --no-daemon \
    --warning-mode=none \
    --console=plain \
    --parallel \
    --build-cache \
    2>&1 | tee "$PROJECT_DIR/build-${BUILD_TYPE_LOWER}.log"

BUILD_EXIT=${PIPESTATUS[0]}

# --- Check build result ---
APK_FILE=$(find "$APK_DIR" -name "*.apk" -not -name "*-androidTest*" 2>/dev/null | head -1)

if [ $BUILD_EXIT -ne 0 ] || [ -z "$APK_FILE" ]; then
    echo
    echo "=== BUILD FAILED ==="
    echo "Check build-${BUILD_TYPE_LOWER}.log for details"
    echo
    echo "Common issues on Termux ARM64:"
    echo "  1. AAPT2 errors      → ensure 'pkg install aapt2' and ARM64 override is set"
    echo "  2. OOM / memory      → close other apps, try '--no-parallel' in script"
    echo "  3. Missing SDK 36    → download platform-36 to \$ANDROID_HOME/platforms/"
    echo "  4. NDK/CMake errors  → ensure NDK 26.x and cmake are installed"
    echo "  5. Missing deps      → check internet connectivity for gradle downloads"
    exit 1
fi

echo
echo "=== BUILD SUCCESSFUL ==="
echo "APK: $APK_FILE"
ls -lh "$APK_FILE"
echo

# --- Install ---
echo "Step 4: Attempting installation..."

# Function to find and connect to ADB wireless
connect_adb_wireless() {
    local was_e
    case $- in *e*) was_e=1;; esac
    set +e

    # Get device IP
    local HOST
    HOST=$(ifconfig 2>/dev/null | awk '/wlan0/{getline; if(/inet /) print $2}')
    if [ -z "$HOST" ]; then
        HOST=$(ifconfig 2>/dev/null | awk '/inet / && !/127.0.0.1/{print $2; exit}')
    fi

    if [ -z "$HOST" ]; then
        echo "Could not determine network IP address"
        [ -n "$was_e" ] && set -e
        return 1
    fi

    echo "Scanning for ADB on: $HOST"
    adb disconnect -a >/dev/null 2>&1

    local PORTS="5555"
    if command -v nmap &>/dev/null; then
        echo "Scanning ports 30000-50000..."
        local SCANNED
        SCANNED=$(nmap -p 30000-50000 --open -oG - "$HOST" 2>/dev/null | \
            awk -F"Ports: " '/Ports:/{n=split($2,a,/, /); for(i=1;i<=n;i++){if(a[i]~/open/){split(a[i],f,"/"); print f[1]}}}')
        PORTS="$PORTS $SCANNED"
    fi

    for port in $PORTS; do
        echo -n "  Trying $HOST:$port... "
        if adb connect "$HOST:$port" >/dev/null 2>&1; then
            for i in 1 2 3; do
                sleep 0.5
                if adb devices | grep -q "^$HOST:$port[[:space:]]*device"; then
                    echo "connected!"
                    [ -n "$was_e" ] && set -e
                    return 0
                fi
            done
            echo "failed to verify"
            adb disconnect "$HOST:$port" >/dev/null 2>&1
        else
            echo "no response"
        fi
    done

    echo "No ADB connection found"
    [ -n "$was_e" ] && set -e
    return 1
}

ADB_BIN="/data/data/com.termux/files/usr/bin/adb"
INSTALLED=false

if [ -f "$ADB_BIN" ]; then
    # Check for existing ADB connection
    if "$ADB_BIN" devices 2>/dev/null | grep -q "device$"; then
        echo "ADB device detected, installing..."
        if "$ADB_BIN" install -r "$APK_FILE" 2>&1; then
            INSTALLED=true
            echo
            echo "=== APK INSTALLED SUCCESSFULLY ==="
        else
            echo "ADB install failed"
        fi
    else
        echo "No active ADB device, scanning for wireless..."
        if connect_adb_wireless; then
            echo "Installing via ADB wireless..."
            if adb install -r "$APK_FILE" 2>&1; then
                INSTALLED=true
                echo
                echo "=== APK INSTALLED SUCCESSFULLY ==="
            else
                echo "ADB wireless install failed"
            fi
        fi
    fi
fi

# Fallback: termux-open
if [ "$INSTALLED" = false ]; then
    if command -v termux-open &>/dev/null; then
        echo "Opening APK with termux-open..."
        termux-open "$APK_FILE" 2>/dev/null && INSTALLED=true
    fi
fi

# Manual fallback
if [ "$INSTALLED" = false ]; then
    echo
    echo "Could not auto-install. APK location:"
    echo "  $APK_FILE"
    echo
    echo "To install manually:"
    echo "  adb install -r $APK_FILE"
    echo "  or: termux-open $APK_FILE"
fi

echo
echo "Done. Build log: $PROJECT_DIR/build-${BUILD_TYPE_LOWER}.log"
