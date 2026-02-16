# Autumn Image Upload Spec

Reference for the Revolt/Stoat Autumn file server image upload constraints,
format handling, aspect ratios, and animation support. Derived from reading
the actual Autumn source code in `revolt-backend` (Rust) and the
`revolt-frontend` (SolidJS) codebase.

---

## Upload Tags & Constraints

| Tag | Max Size | Max MP | Max Pixel Side | Preview Bounding Box | Display Aspect Ratio (frontend) |
|---|---|---|---|---|---|
| `avatars` | 4 MB | 40 | 10,000 px | 128 × 128 | 1:1 |
| `icons` | 2.5 MB | 40 | 10,000 px | 128 × 128 | 1:1 |
| `emojis` | 500 KB | 40 | 10,000 px | 128 × 128 | 1:1 |
| `banners` | 6 MB | 40 | 10,000 px | 480 × 480 | 232:100 (2.32:1) |
| `backgrounds` | 6 MB | 40 | 10,000 px | 1280 × 720 | 232:100 (2.32:1) |
| `attachments` | 20 MB | 40 | 10,000 px | 1280 × 1280 | (original) |

**Sources:**
- Config: `revolt-backend/crates/core/config/Revolt.toml` lines 151–168
- Upload constraints: `autumn/src/api.rs` lines 147–157
- Frontend aspect: `revolt-frontend` `UserProfileEditor.tsx:164`, `Overview.tsx:231`

### Preview Bounding Box vs Display Aspect Ratio

The preview bounding box is the max dimensions for the thumbnail WebP. The
`image.thumbnail(w.min(box_w), h.min(box_h))` call preserves the original
aspect ratio, fitting within the box. It does NOT enforce a specific ratio.

The display aspect ratio is what the frontend uses for the picker preview and
profile card rendering. The server stores and serves the original file —
cropping is a client responsibility.

### Background Aspect Ratio

**Recommended: 232:100 (2.32:1)**

Both the web frontend profile editor and server settings banner picker use
`imageAspect="232/100"`. The `ProfileBanner` component renders with
`height: 120px` and `backgroundSize: cover` + `backgroundPosition: center`,
meaning images wider or taller than 2.32:1 get center-cropped by CSS.

Our Android `RawUserOverview` displays backgrounds at 128dp height, full width,
`ContentScale.FillWidth` — this means the display ratio varies with screen
width (typically ~2.5:1 on phones in portrait). We should offer a 2.32:1 crop
dialog to match the web client.

### Server Banner Aspect Ratio

**Recommended: 232:100 (2.32:1)**

Our current implementation uses 2.5 (5:2 = 480:192). The web frontend also
uses 232:100 for server banners (`Overview.tsx:231`). Consider updating
`AutumnUploadType.BANNER` from 2.5f to 2.32f.

---

## Accepted MIME Types (server-side)

### Image types accepted for ALL non-attachment tags

From `autumn/src/metadata.rs`:

```
image/avif, image/bmp, image/gif, image/x-icon (ico),
image/jpeg, image/jxl, image/png, image/tiff, image/webp
```

9 total. SVG (`image/svg+xml`) is supported for decoding but not listed in the
upload MIME allowlist for general tags.

### Attachments

Attachments accept ANY file type (not restricted to images). Non-image
attachments don't get previews.

### MIME detection

Uses the `infer` crate (magic byte detection), not file extension.
Special cases for `.apk`, `.exe`, `.svg`, plain text fallback.

---

## Image Processing Pipeline (server-side)

### On upload
1. **MIME detection** via `infer` crate magic bytes
2. **Metadata extraction** — dimensions via `imagesize` crate
3. **Validation** — decode test to ensure image is valid
4. **EXIF stripping** (JPEG, PNG, AVIF, TIFF only) — re-encodes to strip EXIF
   and apply orientation. **GIF, WebP, JXL pass through UNCHANGED.**
5. Store original file to S3

### On preview request (`GET /{tag}/{file_id}`)
1. Check if animated GIF (`content_type == "image/gif"`)
2. **If animated GIF AND tag is NOT avatars/icons:**
   → Redirect to original file (HTTP 301), preserving animation
3. **Otherwise:**
   → Decode image, `thumbnail()` to preview bounding box, encode as WebP
4. Serve with `Content-Type: image/webp`, `Cache-Control: public, max-age=604800`

### On original request (`GET /{tag}/{file_id}/{file_name}`)
- Serves original file as-is with `Content-Disposition: attachment`
- Using `original` as filename redirects to original

---

## Animation Support

### Summary Table

| Format | Avatars | Icons | Emojis | Banners | Backgrounds |
|---|---|---|---|---|---|
| **GIF (animated)** | Static (thumbnailed) | Static (thumbnailed) | **Animated** (redirect) | **Animated** (redirect) | **Animated** (redirect) |
| **WebP (animated)** | Static | Static | Static | Static | Static |
| **APNG** | Static | Static | Static | Static | Static |

### Animated GIF handling

**Source:** `api.rs:383-392`

```rust
let is_animated = hash.content_type == "image/gif";
// animated GIFs for non-avatar/non-icon tags redirect to original:
if !matches!(hash.metadata, Metadata::Image { .. })
    || (is_animated && !matches!(tag, Tag::avatars | Tag::icons))
{
    return Ok(Redirect::permanent(&format!("/{tag_str}/{file_id}/{}", file.filename)));
}
```

For emojis, banners, and backgrounds: animated GIFs are served at full
resolution from the original file. The preview URL redirects to the original.

For avatars and icons: animated GIFs go through thumbnail decode, which
extracts the first frame only (`image::DynamicImage` doesn't preserve GIF
frames).

### Animated WebP — SERVER BUG

The server checks animation **only** via `content_type == "image/gif"`.
Animated WebP files have `content_type == "image/webp"` and are NOT detected
as animated. They go through the thumbnail path, which extracts the first
frame via `image::DynamicImage::thumbnail()`.

**Result:** Animated WebP loses animation on preview for ALL tags.

The code has a `// TODO: extract this data from files` comment acknowledging
this limitation.

### APNG (Animated PNG)

Same as animated WebP — not detected as animated by the `image/gif` check.
Goes through thumbnail path, first frame extracted. Animation lost for all
tags.

### Emoji `animated` field

The Emoji schema includes an `animated: bool` field. This appears to be set
based on the original file's content type being `image/gif`. It could be used
by clients to add an animation badge or toggle.

---

## Our Client (Stoat Android) — Current Behavior

### Processing pipeline

All images go through `ImageProcessor.processForUpload()`:
1. Decode with `inSampleSize` downsampling
2. Apply EXIF rotation
3. Center-crop to target aspect ratio (if set)
4. Resize to max dimension
5. Compress to **static WebP** (lossy, iterative quality 90→10→5)

### Consequences

- **Animated GIFs are destroyed.** We convert to static WebP before upload.
  For emojis, this means animated emojis are impossible through our client
  even though the server fully supports them.
- **Animated WebP is destroyed.** Same — converted to static WebP.
- **APNG is destroyed.** Same — first frame only after decode.
- **MozJPEG** — works fine. JPEG is JPEG regardless of encoder. Server accepts
  it, we decode it correctly via BitmapFactory.

### Our constraints vs server

| Tag | Our maxDim | Server max side | Our maxBytes | Server maxBytes | Match? |
|---|---|---|---|---|---|
| avatars | 1024 | 10,000 | 4 MB | 4 MB | Size matches, res stricter (good) |
| icons | 1024 | 10,000 | 2.5 MB | 2.5 MB | Size matches, res stricter (good) |
| emojis | 512 | 10,000 | 500 KB | 500 KB | Size matches, res stricter (good) |
| banners | 2048 | 10,000 | 6 MB | 6 MB | Size matches, res stricter (good) |
| backgrounds | 2048 | 10,000 | 6 MB | 6 MB | Size matches, res stricter (good) |
| attachments | 4096 | 10,000 | 20 MB | 20 MB | Size matches, res stricter (good) |

Our constraints are correct. We're more restrictive on resolution (which saves
bandwidth and processing time) while matching the server's file size limits
exactly.

---

## Issues & Recommendations

### 1. Animated emoji support (HIGH PRIORITY)

**Problem:** We convert all images to static WebP, destroying GIF animation.
The server preserves animated GIFs for emojis.

**Fix:** Detect if source is animated GIF. If so, skip the WebP conversion
pipeline — upload the original GIF directly (after optional resize via
re-encoding with a GIF library or just byte passthrough if under 500KB).

**Note:** Android's `BitmapFactory` only decodes the first frame of GIFs.
To preserve animation we'd need to:
- Check if the file is an animated GIF (read GIF89a header + check for
  multiple image blocks)
- If animated AND under 500KB, upload the original file directly
- If animated AND over 500KB, could try `ImageDecoder` (API 28+) to resize
  the animated GIF, or simply reject with a size warning

### 2. Banner aspect ratio mismatch (LOW PRIORITY)

**Problem:** We use 2.5f (5:2), frontend uses 2.32 (232:100).

**Fix:** Change `AutumnUploadType.BANNER` targetAspectRatio from `2.5f` to
`2.32f`. Crop dialog already supports any float ratio.

### 3. Background crop dialog (MEDIUM PRIORITY)

**Problem:** Profile backgrounds have `targetAspectRatio = null` so no crop
dialog is shown. Users can upload any ratio, but the display crops to the
banner area (128dp height, full width ≈ 2.5:1 on phones).

**Fix:** Change `BACKGROUND` targetAspectRatio to `2.32f` to match the web
client. This enables the crop dialog for backgrounds. The server's preview
bounding box (1280×720 = 16:9) will fit any 2.32:1 image within it.

### 4. Animated WebP — upstream bug (INFORMATIONAL)

Server doesn't detect animated WebP. This is an upstream issue (`api.rs:383`).
We can't fix this server-side. For now, animated WebP emojis won't animate
even if we skip our WebP conversion, because the server's preview route would
still thumbnail them (extracting first frame only). The workaround is to use
GIF format for animated content.

### 5. EXIF stripping coverage (INFORMATIONAL)

Server strips EXIF from JPEG, PNG, AVIF, TIFF. GIF and WebP pass through
unchanged (no EXIF stripping). Our client applies EXIF rotation before upload,
so the resulting WebP has correct orientation regardless.

---

## Format Decision Matrix

| Source Format | Animated? | Recommended Upload Format | Reason |
|---|---|---|---|
| JPEG / MozJPEG | No | WebP (current) | Smaller, server accepts |
| PNG | No | WebP (current) | Smaller, server accepts |
| WebP (static) | No | WebP (current) | Already optimal |
| GIF (static) | No | WebP (current) | Smaller than GIF |
| GIF (animated) | Yes | **GIF (original)** | Server preserves animation |
| WebP (animated) | Yes | **GIF (re-encode)** | Server animated WebP bug |
| APNG | Yes | **GIF (re-encode)** | Server doesn't detect APNG animation |
| BMP / TIFF | No | WebP (current) | Much smaller |

For non-animated content, our current WebP conversion is correct and optimal.
For animated content destined for emojis/banners/backgrounds, GIF is the only
format where the server preserves animation.
