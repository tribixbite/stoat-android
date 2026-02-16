package com.tribixbite.stoatally.composables.generic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.routes.microservices.autumn.AutumnUploadType
import com.tribixbite.stoatally.api.routes.microservices.autumn.ImageProcessor
import com.tribixbite.stoatally.api.routes.microservices.autumn.NormalizedCropRect
import com.tribixbite.stoatally.api.routes.microservices.autumn.ProcessedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimum crop size in display pixels to prevent accidental zero-area crops.
 */
private const val MIN_CROP_PX = 48f

/**
 * Radius of corner drag handles in display pixels.
 */
private const val HANDLE_RADIUS = 14f

/**
 * Hit-test radius for corner handles — slightly larger than visual for touch ergonomics.
 */
private const val HANDLE_HIT_RADIUS = 32f

/**
 * Semi-transparent overlay colour drawn outside the crop region.
 */
private val OVERLAY_COLOR = Color.Black.copy(alpha = 0.6f)

/**
 * Full-screen crop dialog. Shows the source image with a draggable, resizable
 * crop rectangle locked to [aspectRatio]. User drags corners to resize (ratio-locked,
 * opposite corner pinned) and drags the crop body to reposition within image bounds.
 *
 * @param uri Source image URI from picker
 * @param aspectRatio Width/height ratio to enforce (1f = square, 2.5f = banner)
 * @param maxDecodeSize Max dimension for in-memory preview bitmap (prevents OOM)
 * @param onConfirm Called with the cropped bitmap and normalized crop rect when user confirms.
 *   The normalized rect (0..1 values) can be used to apply the same crop to animated frames.
 * @param onDismiss Called when user cancels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropDialog(
    uri: Uri,
    aspectRatio: Float,
    maxDecodeSize: Int = 2048,
    onConfirm: (Bitmap, NormalizedCropRect) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Decoded + EXIF-rotated source bitmap for display
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }

    // Crop rectangle in bitmap-coordinate space (pixels of displayBitmap)
    var cropRect by remember { mutableStateOf(Rect.Zero) }

    // Which corner is being dragged (null = body drag or idle)
    // 0=TL, 1=TR, 2=BR, 3=BL
    var dragCorner by remember { mutableStateOf<Int?>(null) }

    // Decode bitmap on first composition
    LaunchedEffect(uri) {
        displayBitmap = withContext(Dispatchers.Default) {
            decodeBitmapForCrop(context, uri, maxDecodeSize)
        }
        if (displayBitmap == null) loadError = true
    }

    // Initialise crop rect once bitmap is loaded
    LaunchedEffect(displayBitmap) {
        val bmp = displayBitmap ?: return@LaunchedEffect
        cropRect = initialCropRect(bmp.width.toFloat(), bmp.height.toFloat(), aspectRatio)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.crop_dialog_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.icn_arrow_back_24dp),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        val bmp = displayBitmap
                        Button(
                            onClick = {
                                if (bmp != null) {
                                    // Extract crop from display bitmap
                                    val x = cropRect.left.roundToInt().coerceIn(0, bmp.width - 1)
                                    val y = cropRect.top.roundToInt().coerceIn(0, bmp.height - 1)
                                    val w = cropRect.width.roundToInt()
                                        .coerceIn(1, bmp.width - x)
                                    val h = cropRect.height.roundToInt()
                                        .coerceIn(1, bmp.height - y)
                                    val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
                                    // Compute normalized crop rect (0..1) for animated frame processing
                                    val normRect = NormalizedCropRect(
                                        left = x.toFloat() / bmp.width,
                                        top = y.toFloat() / bmp.height,
                                        width = w.toFloat() / bmp.width,
                                        height = h.toFloat() / bmp.height
                                    )
                                    onConfirm(cropped, normRect)
                                }
                            },
                            enabled = bmp != null,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.crop_dialog_done))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Black
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = displayBitmap
                if (bmp == null) {
                    if (loadError) {
                        Text(
                            "Failed to load image",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.crop_dialog_loading),
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    CropCanvas(
                        bitmap = bmp,
                        cropRect = cropRect,
                        aspectRatio = aspectRatio,
                        onCropRectChanged = { cropRect = it },
                        dragCorner = dragCorner,
                        onDragCornerChanged = { dragCorner = it }
                    )
                }
            }
        }
    }
}

/**
 * Canvas that renders the image, overlay, crop rect border, corner handles,
 * and handles all drag gestures for cropping.
 */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    cropRect: Rect,
    aspectRatio: Float,
    onCropRectChanged: (Rect) -> Unit,
    dragCorner: Int?,
    onDragCornerChanged: (Int?) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bmpW, bmpH, aspectRatio) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Convert touch position to bitmap coordinates
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val scale = min(canvasW / bmpW, canvasH / bmpH)
                        val offsetX = (canvasW - bmpW * scale) / 2f
                        val offsetY = (canvasH - bmpH * scale) / 2f

                        val bmpX = (offset.x - offsetX) / scale
                        val bmpY = (offset.y - offsetY) / scale

                        // Check if touching a corner handle
                        val hitRadius = HANDLE_HIT_RADIUS / scale
                        val corners = listOf(
                            Offset(cropRect.left, cropRect.top),   // 0 = TL
                            Offset(cropRect.right, cropRect.top),  // 1 = TR
                            Offset(cropRect.right, cropRect.bottom), // 2 = BR
                            Offset(cropRect.left, cropRect.bottom)  // 3 = BL
                        )
                        val hitCorner = corners.indexOfFirst { corner ->
                            (corner - Offset(bmpX, bmpY)).getDistance() < hitRadius
                        }
                        onDragCornerChanged(if (hitCorner >= 0) hitCorner else null)
                    },
                    onDragEnd = { onDragCornerChanged(null) },
                    onDragCancel = { onDragCornerChanged(null) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val scale = min(canvasW / bmpW, canvasH / bmpH)

                        // Convert drag delta from screen to bitmap coordinates
                        val dx = dragAmount.x / scale
                        val dy = dragAmount.y / scale

                        val corner = dragCorner
                        if (corner != null) {
                            // Corner drag — resize with locked aspect ratio
                            onCropRectChanged(
                                resizeCropRect(cropRect, corner, dx, dy, aspectRatio, bmpW, bmpH)
                            )
                        } else {
                            // Body drag — reposition within image bounds
                            onCropRectChanged(
                                moveCropRect(cropRect, dx, dy, bmpW, bmpH)
                            )
                        }
                    }
                )
            }
    ) {
        // Compute scale + offset to center-fit bitmap in canvas
        val canvasW = size.width
        val canvasH = size.height
        val scale = min(canvasW / bmpW, canvasH / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val offsetX = (canvasW - scaledW) / 2f
        val offsetY = (canvasH - scaledH) / 2f

        // Draw the source image scaled to fit
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
            dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt())
        )

        // Map crop rect from bitmap coords to canvas coords
        val displayCropRect = Rect(
            left = cropRect.left * scale + offsetX,
            top = cropRect.top * scale + offsetY,
            right = cropRect.right * scale + offsetX,
            bottom = cropRect.bottom * scale + offsetY
        )

        // Draw semi-transparent overlay outside crop area
        val fullPath = Path().apply {
            addRect(Rect(0f, 0f, canvasW, canvasH))
        }
        val cropPath = Path().apply {
            addRect(displayCropRect)
        }
        val overlayPath = Path().apply {
            op(fullPath, cropPath, PathOperation.Difference)
        }
        drawPath(overlayPath, OVERLAY_COLOR)

        // Draw crop border
        drawRect(
            color = Color.White,
            topLeft = Offset(displayCropRect.left, displayCropRect.top),
            size = Size(displayCropRect.width, displayCropRect.height),
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw rule-of-thirds grid lines (subtle)
        val thirdW = displayCropRect.width / 3f
        val thirdH = displayCropRect.height / 3f
        val gridColor = Color.White.copy(alpha = 0.3f)
        for (i in 1..2) {
            // Vertical lines
            drawLine(
                color = gridColor,
                start = Offset(displayCropRect.left + thirdW * i, displayCropRect.top),
                end = Offset(displayCropRect.left + thirdW * i, displayCropRect.bottom),
                strokeWidth = 1.dp.toPx()
            )
            // Horizontal lines
            drawLine(
                color = gridColor,
                start = Offset(displayCropRect.left, displayCropRect.top + thirdH * i),
                end = Offset(displayCropRect.right, displayCropRect.top + thirdH * i),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw corner handles
        val handleRadius = HANDLE_RADIUS
        val corners = listOf(
            Offset(displayCropRect.left, displayCropRect.top),
            Offset(displayCropRect.right, displayCropRect.top),
            Offset(displayCropRect.right, displayCropRect.bottom),
            Offset(displayCropRect.left, displayCropRect.bottom)
        )
        corners.forEach { corner ->
            drawCircle(
                color = Color.White,
                radius = handleRadius,
                center = corner
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.3f),
                radius = handleRadius - 2f,
                center = corner
            )
        }
    }
}

/**
 * Compute the initial max-fill crop rect at the target aspect ratio
 * centered within the image bounds.
 */
private fun initialCropRect(imgW: Float, imgH: Float, aspectRatio: Float): Rect {
    val imgRatio = imgW / imgH
    val (cropW, cropH) = if (imgRatio > aspectRatio) {
        // Image is wider — height-limited
        val h = imgH
        val w = h * aspectRatio
        w to h
    } else {
        // Image is taller — width-limited
        val w = imgW
        val h = w / aspectRatio
        w to h
    }
    val x = (imgW - cropW) / 2f
    val y = (imgH - cropH) / 2f
    return Rect(x, y, x + cropW, y + cropH)
}

/**
 * Resize crop rect by dragging a corner. The opposite corner stays pinned.
 * Aspect ratio is locked. Clamped to image bounds and minimum size.
 */
private fun resizeCropRect(
    rect: Rect,
    corner: Int,
    dx: Float,
    dy: Float,
    aspectRatio: Float,
    imgW: Float,
    imgH: Float
): Rect {
    // Determine which axis the user is primarily dragging along,
    // then compute the other axis from the aspect ratio
    val dominantDelta = if (abs(dx) > abs(dy)) dx else dy * aspectRatio

    return when (corner) {
        0 -> {
            // TL dragged — BR pinned
            var newLeft = rect.left + dominantDelta
            val minLeft = max(0f, rect.right - (imgH * aspectRatio))
            newLeft = newLeft.coerceIn(minLeft, rect.right - MIN_CROP_PX * aspectRatio)
            val newW = rect.right - newLeft
            val newH = newW / aspectRatio
            val newTop = rect.bottom - newH
            if (newTop < 0f) return rect // Can't fit
            Rect(newLeft, newTop, rect.right, rect.bottom)
        }
        1 -> {
            // TR dragged — BL pinned
            var newRight = rect.right + dominantDelta
            val maxRight = min(imgW, rect.left + (imgH * aspectRatio))
            newRight = newRight.coerceIn(rect.left + MIN_CROP_PX * aspectRatio, maxRight)
            val newW = newRight - rect.left
            val newH = newW / aspectRatio
            val newTop = rect.bottom - newH
            if (newTop < 0f) return rect
            Rect(rect.left, newTop, newRight, rect.bottom)
        }
        2 -> {
            // BR dragged — TL pinned
            var newRight = rect.right + dominantDelta
            val maxRight = min(imgW, rect.left + ((imgH - rect.top) * aspectRatio))
            newRight = newRight.coerceIn(rect.left + MIN_CROP_PX * aspectRatio, maxRight)
            val newW = newRight - rect.left
            val newH = newW / aspectRatio
            val newBottom = rect.top + newH
            if (newBottom > imgH) return rect
            Rect(rect.left, rect.top, newRight, newBottom)
        }
        3 -> {
            // BL dragged — TR pinned
            var newLeft = rect.left + dominantDelta
            val minLeft = max(0f, rect.right - ((imgH - rect.top) * aspectRatio))
            newLeft = newLeft.coerceIn(minLeft, rect.right - MIN_CROP_PX * aspectRatio)
            val newW = rect.right - newLeft
            val newH = newW / aspectRatio
            val newBottom = rect.top + newH
            if (newBottom > imgH) return rect
            Rect(newLeft, rect.top, rect.right, newBottom)
        }
        else -> rect
    }
}

/**
 * Move crop rect by [dx], [dy] clamped to image bounds.
 */
private fun moveCropRect(rect: Rect, dx: Float, dy: Float, imgW: Float, imgH: Float): Rect {
    var newLeft = rect.left + dx
    var newTop = rect.top + dy

    // Clamp to image bounds
    newLeft = newLeft.coerceIn(0f, imgW - rect.width)
    newTop = newTop.coerceIn(0f, imgH - rect.height)

    return Rect(newLeft, newTop, newLeft + rect.width, newTop + rect.height)
}

/**
 * Decode a bitmap from URI with inSampleSize targeting maxDimension,
 * then apply EXIF rotation. Returns null on failure.
 */
private fun decodeBitmapForCrop(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    // First pass: get dimensions
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

    // Calculate sample size
    var sampleSize = 1
    val larger = max(opts.outWidth, opts.outHeight)
    while (larger / sampleSize > maxDimension * 3 / 2) {
        sampleSize *= 2
    }
    opts.inSampleSize = sampleSize
    opts.inJustDecodeBounds = false

    val raw = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null

    // Apply EXIF rotation for correct orientation
    return ImageProcessor.applyExifRotation(context, uri, raw)
}

/**
 * Convenience composable that wraps [ImageCropDialog] with background processing
 * into a single reusable component. Shows crop dialog when [pendingUri] is non-null,
 * processes the cropped bitmap via [ImageProcessor.processForUploadBitmap], and
 * returns the result via [onProcessed].
 */
@Composable
fun CropAndProcess(
    pendingUri: Uri?,
    uploadType: AutumnUploadType,
    onProcessed: (ProcessedImage) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ratio = uploadType.targetAspectRatio

    if (pendingUri != null && ratio != null) {
        ImageCropDialog(
            uri = pendingUri,
            aspectRatio = ratio,
            onConfirm = { croppedBitmap, _ ->
                onDismiss() // Close dialog immediately
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        ImageProcessor.processForUploadBitmap(
                            croppedBitmap,
                            uploadType,
                            context.cacheDir
                        )
                    }
                    // Recycle the cropped bitmap after processing
                    if (!croppedBitmap.isRecycled) croppedBitmap.recycle()

                    if (result != null) {
                        onProcessed(result)
                    } else {
                        onError("Failed to process image")
                    }
                }
            },
            onDismiss = onDismiss
        )
    }
}
