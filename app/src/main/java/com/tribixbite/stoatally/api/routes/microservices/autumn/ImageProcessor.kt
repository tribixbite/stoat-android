package com.tribixbite.stoatally.api.routes.microservices.autumn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Autumn upload tag → constraints enforced before upload.
 * Sizes from Revolt Autumn file server configuration.
 */
enum class AutumnUploadType(
    val tag: String,
    val maxBytes: Long,
    val maxDimension: Int,
    val targetAspectRatio: Float?, // null = no forced crop
    val description: String
) {
    AVATAR("avatars", 4_000_000L, 1024, 1f, "Avatar"),
    ICON("icons", 2_500_000L, 1024, 1f, "Server icon"),
    BANNER("banners", 6_000_000L, 2048, 2.5f, "Server banner"), // ~5:2 aspect
    EMOJI("emojis", 500_000L, 512, 1f, "Custom emoji"),
    BACKGROUND("backgrounds", 6_000_000L, 2048, null, "Profile background"),
    ATTACHMENT("attachments", 20_000_000L, 4096, null, "Attachment");

    companion object {
        fun fromTag(tag: String): AutumnUploadType? = entries.find { it.tag == tag }
    }
}

/**
 * Result of image processing — contains the processed file ready for upload,
 * plus metadata for preview display.
 */
data class ProcessedImage(
    val file: File,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String
)

/**
 * Processes images for Autumn upload — handles decoding, EXIF rotation,
 * center-crop to target aspect ratio, resize to max dimensions,
 * WebP compression, and file size enforcement.
 */
object ImageProcessor {
    private const val TAG = "ImageProcessor"

    /**
     * Process an image URI for a specific upload type.
     * Returns a [ProcessedImage] ready for upload to Autumn, or null on failure.
     *
     * @param context Android context for content resolver access
     * @param uri Source image URI from picker
     * @param uploadType The Autumn upload category (determines constraints)
     * @param cacheDir Directory for writing processed output
     */
    fun processForUpload(
        context: Context,
        uri: Uri,
        uploadType: AutumnUploadType,
        cacheDir: File = context.cacheDir
    ): ProcessedImage? {
        // Track all intermediate bitmaps for cleanup — recycle each as soon as
        // possible to minimize peak memory (critical for large banners/backgrounds).
        var bitmap: Bitmap? = null
        var rotated: Bitmap? = null
        var cropped: Bitmap? = null
        var resized: Bitmap? = null
        try {
            // Step 1: Decode bitmap with downsampling for very large images
            bitmap = decodeSampledBitmap(context, uri, uploadType.maxDimension)
                ?: run {
                    Log.e(TAG, "Failed to decode bitmap from $uri")
                    return null
                }

            // Step 2: Apply EXIF rotation
            rotated = applyExifRotation(context, uri, bitmap)
            if (rotated !== bitmap) { bitmap.recycle(); bitmap = null }

            // Step 3: Center-crop to target aspect ratio (if required)
            val rotatedBmp = rotated ?: return null
            cropped = uploadType.targetAspectRatio?.let { ratio ->
                centerCrop(rotatedBmp, ratio)
            } ?: rotatedBmp
            if (cropped !== rotated) { rotated?.recycle(); rotated = null }

            // Step 4: Resize to max dimensions
            val croppedBmp = cropped ?: return null
            resized = resizeToMax(croppedBmp, uploadType.maxDimension)
            if (resized !== cropped) { cropped?.recycle(); cropped = null }

            // Step 5: Compress as WebP with file size enforcement
            val resizedBmp = resized ?: return null
            val outputFile = File(cacheDir, "processed_${System.currentTimeMillis()}.webp")
            val compressed = compressToWebP(resizedBmp, outputFile, uploadType.maxBytes)

            val finalWidth = resizedBmp.width
            val finalHeight = resizedBmp.height

            // Recycle the final bitmap before returning
            resizedBmp.recycle(); resized = null

            if (!compressed) {
                Log.e(TAG, "Failed to compress within ${uploadType.maxBytes} bytes")
                outputFile.delete()
                return null
            }

            return ProcessedImage(
                file = outputFile,
                width = finalWidth,
                height = finalHeight,
                sizeBytes = outputFile.length(),
                mimeType = "image/webp"
            )
        } catch (e: Throwable) {
            // Catch Throwable (not just Exception) to handle OutOfMemoryError —
            // large images (especially banners at 2048px) can OOM during processing.
            Log.e(TAG, "Image processing failed: ${e.javaClass.simpleName}", e)
            return null
        } finally {
            // Ensure all intermediate bitmaps are freed even on error
            listOfNotNull(bitmap, rotated, cropped, resized)
                .distinct()
                .filter { !it.isRecycled }
                .forEach { it.recycle() }
        }
    }

    /**
     * Decode bitmap with inSampleSize to avoid OOM on large images.
     * Targets maxDimension as the largest side.
     */
    private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        // First pass: get dimensions without allocating pixels
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // Calculate sample size (power of 2 downsampling)
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        options.inJustDecodeBounds = false

        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val largerDimension = maxOf(width, height)
        // Downsample when decoded size would exceed 1.5x the target dimension.
        // This prevents OOM on large camera photos (e.g. 4000x3000 at maxDim=2048
        // would decode at full size = 48MB ARGB_8888) while preserving quality for
        // images that are only slightly above the target.
        val threshold = maxDimension * 3 / 2
        while (largerDimension / sampleSize > threshold) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Process a pre-cropped bitmap for upload — skips decode/EXIF/center-crop,
     * goes straight to resize + WebP compress. Used by ImageCropDialog which
     * provides an already-cropped bitmap from user interaction.
     */
    fun processForUploadBitmap(
        bitmap: Bitmap,
        uploadType: AutumnUploadType,
        cacheDir: File
    ): ProcessedImage? {
        var resized: Bitmap? = null
        try {
            // Resize to max dimensions (no decode/EXIF/crop needed — already handled)
            resized = resizeToMax(bitmap, uploadType.maxDimension)

            val resizedBmp = resized ?: return null
            val outputFile = File(cacheDir, "processed_${System.currentTimeMillis()}.webp")
            val compressed = compressToWebP(resizedBmp, outputFile, uploadType.maxBytes)

            val finalWidth = resizedBmp.width
            val finalHeight = resizedBmp.height

            // Recycle only if we created a new bitmap during resize
            if (resized !== bitmap) { resizedBmp.recycle(); resized = null }

            if (!compressed) {
                Log.e(TAG, "Failed to compress within ${uploadType.maxBytes} bytes")
                outputFile.delete()
                return null
            }

            return ProcessedImage(
                file = outputFile,
                width = finalWidth,
                height = finalHeight,
                sizeBytes = outputFile.length(),
                mimeType = "image/webp"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Bitmap processing failed: ${e.javaClass.simpleName}", e)
            return null
        } finally {
            if (resized != null && resized !== bitmap && !resized.isRecycled) {
                resized.recycle()
            }
        }
    }

    /**
     * Read EXIF orientation and rotate/flip bitmap accordingly.
     * Many phone cameras embed rotation in EXIF rather than pixel data.
     * Internal visibility so ImageCropDialog can reuse for EXIF-aware preview.
     */
    internal fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap // No rotation needed
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.w(TAG, "EXIF rotation failed, using original", e)
            bitmap
        }
    }

    /**
     * Center-crop bitmap to the target aspect ratio.
     * Crops the longer axis to match the ratio, keeping content centered.
     */
    private fun centerCrop(bitmap: Bitmap, targetRatio: Float): Bitmap {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (kotlin.math.abs(srcRatio - targetRatio) < 0.01f) return bitmap // Already correct

        val (cropWidth, cropHeight) = if (srcRatio > targetRatio) {
            // Source is wider — crop horizontally
            val newWidth = (bitmap.height * targetRatio).roundToInt()
                .coerceIn(1, bitmap.width)
            newWidth to bitmap.height
        } else {
            // Source is taller — crop vertically
            val newHeight = (bitmap.width / targetRatio).roundToInt()
                .coerceIn(1, bitmap.height)
            bitmap.width to newHeight
        }

        // Ensure crop region stays within bitmap bounds: x+w ≤ width, y+h ≤ height
        val x = ((bitmap.width - cropWidth) / 2).coerceIn(0, bitmap.width - cropWidth)
        val y = ((bitmap.height - cropHeight) / 2).coerceIn(0, bitmap.height - cropHeight)

        return try {
            Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
        } catch (e: Exception) {
            Log.w(TAG, "Center crop failed, using original", e)
            bitmap
        }
    }

    /**
     * Scale bitmap down so no dimension exceeds maxDimension.
     * Maintains aspect ratio. Does not upscale.
     */
    private fun resizeToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val larger = maxOf(bitmap.width, bitmap.height)
        if (larger <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / larger
        val newWidth = (bitmap.width * scale).roundToInt()
        val newHeight = (bitmap.height * scale).roundToInt()

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Log.w(TAG, "Resize failed, using original", e)
            bitmap
        }
    }

    /**
     * Compress bitmap to WebP format within the given byte limit.
     * Uses iterative quality reduction: starts at quality 90, steps down
     * by 10 until file fits or quality hits 10.
     *
     * @return true if compressed within limit, false if impossible
     */
    private fun compressToWebP(bitmap: Bitmap, output: File, maxBytes: Long): Boolean {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        // Try decreasing quality until we fit under the limit
        var quality = 90
        while (quality >= 10) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(format, quality, baos)
            val bytes = baos.toByteArray()

            if (bytes.size <= maxBytes) {
                output.writeBytes(bytes)
                Log.d(TAG, "Compressed to ${bytes.size} bytes at quality $quality")
                return true
            }

            Log.d(TAG, "Quality $quality = ${bytes.size} bytes (limit: $maxBytes), reducing...")
            quality -= 10
        }

        // Last resort: lowest quality
        val baos = ByteArrayOutputStream()
        bitmap.compress(format, 5, baos)
        val bytes = baos.toByteArray()
        if (bytes.size <= maxBytes) {
            output.writeBytes(bytes)
            Log.d(TAG, "Compressed to ${bytes.size} bytes at quality 5")
            return true
        }

        return false
    }

}
