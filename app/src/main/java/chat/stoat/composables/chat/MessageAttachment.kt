package chat.stoat.composables.chat

import android.annotation.SuppressLint
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import chat.stoat.api.StoatHttp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.STOAT_FILES
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.media.AudioPlayer
import chat.stoat.core.model.schemas.AutumnResource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun FileAttachment(attachment: AutumnResource) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.icn_file_present_24dp),
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = attachment.filename ?: "File",
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = Formatter.formatShortFileSize(context, attachment.size ?: 0),
                    maxLines = 1,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ImageAttachment(attachment: AutumnResource) {
    val url = "$STOAT_FILES/attachments/${attachment.id}/${attachment.filename}"
    var spoilerShown by remember { mutableStateOf(false) }
    val hazeState =
        if (attachment.filename?.startsWith("SPOILER_") == true) rememberHazeState() else null

    BoxWithConstraints {
        RemoteImage(
            url = url,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(attachment.metadata?.width?.toInt()?.dp ?: maxWidth)
                .aspectRatio(
                    attachment.metadata!!.width!!.toFloat() / attachment.metadata!!.height!!.toFloat()
                )
                .then(
                    if (hazeState != null) Modifier.hazeSource(state = hazeState)
                    else Modifier
                ),
            description = attachment.filename ?: "Image"
        )
        if (attachment.filename?.startsWith("SPOILER_") == true && !spoilerShown) {
            Box(
                modifier = Modifier
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
                    .width(attachment.metadata?.width?.toInt()?.dp ?: maxWidth)
                    .aspectRatio(
                        attachment.metadata!!.width!!.toFloat() / attachment.metadata!!.height!!.toFloat()
                    )
                    .clickable { spoilerShown = true },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        .padding(8.dp)
                ) {
                    Text(stringResource(R.string.attachment_spoiler))
                }
            }
        }
    }
}

@Composable
fun VideoPlayButton() {
    Box(
        modifier = Modifier
            .width(48.dp)
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )

    Icon(
        painter = painterResource(R.drawable.icn_play_arrow_24dp),
        contentDescription = stringResource(id = R.string.media_viewer_play),
        modifier = Modifier
            .width(32.dp)
            .aspectRatio(1f)
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VideoAttachment(attachment: AutumnResource) {
    val url = "$STOAT_FILES/attachments/${attachment.id}/${attachment.filename}"

    BoxWithConstraints {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(attachment.metadata?.width?.toInt()?.dp ?: maxWidth)
                .aspectRatio(
                    attachment.metadata!!.width!!.toFloat() / attachment.metadata!!.height!!.toFloat()
                )
        ) {
            // Turns out that when you give Glide a video URL, you get a perfectly cromulent thumbnail.
            RemoteImage(
                url = url,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        attachment.metadata!!.width!!.toFloat() / attachment.metadata!!.height!!.toFloat()
                    ),
                description = attachment.filename ?: "Video"
            )

            VideoPlayButton()
        }
    }
}

@Composable
fun AudioAttachment(attachment: AutumnResource) {
    val url = "$STOAT_FILES/attachments/${attachment.id}/${attachment.filename}"
    AudioPlayer(
        url = url,
        filename = attachment.filename ?: "Audio",
        contentType = attachment.metadata?.type ?: "audio/mpeg"
    )
}

/**
 * Inline text file preview — fetches the first 12 lines and displays
 * them in a monospace code block, with file header and size.
 * Falls back to [FileAttachment] if the fetch fails or file is too large.
 */
@Composable
fun TextAttachment(attachment: AutumnResource) {
    val maxPreviewBytes = 100_000L // only preview files under 100KB
    val maxPreviewLines = 12

    // Skip preview for large files — just show the download card
    if ((attachment.size ?: 0) > maxPreviewBytes) {
        FileAttachment(attachment)
        return
    }

    val url = "$STOAT_FILES/attachments/${attachment.id}/${attachment.filename}"
    var preview by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        try {
            val text = StoatHttp.get(url).bodyAsText()
            val lines = text.lines()
            val truncated = lines.take(maxPreviewLines)
            val suffix = if (lines.size > maxPreviewLines) "\n… (${lines.size - maxPreviewLines} more lines)" else ""
            preview = truncated.joinToString("\n") + suffix
        } catch (_: Exception) {
            failed = true
        }
    }

    if (failed || preview == null) {
        FileAttachment(attachment)
        return
    }

    val context = LocalContext.current
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clip(MaterialTheme.shapes.medium)
        ) {
            // File header row
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icn_file_present_24dp),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = attachment.filename ?: "File",
                        maxLines = 1,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatter.formatShortFileSize(context, attachment.size ?: 0),
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            // Code preview block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    text = preview ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = LocalContentColor.current.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun MessageAttachment(attachment: AutumnResource, onAttachmentClick: (AutumnResource) -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onAttachmentClick(attachment) }
    ) {
        if (attachment.metadata?.type == null) {
            FileAttachment(attachment)
            return
        }

        when (attachment.metadata!!.type) {
            "Image" -> ImageAttachment(attachment)
            "Video" -> VideoAttachment(attachment)
            "Audio" -> AudioAttachment(attachment)
            "Text" -> TextAttachment(attachment)
            else -> FileAttachment(attachment)
        }
    }
}
