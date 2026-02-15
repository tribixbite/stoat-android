package chat.stoat.api.routes.microservices.autumn

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
    EMOJI("emojis", 500_000L, 512, null, "Custom emoji"),
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
        try {
            // Step 1: Decode bitmap with downsampling for very large images
            val bitmap = decodeSampledBitmap(context, uri, uploadType.maxDimension)
                ?: run {
                    Log.e(TAG, "Failed to decode bitmap from $uri")
                    return null
                }

            // Step 2: Apply EXIF rotation
            val rotated = applyExifRotation(context, uri, bitmap)

            // Step 3: Center-crop to target aspect ratio (if required)
            val cropped = uploadType.targetAspectRatio?.let { ratio ->
                centerCrop(rotated, ratio)
            } ?: rotated

            // Step 4: Resize to max dimensions
            val resized = resizeToMax(cropped, uploadType.maxDimension)

            // Step 5: Compress as WebP with file size enforcement
            val outputFile = File(cacheDir, "processed_${System.currentTimeMillis()}.webp")
            val compressed = compressToWebP(resized, outputFile, uploadType.maxBytes)
            if (!compressed) {
                Log.e(TAG, "Failed to compress within ${uploadType.maxBytes} bytes")
                // Clean up intermediate bitmaps
                recycleSafe(bitmap, rotated, cropped, resized)
                return null
            }

            val finalWidth = resized.width
            val finalHeight = resized.height

            // Clean up intermediate bitmaps
            recycleSafe(bitmap, rotated, cropped, resized)

            return ProcessedImage(
                file = outputFile,
                width = finalWidth,
                height = finalHeight,
                sizeBytes = outputFile.length(),
                mimeType = "image/webp"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Image processing failed", e)
            return null
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
        // Double the sample size as long as the result is still bigger than target
        while (largerDimension / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Read EXIF orientation and rotate/flip bitmap accordingly.
     * Many phone cameras embed rotation in EXIF rather than pixel data.
     */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
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
            newWidth to bitmap.height
        } else {
            // Source is taller — crop vertically
            val newHeight = (bitmap.width / targetRatio).roundToInt()
            bitmap.width to newHeight
        }

        val x = (bitmap.width - cropWidth) / 2
        val y = (bitmap.height - cropHeight) / 2

        return try {
            Bitmap.createBitmap(
                bitmap,
                x.coerceAtLeast(0),
                y.coerceAtLeast(0),
                cropWidth.coerceAtMost(bitmap.width),
                cropHeight.coerceAtMost(bitmap.height)
            )
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

    /**
     * Safely recycle bitmaps that are no longer needed.
     * Skips duplicate references (e.g. when no crop was applied, cropped == original).
     */
    private fun recycleSafe(vararg bitmaps: Bitmap) {
        val seen = mutableSetOf<Bitmap>()
        for (bm in bitmaps) {
            if (bm !in seen && !bm.isRecycled) {
                bm.recycle()
                seen.add(bm)
            }
        }
    }
}
