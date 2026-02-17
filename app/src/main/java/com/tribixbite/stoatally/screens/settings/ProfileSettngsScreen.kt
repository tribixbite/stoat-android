package com.tribixbite.stoatally.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.STOAT_FILES
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.routes.microservices.autumn.AnimatedImageUtils
import com.tribixbite.stoatally.api.routes.microservices.autumn.AutumnUploadType
import com.tribixbite.stoatally.api.routes.microservices.autumn.ImageProcessor
import com.tribixbite.stoatally.api.routes.microservices.autumn.NormalizedCropRect
import com.tribixbite.stoatally.api.routes.microservices.autumn.uploadToAutumn
import com.tribixbite.stoatally.api.routes.user.fetchUserProfile
import com.tribixbite.stoatally.api.routes.user.patchSelf
import com.tribixbite.stoatally.core.model.schemas.Profile
import com.tribixbite.stoatally.composables.generic.ImageCropDialog
import com.tribixbite.stoatally.composables.generic.InlineMediaPicker
import com.tribixbite.stoatally.composables.screens.settings.RawUserOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@Suppress("StaticFieldLeak")
class ProfileSettingsScreenViewModel @Inject constructor(@ApplicationContext val context: Context) :
    ViewModel() {
    var isLoading by mutableStateOf(true)
    var pfpModel by mutableStateOf<Any?>(null)
    var currentProfile by mutableStateOf<Profile?>(null)
    var pendingProfile by mutableStateOf<Profile?>(null)
    var backgroundModel by mutableStateOf<Any?>(null)
    var pendingAvatarCropUri by mutableStateOf<Uri?>(null)
    var pendingBackgroundCropUri by mutableStateOf<Uri?>(null)
    var isAnimatedBackground by mutableStateOf(false)

    /** Detect animation off main thread, then show crop dialog. */
    fun onBackgroundPicked(uri: Uri) {
        viewModelScope.launch {
            isAnimatedBackground = withContext(Dispatchers.IO) {
                AnimatedImageUtils.isAnimated(context, uri)
            }
            pendingBackgroundCropUri = uri
        }
    }
    var uploadProgress by mutableFloatStateOf(0f)
    var uploadError by mutableStateOf<String?>(null)
    var bioError by mutableStateOf<String?>(null)

    init {
        StoatAPI.selfId?.let { self ->
            StoatAPI.userCache[self]?.avatar?.id?.let {
                pfpModel = "$STOAT_FILES/avatars/${it}"
            }
            viewModelScope.launch {
                currentProfile = fetchUserProfile(self)
                currentProfile!!.background?.id?.let {
                    backgroundModel = "$STOAT_FILES/backgrounds/${it}"
                }

                pendingProfile = currentProfile!!.copy()

                isLoading = false
            }
        }

    }

    /**
     * Process a pre-cropped bitmap and upload as user avatar.
     */
    fun processAndUploadAvatar(croppedBitmap: android.graphics.Bitmap) {
        uploadError = null
        viewModelScope.launch {
            try {
                val processed = withContext(Dispatchers.Default) {
                    ImageProcessor.processForUploadBitmap(
                        croppedBitmap, AutumnUploadType.AVATAR, context.cacheDir
                    )
                } ?: throw Exception("Failed to process image")
                if (!croppedBitmap.isRecycled) croppedBitmap.recycle()

                val id = uploadToAutumn(
                    processed.file, "avatar.webp", "avatars", ContentType.Image.Any,
                    onProgress = { soFar, outOf ->
                        uploadProgress = soFar.toFloat() / outOf.toFloat()
                    }
                )
                processed.file.delete()
                patchSelf(avatar = id)

                pfpModel = StoatAPI.userCache[StoatAPI.selfId]?.avatar?.id?.let {
                    "$STOAT_FILES/avatars/${it}"
                }
            } catch (e: Exception) {
                uploadError = e.message
            }
            uploadProgress = 0f
        }
    }

    /**
     * Process a pre-cropped bitmap and upload as profile background.
     * Handles animated images: extracts frames, applies crop, encodes as GIF.
     * Static images are encoded as WebP.
     */
    fun processAndUploadBackground(
        croppedBitmap: android.graphics.Bitmap,
        cropRect: NormalizedCropRect
    ) {
        uploadError = null
        uploadProgress = 0f

        val uri = pendingBackgroundCropUri
        val animated = isAnimatedBackground && uri != null

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (animated) {
                        // Try pass-through first (animated GIF under size limit, no crop)
                        val passThru = if (cropRect.isFullFrame() && AnimatedImageUtils.isAnimatedGif(context, uri!!)) {
                            ImageProcessor.passThruAnimatedGif(
                                context, uri, AutumnUploadType.BACKGROUND, context.cacheDir
                            )
                        } else null

                        if (passThru != null) {
                            passThru
                        } else {
                            // Extract frames, crop, resize, re-encode as GIF
                            val frames = AnimatedImageUtils.extractFrames(
                                context, uri!!,
                                maxDimension = AutumnUploadType.BACKGROUND.maxDimension
                            )
                            if (frames.isNotEmpty()) {
                                val gifResult = ImageProcessor.processAnimatedFrames(
                                    frames, AutumnUploadType.BACKGROUND,
                                    cropNormalized = cropRect,
                                    cacheDir = context.cacheDir
                                )
                                frames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
                                gifResult
                            } else {
                                // Fallback: animated extraction failed, use static WebP
                                ImageProcessor.processForUploadBitmap(
                                    croppedBitmap, AutumnUploadType.BACKGROUND, context.cacheDir
                                )
                            }
                        }
                    } else {
                        ImageProcessor.processForUploadBitmap(
                            croppedBitmap, AutumnUploadType.BACKGROUND, context.cacheDir
                        )
                    }
                }
                if (!croppedBitmap.isRecycled) croppedBitmap.recycle()

                if (result == null) throw Exception("Failed to process image")

                val isGif = result.mimeType == "image/gif"
                val fileName = if (isGif) "background.gif" else "background.webp"
                val ct = if (isGif) ContentType.Image.GIF else ContentType.Image.Any

                val id = uploadToAutumn(
                    result.file, fileName, "backgrounds", ct,
                    onProgress = { soFar, outOf ->
                        uploadProgress = soFar.toFloat() / outOf.toFloat()
                    }
                )
                result.file.delete()
                patchSelf(background = id)
            } catch (e: Exception) {
                uploadError = e.message
                uploadProgress = 0f
                return@launch
            }

            backgroundModel = StoatAPI.selfId?.let {
                val profile = fetchUserProfile(it)
                currentProfile = profile
                pendingProfile = profile

                profile.background?.id?.let { bgId ->
                    "$STOAT_FILES/backgrounds/$bgId"
                }
            }

            uploadProgress = 0f
            isAnimatedBackground = false
        }
    }

    fun removePfp() {
        viewModelScope.launch {
            patchSelf(remove = listOf("Avatar"))
            pfpModel = null
        }
    }

    fun removeBackground() {
        viewModelScope.launch {
            patchSelf(remove = listOf("ProfileBackground"))
            backgroundModel = null
        }
    }

    fun saveBio() {
        bioError = null
        viewModelScope.launch {
            try {
                patchSelf(bio = pendingProfile?.content)

                fetchUserProfile(StoatAPI.selfId!!).let {
                    currentProfile = it
                    pendingProfile = it
                }
            } catch (e: Exception) {
                bioError = e.message
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    navController: NavController,
    viewModel: ProfileSettingsScreenViewModel = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.settings_profile),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (viewModel.isLoading) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(scrollState)
                        }
                    ),
                verticalArrangement = if (viewModel.isLoading) {
                    Arrangement.Center
                } else {
                    Arrangement.Top
                },
                horizontalAlignment = if (viewModel.isLoading) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                }
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                    )
                } else {
                    StoatAPI.userCache[StoatAPI.selfId]?.let {
                        RawUserOverview(
                            it,
                            viewModel.pendingProfile,
                            viewModel.pfpModel?.toString(),
                            viewModel.backgroundModel?.toString()
                        )
                    }

                    AnimatedVisibility(visible = viewModel.uploadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { viewModel.uploadProgress },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                        )
                    }

                    AnimatedVisibility(visible = viewModel.uploadError != null) {
                        Text(
                            text = viewModel.uploadError ?: "",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.settings_profile_profile_picture),
                                style = MaterialTheme.typography.labelLarge
                            )

                            Spacer(Modifier.height(10.dp))

                            InlineMediaPicker(
                                currentModel = viewModel.pfpModel,
                                circular = true,
                                useAvatarCircularity = true,
                                onPick = { model ->
                                    // Show crop dialog instead of uploading directly
                                    val uri = when (model) {
                                        is Uri -> model
                                        is String -> Uri.parse(model.toString())
                                        else -> null
                                    }
                                    if (uri != null) {
                                        viewModel.pendingAvatarCropUri = uri
                                    }
                                },
                                canRemove = true,
                                onRemove = {
                                    viewModel.removePfp()
                                }
                            )

                            // Avatar crop dialog (1:1 aspect ratio)
                            if (viewModel.pendingAvatarCropUri != null) {
                                ImageCropDialog(
                                    uri = viewModel.pendingAvatarCropUri!!,
                                    aspectRatio = 1f,
                                    onConfirm = { croppedBitmap, _ ->
                                        viewModel.pendingAvatarCropUri = null
                                        viewModel.processAndUploadAvatar(croppedBitmap)
                                    },
                                    onDismiss = { viewModel.pendingAvatarCropUri = null }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.settings_profile_custom_background),
                                style = MaterialTheme.typography.labelLarge,
                            )

                            Spacer(Modifier.height(10.dp))

                            InlineMediaPicker(
                                currentModel = viewModel.backgroundModel,
                                onPick = { model ->
                                    // Show crop dialog instead of uploading directly
                                    val uri = when (model) {
                                        is Uri -> model
                                        is String -> Uri.parse(model.toString())
                                        else -> null
                                    }
                                    if (uri != null) {
                                        viewModel.onBackgroundPicked(uri)
                                    }
                                },
                                canRemove = true,
                                onRemove = {
                                    viewModel.removeBackground()
                                }
                            )

                            // Background crop dialog (232:100 aspect, matching web frontend)
                            if (viewModel.pendingBackgroundCropUri != null) {
                                ImageCropDialog(
                                    uri = viewModel.pendingBackgroundCropUri!!,
                                    aspectRatio = 2.32f,
                                    onConfirm = { croppedBitmap, cropRect ->
                                        viewModel.pendingBackgroundCropUri = null
                                        viewModel.processAndUploadBackground(croppedBitmap, cropRect)
                                    },
                                    onDismiss = { viewModel.pendingBackgroundCropUri = null }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 20.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.pendingProfile?.content ?: "",
                            onValueChange = { value ->
                                viewModel.pendingProfile?.let {
                                    viewModel.pendingProfile = it.copy(content = value)
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(id = R.string.user_info_sheet_category_bio),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AnimatedVisibility(visible = viewModel.bioError != null) {
                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = viewModel.bioError ?: "",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                viewModel.saveBio()
                            },
                            enabled = viewModel.pendingProfile?.content != viewModel.currentProfile?.content,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.icn_check_24dp),
                                contentDescription = null
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(id = R.string.settings_profile_save),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
