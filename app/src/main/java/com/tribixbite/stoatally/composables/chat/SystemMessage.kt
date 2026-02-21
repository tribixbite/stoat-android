package com.tribixbite.stoatally.composables.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.composables.markdown.RichMarkdown
import com.tribixbite.stoatally.core.model.schemas.Message

enum class SystemMessageType(val type: String) {
    CHANNEL_OWNERSHIP_CHANGED("channel_ownership_changed"),
    CHANNEL_ICON_CHANGED("channel_icon_changed"),
    CHANNEL_DESCRIPTION_CHANGED("channel_description_changed"),
    CHANNEL_RENAMED("channel_renamed"),
    USER_REMOVE("user_remove"),
    USER_ADDED("user_added"),
    USER_BANNED("user_banned"),
    USER_KICKED("user_kicked"),
    USER_LEFT("user_left"),
    USER_JOINED("user_joined"),
    MESSAGE_PINNED("message_pinned"),
    MESSAGE_UNPINNED("message_unpinned"),
    TEXT("text")
}

fun String?.mention(): String {
    return "<@$this>"
}

@Composable
fun SystemMessage(message: Message) {
    if (message.system == null) return

    val systemMessageType =
        SystemMessageType.entries.firstOrNull { it.type == message.system!!.type }

    if (systemMessageType == null) {
        UnsupportedMessage(context = message.system!!.type)
        return
    }

    CompositionLocalProvider(
        LocalContentColor provides LocalContentColor.current.copy(alpha = 0.7f),
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontWeight = FontWeight.Light
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .fillMaxWidth()
        ) {
            SystemMessageIconWithBackground(type = systemMessageType)

            Spacer(modifier = Modifier.width(10.dp))

            when (systemMessageType) {
                SystemMessageType.CHANNEL_OWNERSHIP_CHANGED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_ownership_changed,
                            message.system!!.from.mention(),
                            message.system!!.to.mention()
                        )
                    )
                }

                SystemMessageType.CHANNEL_ICON_CHANGED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_channel_icon_changed,
                            message.system!!.by.mention()
                        )
                    )
                }

                SystemMessageType.CHANNEL_DESCRIPTION_CHANGED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_channel_description_changed,
                            message.system!!.by.mention()
                        )
                    )
                }

                SystemMessageType.CHANNEL_RENAMED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_channel_renamed,
                            message.system!!.by.mention(),
                            "**${message.system!!.name ?: stringResource(R.string.unknown)}**"
                        )
                    )
                }

                SystemMessageType.USER_REMOVE -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_user_removed,
                            message.system!!.by.mention(),
                            message.system!!.id.mention()
                        )
                    )
                }

                SystemMessageType.USER_ADDED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_user_added,
                            message.system!!.by.mention(),
                            message.system!!.id.mention()
                        )
                    )
                }

                SystemMessageType.USER_BANNED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_user_banned,
                            message.system!!.id.mention()
                        )
                    )
                }

                SystemMessageType.USER_KICKED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_user_kicked,
                            message.system!!.id.mention()
                        )
                    )
                }

                SystemMessageType.USER_LEFT -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_user_left,
                            message.system!!.id.mention()
                        )
                    )
                }

                SystemMessageType.USER_JOINED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_user_joined,
                            message.system!!.id.mention()
                        )
                    )
                }

                SystemMessageType.MESSAGE_PINNED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_message_pinned,
                            message.system!!.by.mention()
                        )
                    )
                }

                SystemMessageType.MESSAGE_UNPINNED -> {
                    RichMarkdown(
                        stringResource(
                            R.string.system_message_message_unpinned,
                            message.system!!.by.mention()
                        )
                    )
                }

                SystemMessageType.TEXT -> {
                    message.system!!.content?.let { RichMarkdown(it) }
                }
            }
        }
    }
}

@Composable
fun SystemMessageIcon(type: SystemMessageType, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    when (type) {
        SystemMessageType.CHANNEL_OWNERSHIP_CHANGED -> {
            Icon(
                painter = painterResource(R.drawable.icn_key_24dp),
                contentDescription = stringResource(R.string.system_message_ownership_changed_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.CHANNEL_ICON_CHANGED -> {
            Icon(
                painter = painterResource(R.drawable.icn_landscape_2_edit_24dp),
                contentDescription = stringResource(
                    R.string.system_message_channel_icon_changed_alt
                ),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.CHANNEL_DESCRIPTION_CHANGED -> {
            Icon(
                painter = painterResource(R.drawable.icn_contract_edit_24dp),
                contentDescription = stringResource(
                    R.string.system_message_channel_description_changed_alt
                ),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.CHANNEL_RENAMED -> {
            Icon(
                painter = painterResource(R.drawable.icn_ink_highlighter_move_24dp),
                contentDescription = stringResource(R.string.system_message_channel_renamed_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.USER_REMOVE -> {
            Icon(
                painter = painterResource(R.drawable.icn_group_remove_24dp),
                contentDescription = stringResource(R.string.system_message_user_removed_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.USER_ADDED -> {
            Icon(
                painter = painterResource(R.drawable.icn_group_add_24dp),
                contentDescription = stringResource(R.string.system_message_user_added_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.USER_BANNED -> {
            Icon(
                painter = painterResource(R.drawable.icn_gavel_24dp),
                contentDescription = stringResource(R.string.system_message_user_banned_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.USER_KICKED -> {
            Icon(
                painter = painterResource(R.drawable.icn_sports_and_outdoors_24dp),
                contentDescription = stringResource(R.string.system_message_user_kicked_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.USER_LEFT -> {
            Icon(
                painter = painterResource(R.drawable.icn_door_open_24dp),
                contentDescription = stringResource(R.string.system_message_user_left_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.USER_JOINED -> {
            Icon(
                painter = painterResource(R.drawable.icn_waving_hand_24dp),
                contentDescription = stringResource(R.string.system_message_user_joined_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size),
            )
        }

        SystemMessageType.MESSAGE_PINNED -> {
            Icon(
                painter = painterResource(R.drawable.ic_pin_24dp),
                contentDescription = stringResource(R.string.system_message_message_pinned_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.MESSAGE_UNPINNED -> {
            Icon(
                painter = painterResource(R.drawable.ic_pin_24dp),
                contentDescription = stringResource(R.string.system_message_message_unpinned_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }

        SystemMessageType.TEXT -> {
            Icon(
                painter = painterResource(R.drawable.icn_info_24dp),
                contentDescription = stringResource(R.string.system_message_text_alt),
                tint = LocalContentColor.current,
                modifier = modifier.size(size)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun shapeForType(type: SystemMessageType): Shape {
    return when (type) {
        SystemMessageType.CHANNEL_OWNERSHIP_CHANGED -> MaterialShapes.Slanted
        SystemMessageType.CHANNEL_ICON_CHANGED -> MaterialShapes.Pentagon
        SystemMessageType.CHANNEL_DESCRIPTION_CHANGED -> MaterialShapes.Gem
        SystemMessageType.CHANNEL_RENAMED -> MaterialShapes.Pill
        SystemMessageType.USER_REMOVE -> MaterialShapes.Flower
        SystemMessageType.USER_ADDED -> MaterialShapes.Sunny
        SystemMessageType.USER_BANNED -> MaterialShapes.Burst
        SystemMessageType.USER_KICKED -> MaterialShapes.SoftBurst
        SystemMessageType.USER_LEFT -> MaterialShapes.Cookie4Sided
        SystemMessageType.USER_JOINED -> MaterialShapes.Cookie9Sided
        SystemMessageType.MESSAGE_PINNED -> MaterialShapes.Clover4Leaf
        SystemMessageType.MESSAGE_UNPINNED -> MaterialShapes.Clover4Leaf
        SystemMessageType.TEXT -> MaterialShapes.Square
    }.toShape()
}

// Type-specific colours: green for joins/adds, red for leaves/bans/kicks, blue for channel edits
@Composable
private fun backgroundColourForType(type: SystemMessageType): Color {
    return when (type) {
        SystemMessageType.USER_JOINED, SystemMessageType.USER_ADDED ->
            Color(0xFF1B3A2A) // green tint — positive membership events
        SystemMessageType.USER_LEFT, SystemMessageType.USER_REMOVE ->
            Color(0xFF3A2A1B) // amber tint — neutral departures
        SystemMessageType.USER_BANNED, SystemMessageType.USER_KICKED ->
            Color(0xFF3A1B1B) // red tint — moderation actions
        SystemMessageType.CHANNEL_OWNERSHIP_CHANGED, SystemMessageType.CHANNEL_ICON_CHANGED,
        SystemMessageType.CHANNEL_DESCRIPTION_CHANGED, SystemMessageType.CHANNEL_RENAMED ->
            MaterialTheme.colorScheme.primaryContainer // blue tint — channel edits
        SystemMessageType.MESSAGE_PINNED, SystemMessageType.MESSAGE_UNPINNED ->
            MaterialTheme.colorScheme.primaryContainer // blue tint — pin events
        SystemMessageType.TEXT ->
            MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun contentColourForType(type: SystemMessageType): Color {
    return when (type) {
        SystemMessageType.USER_JOINED, SystemMessageType.USER_ADDED ->
            Color(0xFF7EDB9E) // green
        SystemMessageType.USER_LEFT, SystemMessageType.USER_REMOVE ->
            Color(0xFFDBB07E) // amber
        SystemMessageType.USER_BANNED, SystemMessageType.USER_KICKED ->
            Color(0xFFDB7E7E) // red
        SystemMessageType.CHANNEL_OWNERSHIP_CHANGED, SystemMessageType.CHANNEL_ICON_CHANGED,
        SystemMessageType.CHANNEL_DESCRIPTION_CHANGED, SystemMessageType.CHANNEL_RENAMED ->
            MaterialTheme.colorScheme.onPrimaryContainer
        SystemMessageType.MESSAGE_PINNED, SystemMessageType.MESSAGE_UNPINNED ->
            MaterialTheme.colorScheme.onPrimaryContainer
        SystemMessageType.TEXT ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SystemMessageIconWithBackground(
    type: SystemMessageType,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shapeForType(type))
            .background(
                color = backgroundColourForType(type),
            )
            .size(size)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColourForType(type),
        ) {
            SystemMessageIcon(type = type)
        }
    }
}
