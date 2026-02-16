package com.tribixbite.stoatally.api.routes.microservices.autumn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.provider.OpenableColumns
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * A single frame from an animated image, with its display duration.
 */
data class AnimFrame(
    val bitmap: Bitmap,
    val delayMs: Int
)

/**
 * Utilities for detecting and extracting frames from animated images (GIF, WebP).
 * Used to preserve animation when uploading emojis, banners, and backgrounds
 * to the Autumn file server, which only preserves animation for GIF format.
 */
object AnimatedImageUtils {
    private const val TAG = "AnimatedImageUtils"

    /**
     * Check if a URI points to an animated GIF (GIF89a with multiple image blocks).
     */
    fun isAnimatedGif(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                isAnimatedGifStream(stream)
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check animated GIF", e)
            false
        }
    }

    /**
     * Check if a URI points to an animated WebP (RIFF container with ANIM chunk).
     */
    fun isAnimatedWebP(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                isAnimatedWebPStream(stream)
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check animated WebP", e)
            false
        }
    }

    /**
     * Check if a URI points to any animated image format (GIF or WebP).
     */
    fun isAnimated(context: Context, uri: Uri): Boolean {
        return isAnimatedGif(context, uri) || isAnimatedWebP(context, uri)
    }

    /**
     * Extract frames from an animated GIF by parsing the GIF89a format.
     * Returns individual frames with their delay times.
     *
     * This parser handles standard GIF89a features:
     * - Global and local color tables
     * - Graphic control extensions (delay, disposal, transparency)
     * - Frame compositing with disposal methods
     *
     * @param maxFrames Maximum number of frames to extract (prevents OOM on long animations)
     * @param maxDimension Maximum dimension for decoded frames (downscales if larger)
     */
    fun extractGifFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = 100,
        maxDimension: Int = 512
    ): List<AnimFrame> {
        // Use android.graphics.Movie for GIF frame extraction (works on all API levels)
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return emptyList()

            @Suppress("DEPRECATION")
            val movie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
                ?: return emptyList()

            val movieWidth = movie.width()
            val movieHeight = movie.height()
            if (movieWidth <= 0 || movieHeight <= 0) return emptyList()

            val duration = movie.duration().coerceAtLeast(1)

            // Determine scaling
            val scale = if (maxOf(movieWidth, movieHeight) > maxDimension) {
                maxDimension.toFloat() / maxOf(movieWidth, movieHeight)
            } else 1f
            val outW = (movieWidth * scale).toInt().coerceAtLeast(1)
            val outH = (movieHeight * scale).toInt().coerceAtLeast(1)

            // Extract frames by stepping through the movie timeline.
            // Detect unique frames by checking when the rendered output changes.
            val frames = mutableListOf<AnimFrame>()
            var lastFrameHash = 0L
            val stepMs = 20 // Sample every 20ms (50fps max)
            var lastChangeTime = 0

            var time = 0
            while (time <= duration && frames.size < maxFrames) {
                movie.setTime(time)
                val frameBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frameBmp)
                if (scale != 1f) canvas.scale(scale, scale)
                movie.draw(canvas, 0f, 0f)

                // Compute a simple hash of the frame to detect changes
                val hash = frameBitmapHash(frameBmp)
                if (hash != lastFrameHash || frames.isEmpty()) {
                    // New unique frame
                    if (frames.isNotEmpty()) {
                        // Update previous frame's delay to the time since last change
                        val prevDelay = (time - lastChangeTime).coerceAtLeast(20)
                        frames[frames.lastIndex] = frames.last().copy(delayMs = prevDelay)
                    }
                    frames.add(AnimFrame(frameBmp, stepMs))
                    lastFrameHash = hash
                    lastChangeTime = time
                } else {
                    frameBmp.recycle()
                }

                time += stepMs
            }

            // Fix last frame delay
            if (frames.size > 1) {
                val lastDelay = (duration - lastChangeTime).coerceAtLeast(20)
                frames[frames.lastIndex] = frames.last().copy(delayMs = lastDelay)
            } else if (frames.size == 1) {
                // Single frame = not really animated
                frames[0].bitmap.recycle()
                return emptyList()
            }

            frames
        } catch (e: Throwable) {
            Log.e(TAG, "GIF frame extraction failed", e)
            emptyList()
        }
    }

    /**
     * Extract frames from an animated WebP using ImageDecoder (API 28+).
     * Falls back to empty list on older API levels.
     */
    suspend fun extractWebPFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = 100,
        maxDimension: Int = 512
    ): List<AnimFrame> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "Animated WebP extraction requires API 28+")
            return emptyList()
        }

        return try {
            extractFramesViaImageDecoder(context, uri, maxFrames, maxDimension)
        } catch (e: Throwable) {
            Log.e(TAG, "WebP frame extraction failed", e)
            emptyList()
        }
    }

    /**
     * Extract frames from any animated image. Tries GIF first, then WebP.
     * Must be called from a coroutine context (WebP extraction uses delay()).
     */
    suspend fun extractFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = 100,
        maxDimension: Int = 512
    ): List<AnimFrame> {
        if (isAnimatedGif(context, uri)) {
            return extractGifFrames(context, uri, maxFrames, maxDimension)
        }
        if (isAnimatedWebP(context, uri)) {
            return extractWebPFrames(context, uri, maxFrames, maxDimension)
        }
        return emptyList()
    }

    /**
     * Get the file size of a URI via ContentResolver metadata query.
     * Falls back to reading the full stream if OpenableColumns not supported.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            // Prefer ContentResolver query for accurate size
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else null
            } ?: context.contentResolver.openInputStream(uri)?.use {
                // Fallback: count actual bytes
                it.readBytes().size.toLong()
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get file size", e)
            0L
        }
    }

    /**
     * Copy a URI's content to a file. Used for pass-through of animated GIFs
     * that are already under the size limit and don't need cropping.
     */
    fun copyUriToFile(context: Context, uri: Uri, outFile: java.io.File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to file", e)
            false
        }
    }

    // -- Internal: format detection --

    /**
     * Check GIF89a header and count image descriptor blocks (0x2C).
     * A GIF is animated if it has more than one image block.
     */
    private fun isAnimatedGifStream(stream: InputStream): Boolean {
        val header = ByteArray(6)
        if (stream.read(header) != 6) return false
        val sig = String(header, Charsets.US_ASCII)
        if (sig != "GIF87a" && sig != "GIF89a") return false

        // Skip logical screen descriptor (7 bytes)
        val lsd = ByteArray(7)
        if (stream.read(lsd) != 7) return false
        val hasGct = (lsd[4].toInt() and 0x80) != 0
        val gctSize = if (hasGct) 3 * (1 shl ((lsd[4].toInt() and 0x07) + 1)) else 0
        // Skip Global Color Table
        if (gctSize > 0) stream.skip(gctSize.toLong())

        var imageBlockCount = 0
        while (true) {
            val marker = stream.read()
            if (marker == -1 || marker == 0x3B) break // EOF or trailer

            when (marker) {
                0x2C -> {
                    // Image descriptor
                    imageBlockCount++
                    if (imageBlockCount >= 2) return true // Definitely animated

                    // Skip image descriptor fields (9 bytes remaining)
                    val desc = ByteArray(9)
                    if (stream.read(desc) != 9) return false
                    val hasLct = (desc[8].toInt() and 0x80) != 0
                    val lctSize = if (hasLct) 3 * (1 shl ((desc[8].toInt() and 0x07) + 1)) else 0
                    if (lctSize > 0) stream.skip(lctSize.toLong())

                    // Skip LZW minimum code size
                    stream.read()
                    // Skip sub-blocks
                    skipSubBlocks(stream)
                }
                0x21 -> {
                    // Extension block — skip
                    stream.read() // Extension label
                    skipSubBlocks(stream)
                }
                else -> {
                    // Unknown block, try to continue
                }
            }
        }
        return false
    }

    /**
     * Check RIFF/WebP header for ANIM chunk (indicates animation).
     */
    private fun isAnimatedWebPStream(stream: InputStream): Boolean {
        val header = ByteArray(12)
        if (stream.read(header) != 12) return false

        // Check RIFF signature and WEBP format
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val webp = String(header, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || webp != "WEBP") return false

        // Scan for ANIM or ANMF chunk
        val chunkHeader = ByteArray(8)
        while (stream.read(chunkHeader) == 8) {
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            if (chunkId == "ANIM" || chunkId == "ANMF") return true

            // Skip chunk data (little-endian size)
            val size = (chunkHeader[4].toInt() and 0xFF) or
                ((chunkHeader[5].toInt() and 0xFF) shl 8) or
                ((chunkHeader[6].toInt() and 0xFF) shl 16) or
                ((chunkHeader[7].toInt() and 0xFF) shl 24)
            // RIFF chunks are padded to even size
            val paddedSize = (size.toLong() + 1) and 1.inv()
            stream.skip(paddedSize)
        }
        return false
    }

    /** Skip GIF sub-blocks (sequences of size+data terminated by zero-length block) */
    private fun skipSubBlocks(stream: InputStream) {
        while (true) {
            val size = stream.read()
            if (size <= 0) break
            stream.skip(size.toLong())
        }
    }

    /**
     * Use ImageDecoder (API 28+) to extract frames from animated images.
     * Works for both animated GIF and WebP via the platform decoder.
     * Must be called from a coroutine context (uses delay() for frame stepping).
     */
    private suspend fun extractFramesViaImageDecoder(
        context: Context,
        uri: Uri,
        maxFrames: Int,
        maxDimension: Int
    ): List<AnimFrame> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()

        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val drawable = ImageDecoder.decodeDrawable(source)
        if (drawable !is AnimatedImageDrawable) return emptyList()

        val drawW = drawable.intrinsicWidth
        val drawH = drawable.intrinsicHeight
        if (drawW <= 0 || drawH <= 0) return emptyList()

        // Scale to fit maxDimension
        val scale = if (maxOf(drawW, drawH) > maxDimension) {
            maxDimension.toFloat() / maxOf(drawW, drawH)
        } else 1f
        val outW = (drawW * scale).toInt().coerceAtLeast(1)
        val outH = (drawH * scale).toInt().coerceAtLeast(1)

        drawable.setBounds(0, 0, outW, outH)

        // Extract frames by rendering at time steps
        // AnimatedImageDrawable doesn't expose frame count or durations directly,
        // so we render at small intervals and detect unique frames by content hash
        val frames = mutableListOf<AnimFrame>()
        var lastHash = 0L
        var lastChangeTime = 0
        val stepMs = 20

        // Estimate total duration (render until we detect a loop)
        drawable.start()
        val maxDuration = 10_000 // Cap at 10 seconds

        var time = 0
        while (time <= maxDuration && frames.size < maxFrames) {
            val frameBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBmp)
            drawable.draw(canvas)

            val hash = frameBitmapHash(frameBmp)
            if (hash != lastHash || frames.isEmpty()) {
                if (frames.isNotEmpty()) {
                    val prevDelay = (time - lastChangeTime).coerceAtLeast(20)
                    frames[frames.lastIndex] = frames.last().copy(delayMs = prevDelay)
                }
                frames.add(AnimFrame(frameBmp, stepMs))
                lastHash = hash
                lastChangeTime = time
            } else {
                frameBmp.recycle()
                // If we've seen at least 2 frames and we're getting repeats,
                // the animation has likely looped
                if (frames.size >= 2) break
            }

            // Advance time — AnimatedImageDrawable auto-advances when started
            delay(stepMs.toLong())
            time += stepMs
        }

        drawable.stop()

        // Fix last frame delay
        if (frames.size > 1) {
            val lastDelay = (time - lastChangeTime).coerceAtLeast(20)
            frames[frames.lastIndex] = frames.last().copy(delayMs = lastDelay)
        } else if (frames.size == 1) {
            frames[0].bitmap.recycle()
            return emptyList()
        }

        return frames
    }

    /**
     * Simple perceptual hash of a bitmap for frame uniqueness detection.
     * Samples a grid of pixels and combines into a long hash.
     */
    private fun frameBitmapHash(bitmap: Bitmap): Long {
        var hash = 0L
        val step = maxOf(1, bitmap.width / 8)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                hash = hash xor (bitmap.getPixel(x, y).toLong() * 31 + x * 37 + y * 41)
                hash = (hash shl 7) or (hash ushr 57) // Rotate
                x += step
            }
            y += step
        }
        return hash
    }
}
