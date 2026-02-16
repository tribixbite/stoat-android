package com.tribixbite.stoatally.screens.settings.server

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.STOAT_FILES
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.routes.custom.createEmoji
import com.tribixbite.stoatally.api.routes.custom.deleteEmoji
import com.tribixbite.stoatally.api.routes.microservices.autumn.AnimatedImageUtils
import com.tribixbite.stoatally.api.routes.microservices.autumn.AutumnUploadType
import com.tribixbite.stoatally.api.routes.microservices.autumn.ImageProcessor
import com.tribixbite.stoatally.api.routes.microservices.autumn.NormalizedCropRect
import com.tribixbite.stoatally.api.routes.microservices.autumn.ProcessedImage
import com.tribixbite.stoatally.api.routes.microservices.autumn.uploadToAutumn
import com.tribixbite.stoatally.composables.generic.ImageCropDialog
import com.tribixbite.stoatally.composables.generic.ListHeader
import com.tribixbite.stoatally.core.model.schemas.Emoji
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun EmojiManagementScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val emojis = remember { mutableStateListOf<Emoji>() }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf<Emoji?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Load emojis from cache
    LaunchedEffect(serverId) {
        emojis.clear()
        emojis.addAll(
            StoatAPI.emojiCache.values
                .filter { it.parent?.id == serverId }
                .sortedBy { it.name }
        )
        isLoading = false
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { emoji ->
        var isDeleting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = null },
            title = { Text(stringResource(R.string.emoji_delete_title)) },
            text = { Text(stringResource(R.string.emoji_delete_confirm, emoji.name ?: emoji.id ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            val emojiId = emoji.id ?: return@launch
                            val error = deleteEmoji(emojiId)
                            if (error == null) {
                                emojis.removeAll { it.id == emojiId }
                                StoatAPI.emojiCache.remove(emojiId)
                                showDeleteConfirm = null
                                Toast.makeText(context, context.getString(R.string.emoji_deleted), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isDeleting = false
                        }
                    },
                    enabled = !isDeleting
                ) { Text(stringResource(R.string.emoji_delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Add emoji dialog
    if (showAddDialog) {
        AddEmojiDialog(
            context = context,
            serverId = serverId,
            onDismiss = { showAddDialog = false },
            onCreated = { newEmoji ->
                newEmoji.id?.let { StoatAPI.emojiCache[it] = newEmoji }
                emojis.add(newEmoji)
                showAddDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.emoji_management_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.icn_add_24dp),
                    contentDescription = stringResource(R.string.emoji_add_title)
                )
            }
        }
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                emojis.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.emoji_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListHeader {
                                Text(stringResource(R.string.emoji_list_header, emojis.size))
                            }
                        }
                        items(emojis, key = { it.id ?: "" }) { emoji ->
                            val emojiUrl = emoji.id?.let { "$STOAT_FILES/emojis/$it" }

                            ListItem(
                                headlineContent = {
                                    Text(":${emoji.name ?: emoji.id}:")
                                },
                                supportingContent = {
                                    val details = buildString {
                                        emoji.creatorID?.let { creatorId ->
                                            val creator = StoatAPI.userCache[creatorId]
                                            append(context.getString(
                                                R.string.emoji_created_by,
                                                creator?.displayName ?: creator?.username ?: creatorId
                                            ))
                                        }
                                        if (emoji.nsfw == true) {
                                            if (isNotEmpty()) append(" · ")
                                            append("NSFW")
                                        }
                                    }
                                    if (details.isNotEmpty()) {
                                        Text(details)
                                    }
                                },
                                leadingContent = {
                                    if (emojiUrl != null) {
                                        GlideImage(
                                            model = emojiUrl,
                                            contentDescription = emoji.name,
                                            modifier = Modifier.size(32.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_emoji_objects_24dp),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { showDeleteConfirm = emoji }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_delete_24dp),
                                            contentDescription = stringResource(R.string.emoji_delete_title),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding a new emoji. User picks an image, sees a preview
 * (processed to fit emoji constraints: 512px max, 500KB WebP),
 * enters a name, then uploads to autumn/emojis.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AddEmojiDialog(
    context: Context,
    serverId: String,
    onDismiss: () -> Unit,
    onCreated: (Emoji) -> Unit
) {
    val scope = rememberCoroutineScope()
    var emojiName by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var isAnimatedImage by remember { mutableStateOf(false) }
    var processedImage by remember { mutableStateOf<ProcessedImage?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    // File picker launcher — detect animation off main thread, then show crop dialog
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            error = null
            processedImage = null
            scope.launch {
                isAnimatedImage = withContext(Dispatchers.IO) {
                    AnimatedImageUtils.isAnimated(context, uri)
                }
                pendingCropUri = uri
            }
        }
    }

    // Crop dialog for square emoji (1:1 aspect ratio)
    if (pendingCropUri != null) {
        ImageCropDialog(
            uri = pendingCropUri!!,
            aspectRatio = 1f,
            onConfirm = { croppedBitmap, normalizedRect ->
                val cropUri = pendingCropUri!!
                val animated = isAnimatedImage
                pendingCropUri = null
                selectedUri = cropUri
                isProcessing = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        if (animated) {
                            // Try pass-through first (animated GIF under size limit, no crop needed)
                            val passThru = if (normalizedRect.isFullFrame() && AnimatedImageUtils.isAnimatedGif(context, cropUri)) {
                                ImageProcessor.passThruAnimatedGif(context, cropUri, AutumnUploadType.EMOJI, context.cacheDir)
                            } else null

                            if (passThru != null) {
                                passThru
                            } else {
                                // Extract frames, crop, resize, re-encode as GIF
                                val frames = AnimatedImageUtils.extractFrames(
                                    context, cropUri,
                                    maxDimension = AutumnUploadType.EMOJI.maxDimension
                                )
                                if (frames.isNotEmpty()) {
                                    val gifResult = ImageProcessor.processAnimatedFrames(
                                        frames, AutumnUploadType.EMOJI,
                                        cropNormalized = normalizedRect,
                                        cacheDir = context.cacheDir
                                    )
                                    // Clean up frames
                                    frames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                                    gifResult
                                } else {
                                    // Fallback: animated extraction failed, use static WebP
                                    ImageProcessor.processForUploadBitmap(
                                        croppedBitmap, AutumnUploadType.EMOJI, context.cacheDir
                                    )
                                }
                            }
                        } else {
                            ImageProcessor.processForUploadBitmap(
                                croppedBitmap, AutumnUploadType.EMOJI, context.cacheDir
                            )
                        }
                    }
                    if (!croppedBitmap.isRecycled) croppedBitmap.recycle()
                    if (result != null) {
                        processedImage = result
                    } else {
                        error = "Failed to process image"
                    }
                    isProcessing = false
                }
            },
            onDismiss = { pendingCropUri = null }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text(stringResource(R.string.emoji_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = emojiName,
                    onValueChange = { emojiName = it.replace(Regex("[^a-zA-Z0-9_]"), "") },
                    label = { Text(stringResource(R.string.emoji_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { launcher.launch("image/*") },
                        enabled = !isUploading && !isProcessing
                    ) {
                        Text(stringResource(R.string.emoji_pick_image))
                    }
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Image preview with dimensions and file size
                processedImage?.let { img ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GlideImage(
                            model = img.file,
                            contentDescription = "Emoji preview",
                            modifier = Modifier
                                .size(96.dp)
                                .padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    val formatLabel = if (img.mimeType == "image/gif") "GIF" else "WebP"
                    Text(
                        "${img.width}×${img.height} · ${img.sizeBytes / 1024}KB · $formatLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                AnimatedVisibility(visible = isUploading) {
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val img = processedImage ?: return@Button
                    if (emojiName.isBlank()) return@Button

                    isUploading = true
                    error = null
                    scope.launch {
                        try {
                            // Use correct filename and content type based on format
                            val isGif = img.mimeType == "image/gif"
                            val fileName = if (isGif) "emoji.gif" else "emoji.webp"
                            val ct = if (isGif) ContentType.Image.GIF else ContentType.Image.Any

                            val autumnId = uploadToAutumn(
                                img.file,
                                fileName,
                                "emojis",
                                ct,
                                onProgress = { soFar, outOf ->
                                    uploadProgress = soFar.toFloat() / outOf.toFloat()
                                }
                            )

                            val newEmoji = createEmoji(
                                emojiId = autumnId,
                                name = emojiName,
                                serverId = serverId
                            )
                            img.file.delete()
                            onCreated(newEmoji)
                            Toast.makeText(context, context.getString(R.string.emoji_created), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            error = e.message
                        }
                        isUploading = false
                    }
                },
                enabled = !isUploading && !isProcessing && emojiName.isNotBlank() && processedImage != null
            ) {
                Text(stringResource(R.string.emoji_add_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
