#!/data/data/com.termux/files/usr/bin/bash
#
# Setup script for x86_64 glibc rootfs on ARM64 Termux
# Downloads minimal Debian amd64 libraries needed to run x86_64 aapt2
# via proot + qemu-x86_64.
#
# Prerequisites: pkg install proot qemu-x86_64-static curl
# Optional: pkg install binutils (for 'ar' command)
#
# Usage: bash setup-x86_64-rootfs.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLS_DIR="$(dirname "$SCRIPT_DIR")"
ROOTFS_DIR="$TOOLS_DIR/x86_64-libs"
TEMP_DIR="$SCRIPT_DIR/tmp-debs"

# Debian bookworm amd64 package URLs
DEBIAN_MIRROR="http://ftp.us.debian.org/debian"

# Package paths in Debian pool (bookworm/amd64)
# Updated 2026-02 - verify at http://ftp.us.debian.org/debian/pool/main/
declare -A PACKAGES=(
    [libc6]="pool/main/g/glibc/libc6_2.36-9+deb12u13_amd64.deb"
    [libstdcpp6]="pool/main/g/gcc-12/libstdc++6_12.2.0-14+deb12u1_amd64.deb"
    [zlib1g]="pool/main/z/zlib/zlib1g_1.2.13.dfsg-1_amd64.deb"
    [libgcc-s1]="pool/main/g/gcc-12/libgcc-s1_12.2.0-14+deb12u1_amd64.deb"
)

echo "=== x86_64 Rootfs Setup for AAPT2 ==="
echo "Target rootfs: $ROOTFS_DIR"
echo

# ---- Check prerequisites ----
check_prereqs() {
    local missing=0
    for cmd in proot curl; do
        if ! command -v "$cmd" &>/dev/null; then
            echo "Error: '$cmd' not found."
            missing=1
        fi
    done

    # Check for qemu - either qemu-x86_64 or qemu-x86_64-static
    if command -v qemu-x86_64 &>/dev/null; then
        QEMU_BIN="$(command -v qemu-x86_64)"
    elif command -v qemu-x86_64-static &>/dev/null; then
        QEMU_BIN="$(command -v qemu-x86_64-static)"
    else
        echo "Error: Neither qemu-x86_64 nor qemu-x86_64-static found."
        echo "Install with: pkg install qemu-x86_64-static"
        missing=1
    fi

    # Check for ar or dpkg-deb (needed to extract .deb files)
    if ! command -v ar &>/dev/null && ! command -v dpkg-deb &>/dev/null; then
        echo "Error: Neither 'ar' nor 'dpkg-deb' found."
        echo "Install with: pkg install binutils"
        missing=1
    fi

    if [ $missing -ne 0 ]; then
        echo
        echo "Install missing packages and re-run."
        exit 1
    fi

    echo "Using QEMU: $QEMU_BIN"
}

# ---- Download a .deb file ----
download_deb() {
    local pkg_name="$1"
    local pkg_path="$2"
    local deb_file="$TEMP_DIR/${pkg_name}.deb"

    echo "Downloading ${pkg_name}..."

    if [ -f "$deb_file" ]; then
        echo "  Already downloaded, skipping."
        return 0
    fi

    # Try primary mirror
    if curl -fL --connect-timeout 15 -o "$deb_file" "$DEBIAN_MIRROR/$pkg_path" 2>/dev/null; then
        echo "  OK ($(du -h "$deb_file" | cut -f1))"
        return 0
    fi

    # Try deb.debian.org
    echo "  Primary mirror failed, trying deb.debian.org..."
    if curl -fL --connect-timeout 15 -o "$deb_file" "http://deb.debian.org/debian/$pkg_path" 2>/dev/null; then
        echo "  OK ($(du -h "$deb_file" | cut -f1))"
        return 0
    fi

    # Try http.debian.net
    echo "  Trying http.debian.net..."
    if curl -fL --connect-timeout 15 -o "$deb_file" "http://http.debian.net/debian/$pkg_path" 2>/dev/null; then
        echo "  OK ($(du -h "$deb_file" | cut -f1))"
        return 0
    fi

    echo "  ERROR: Failed to download ${pkg_name}"
    rm -f "$deb_file"
    return 1
}

# ---- Extract a .deb file ----
extract_deb() {
    local pkg_name="$1"
    local deb_file="$TEMP_DIR/${pkg_name}.deb"
    local extract_dir="$TEMP_DIR/${pkg_name}"

    echo "Extracting ${pkg_name}..."
    mkdir -p "$extract_dir"

    # Method 1: dpkg-deb
    if command -v dpkg-deb &>/dev/null; then
        dpkg-deb -x "$deb_file" "$extract_dir" 2>/dev/null && {
            echo "  Extracted with dpkg-deb."
            return 0
        }
    fi

    # Method 2: ar + tar
    if command -v ar &>/dev/null; then
        cd "$extract_dir"
        ar x "$deb_file" 2>/dev/null || {
            echo "  ar extraction failed"
            cd "$SCRIPT_DIR"
            return 1
        }

        # Find data tarball
        local data_tar
        data_tar="$(ls data.tar.* 2>/dev/null | head -1)"
        if [ -z "$data_tar" ]; then
            echo "  ERROR: No data.tar.* found in deb"
            cd "$SCRIPT_DIR"
            return 1
        fi

        case "$data_tar" in
            *.xz)
                if command -v xz &>/dev/null; then
                    xz -d "$data_tar" && tar xf data.tar
                else
                    tar xJf "$data_tar"
                fi
                ;;
            *.gz)   tar xzf "$data_tar" ;;
            *.zst)
                if command -v zstd &>/dev/null; then
                    zstd -d "$data_tar" -o data.tar && tar xf data.tar
                else
                    echo "  ERROR: zstd not available for $data_tar"
                    cd "$SCRIPT_DIR"
                    return 1
                fi
                ;;
            *.bz2)  tar xjf "$data_tar" ;;
            *)      tar xf "$data_tar" ;;
        esac

        cd "$SCRIPT_DIR"
        echo "  Extracted with ar + tar."
        return 0
    fi

    echo "  ERROR: No extraction method available"
    return 1
}

# ---- Copy library files, following symlinks ----
copy_lib() {
    local src_dir="$1"
    local pattern="$2"
    local dest_dir="$3"

    find "$src_dir" -name "$pattern" 2>/dev/null | while read -r f; do
        local basename
        basename="$(basename "$f")"
        # Copy the actual file (follow symlinks with -L)
        cp -fL "$f" "$dest_dir/$basename" 2>/dev/null || true
    done
}

# ---- Main ----
check_prereqs

# Create rootfs directories
mkdir -p "$ROOTFS_DIR/lib64"
mkdir -p "$ROOTFS_DIR/lib/x86_64-linux-gnu"
mkdir -p "$ROOTFS_DIR/usr/lib/x86_64-linux-gnu"
mkdir -p "$TEMP_DIR"

echo
echo "--- Downloading Debian amd64 packages ---"
echo

for pkg_name in "${!PACKAGES[@]}"; do
    download_deb "$pkg_name" "${PACKAGES[$pkg_name]}"
done

echo
echo "--- Extracting packages ---"
echo

for pkg_name in "${!PACKAGES[@]}"; do
    extract_deb "$pkg_name"
done

echo
echo "--- Installing libraries into rootfs ---"
echo

# ---- libc6: core C library + dynamic linker ----
echo "Installing libc6..."
LIBC6_DIR="$TEMP_DIR/libc6"

# Dynamic linker (ld-linux-x86-64.so.2)
LD_SO="$(find "$LIBC6_DIR" -name 'ld-linux-x86-64.so*' 2>/dev/null | head -1)"
if [ -n "$LD_SO" ]; then
    cp -fL "$LD_SO" "$ROOTFS_DIR/lib64/ld-linux-x86-64.so.2"
    echo "  ld-linux-x86-64.so.2: OK"
else
    echo "  WARNING: ld-linux-x86-64.so.2 not found in libc6!"
fi

# Core libraries from libc6
for lib in libc.so.6 libm.so.6 libdl.so.2 libpthread.so.0 librt.so.1 \
           libresolv.so.2 libnss_dns.so.2 libnss_files.so.2; do
    src="$(find "$LIBC6_DIR" -name "$lib" 2>/dev/null | head -1)"
    if [ -n "$src" ]; then
        cp -fL "$src" "$ROOTFS_DIR/lib/x86_64-linux-gnu/$lib"
        echo "  $lib: OK"
    else
        echo "  $lib: not found (may be merged into libc.so.6 in glibc 2.34+)"
    fi
done

# In glibc 2.34+, libdl/libpthread/librt are merged into libc.so.6
# Create symlinks for compatibility
for lib in libdl.so.2 libpthread.so.0 librt.so.1; do
    if [ ! -f "$ROOTFS_DIR/lib/x86_64-linux-gnu/$lib" ] && \
       [ ! -L "$ROOTFS_DIR/lib/x86_64-linux-gnu/$lib" ]; then
        echo "  Creating compat symlink: $lib -> libc.so.6"
        ln -sf libc.so.6 "$ROOTFS_DIR/lib/x86_64-linux-gnu/$lib"
    fi
done

# ---- libstdc++6: C++ standard library ----
echo "Installing libstdc++6..."
STDCPP_DIR="$TEMP_DIR/libstdcpp6"
# Look specifically in lib directories for .so files (avoid gdb python scripts)
STDCPP_LIB="$(find "$STDCPP_DIR" -path '*/lib/x86_64-linux-gnu/libstdc++.so.6*' -not -type d -not -name '*.py' 2>/dev/null | sort -V | tail -1)"
if [ -n "$STDCPP_LIB" ]; then
    cp -fL "$STDCPP_LIB" "$ROOTFS_DIR/lib/x86_64-linux-gnu/libstdc++.so.6"
    echo "  libstdc++.so.6: OK"
else
    echo "  WARNING: libstdc++.so.6 not found!"
fi

# ---- zlib1g: compression library ----
echo "Installing zlib1g..."
ZLIB_DIR="$TEMP_DIR/zlib1g"
ZLIB_LIB="$(find "$ZLIB_DIR" -name 'libz.so.1*' -not -type d 2>/dev/null | sort -V | tail -1)"
if [ -n "$ZLIB_LIB" ]; then
    cp -fL "$ZLIB_LIB" "$ROOTFS_DIR/lib/x86_64-linux-gnu/libz.so.1"
    echo "  libz.so.1: OK"
else
    echo "  WARNING: libz.so.1 not found!"
fi

# ---- libgcc_s: GCC runtime ----
echo "Installing libgcc-s1..."
LIBGCC_DIR="$TEMP_DIR/libgcc-s1"
LIBGCC_LIB="$(find "$LIBGCC_DIR" -name 'libgcc_s.so.1*' -not -type d 2>/dev/null | sort -V | tail -1)"
if [ -n "$LIBGCC_LIB" ]; then
    cp -fL "$LIBGCC_LIB" "$ROOTFS_DIR/lib/x86_64-linux-gnu/libgcc_s.so.1"
    echo "  libgcc_s.so.1: OK"
else
    echo "  WARNING: libgcc_s.so.1 not found!"
fi

echo
echo "--- Verifying rootfs ---"
echo

MISSING=0
for required_lib in \
    "lib64/ld-linux-x86-64.so.2" \
    "lib/x86_64-linux-gnu/libc.so.6" \
    "lib/x86_64-linux-gnu/libm.so.6" \
    "lib/x86_64-linux-gnu/libdl.so.2" \
    "lib/x86_64-linux-gnu/libpthread.so.0" \
    "lib/x86_64-linux-gnu/libstdc++.so.6" \
    "lib/x86_64-linux-gnu/libz.so.1" \
    "lib/x86_64-linux-gnu/libgcc_s.so.1" \
; do
    if [ -f "$ROOTFS_DIR/$required_lib" ] || [ -L "$ROOTFS_DIR/$required_lib" ]; then
        local_size=""
        if [ -f "$ROOTFS_DIR/$required_lib" ] && [ ! -L "$ROOTFS_DIR/$required_lib" ]; then
            local_size=" ($(du -h "$ROOTFS_DIR/$required_lib" | cut -f1))"
        elif [ -L "$ROOTFS_DIR/$required_lib" ]; then
            local_size=" -> $(readlink "$ROOTFS_DIR/$required_lib")"
        fi
        echo "  OK: $required_lib$local_size"
    else
        echo "  MISSING: $required_lib"
        MISSING=$((MISSING + 1))
    fi
done

if [ $MISSING -gt 0 ]; then
    echo
    echo "WARNING: $MISSING required libraries are missing."
    echo "The wrapper may not work correctly."
    echo "Check $TEMP_DIR for extracted files and copy manually."
    echo
    echo "Debugging tips:"
    echo "  ls -laR $TEMP_DIR/*/lib/"
    echo "  ls -laR $TEMP_DIR/*/usr/lib/"
    exit 1
else
    echo
    echo "All required libraries present."
fi

# Show total rootfs size
echo
TOTAL_SIZE="$(du -sh "$ROOTFS_DIR" | cut -f1)"
echo "Total rootfs size: $TOTAL_SIZE"

echo
echo "Temp files in: $TEMP_DIR"
echo "Run 'rm -rf $TEMP_DIR' to clean up after verifying everything works."
echo
echo "=== Setup complete ==="
echo "Rootfs at: $ROOTFS_DIR"
echo "Next: Run the wrapper to test:"
echo "  $TOOLS_DIR/aapt2-x86_64/aapt2-wrapper version"
