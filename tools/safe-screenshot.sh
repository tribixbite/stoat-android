#!/bin/bash
# Safe screenshot helper - ensures no dimension >= 2000px and size < 4MB
# Usage: ./tools/safe-screenshot.sh [output_name] [adb_device]
# Output: $TMPDIR/sc_safe.png (always) + optional named copy

NAME="${1:-screenshot}"
DEV="${2:-$(adb devices | grep -v offline | grep 'device$' | head -1 | awk '{print $1}')}"

if [ -z "$DEV" ]; then
    echo "ERROR: No ADB device found"
    exit 1
fi

REMOTE="/sdcard/sc_${NAME}.png"
RAW="$TMPDIR/sc_raw.png"
SAFE="$TMPDIR/sc_safe.png"

# Capture and pull
adb -s "$DEV" shell screencap -p "$REMOTE" || { echo "ERROR: screencap failed"; exit 1; }
adb -s "$DEV" pull "$REMOTE" "$RAW" 2>/dev/null || { echo "ERROR: pull failed"; exit 1; }

# Check dimensions, resize if needed (max 1999px on any side, max 4MB)
python3 -c "
from PIL import Image
import shutil, os

img = Image.open('$RAW')
w, h = img.size
fsize = os.path.getsize('$RAW')

needs_resize = w >= 2000 or h >= 2000 or fsize > 4000000

if needs_resize:
    # Scale so max dimension = 1999
    ratio = min(1999 / max(w, h), 1.0)
    nw, nh = int(w * ratio), int(h * ratio)
    img = img.resize((nw, nh), Image.LANCZOS)
    img.save('$SAFE', optimize=True)
    print(f'{nw}x{nh} (resized from {w}x{h})')
else:
    shutil.copy('$RAW', '$SAFE')
    print(f'{w}x{h} (ok)')
" 2>&1

# Verify output
if [ -f "$SAFE" ]; then
    SIZE=$(stat -c%s "$SAFE" 2>/dev/null)
    echo "Output: $SAFE ($SIZE bytes)"
else
    echo "ERROR: safe screenshot not created"
    exit 1
fi
