package com.tribixbite.stoatally.composables.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.core.model.util.UserVoiceState
import com.tribixbite.stoatally.composables.chat.displayNameInChannel
import com.tribixbite.stoatally.composables.generic.UserAvatar
import com.tribixbite.stoatally.internals.extensions.TransparentListItemColours

@Composable
fun VoiceParticipant(
    state: UserVoiceState,
    channelId: String,
    speaking: Boolean,
    modifier: Modifier = Modifier
) {
    val user = StoatAPI.userCache[state.id]
    ListItem(
        colors = TransparentListItemColours,
        headlineContent = {
            Text(displayNameInChannel(state.id, channelId))
        },
        leadingContent = {
            // Pulsing green border when speaking
            val speakingTransition = rememberInfiniteTransition(label = "speaking")
            val speakingAlpha by speakingTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "speakingAlpha"
            )
            val borderColor by animateColorAsState(
                targetValue = if (speaking) Color.Green.copy(alpha = speakingAlpha)
                else Color.Transparent,
                label = "borderColor"
            )
            Box(
                modifier = Modifier
                    .border(2.dp, borderColor, CircleShape)
                    .padding(2.dp)
            ) {
                UserAvatar(
                    username = displayNameInChannel(state.id, channelId),
                    userId = state.id,
                    allowAnimation = speaking,
                    avatar = user?.avatar
                )
            }
        },
        trailingContent = {
            Row {
                if (!state.isPublishing) {
                    Icon(
                        painter = painterResource(R.drawable.icn_mic_off_24dp),
                        contentDescription = stringResource(R.string.voice_muted),
                    )
                }
                if (!state.isReceiving) {
                    Icon(
                        painter = painterResource(R.drawable.icn_headset_off_24dp),
                        contentDescription = stringResource(R.string.voice_deafened),
                    )
                }
                if (state.camera) {
                    Icon(
                        painter = painterResource(R.drawable.icn_videocam_24dp),
                        contentDescription = stringResource(R.string.voice_camera_on),
                    )
                }
                if (state.screensharing) {
                    Icon(
                        painter = painterResource(R.drawable.icn_screen_share_24dp),
                        contentDescription = stringResource(R.string.voice_screen_sharing),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}