package com.tribixbite.stoatally.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.internals.ChannelUtils
import com.tribixbite.stoatally.core.model.schemas.ChannelType
import com.tribixbite.stoatally.core.model.schemas.User
import com.tribixbite.stoatally.api.settings.LoadedSettings
import com.tribixbite.stoatally.composables.generic.GroupIcon
import com.tribixbite.stoatally.composables.generic.UserAvatar
import com.tribixbite.stoatally.composables.generic.presenceFromStatus
import com.tribixbite.stoatally.internals.extensions.zero

// Note - this is not a traditional screen per se, as it is a part of the main screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(navController: NavController) {
    val dmAbleChannels =
        StoatAPI.channelCache.values
            .filter { it.channelType == ChannelType.DirectMessage || it.channelType == ChannelType.Group }
            .filter { if (it.channelType == ChannelType.DirectMessage) it.active == true else true }
            .sortedBy { it.lastMessageID ?: it.id }
            .reversed()

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(NavigationBarDefaults.windowInsets),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.main_tab_conversations)) },
                windowInsets = WindowInsets.zero
            )
        },
    ) { pv ->
        LazyColumn(
            modifier = Modifier.padding(pv),
        ) {
            // Saved Messages (notes to self) pinned at top
            item(key = "saved_messages") {
                val notesChannel =
                    StoatAPI.channelCache.values.firstOrNull { it.channelType == ChannelType.SavedMessages }
                val lastMessage = notesChannel?.lastMessageID?.let { StoatAPI.messageCache[it] }
                val preview = lastMessage?.content?.trim()

                if (notesChannel != null) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.channel_notes))
                        },
                        supportingContent = {
                            if (!preview.isNullOrBlank()) {
                                Text(
                                    preview,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        leadingContent = {
                            Box(contentAlignment = Alignment.TopEnd) {
                                StoatAPI.userCache[StoatAPI.selfId]?.let {
                                    UserAvatar(
                                        username = it.username.toString(),
                                        avatar = it.avatar,
                                        userId = it.id.toString(),
                                        shape = RoundedCornerShape(LoadedSettings.avatarRadius)
                                    )
                                }
                                Badge {
                                    Icon(
                                        painter = painterResource(R.drawable.icn_keep_24dp),
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            navController.navigate("main/conversation/${notesChannel.id}")
                        }
                    )
                    HorizontalDivider()
                }
            }

            // Real DM and group conversations
            items(
                items = dmAbleChannels,
                key = { it.id ?: it.hashCode() }
            ) { channel ->
                val partner =
                    if (channel.channelType == ChannelType.DirectMessage) {
                        StoatAPI.userCache[ChannelUtils.resolveDMPartner(channel)]
                    } else {
                        null
                    }

                val displayName = when (channel.channelType) {
                    ChannelType.Group -> channel.name ?: stringResource(R.string.unknown)
                    ChannelType.DirectMessage -> partner?.let { User.resolveDefaultName(it) }
                        ?: stringResource(R.string.unknown)
                    else -> channel.name ?: stringResource(R.string.unknown)
                }

                // Last message preview
                val lastMessage = channel.lastMessageID?.let { StoatAPI.messageCache[it] }
                val messagePreview = lastMessage?.content?.trim()

                // Unread indicator
                val hasUnread = channel.lastMessageID?.let { lastMsgId ->
                    channel.id?.let { chId ->
                        StoatAPI.unreads.hasUnread(chId, lastMsgId, serverId = null)
                    }
                } ?: false

                ListItem(
                    headlineContent = {
                        Text(
                            text = displayName,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        if (!messagePreview.isNullOrBlank()) {
                            Text(
                                messagePreview,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingContent = {
                        when (channel.channelType) {
                            ChannelType.Group -> {
                                GroupIcon(
                                    name = channel.name ?: "?",
                                    size = 48.dp,
                                )
                            }
                            else -> {
                                // DM — show partner's avatar with presence
                                UserAvatar(
                                    username = partner?.username ?: displayName,
                                    avatar = partner?.avatar,
                                    userId = partner?.id ?: channel.id ?: "",
                                    presence = partner?.let {
                                        presenceFromStatus(
                                            it.status?.presence,
                                            it.online ?: false
                                        )
                                    },
                                    shape = RoundedCornerShape(LoadedSettings.avatarRadius),
                                    size = 48.dp,
                                )
                            }
                        }
                    },
                    trailingContent = if (hasUnread) {
                        {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("main/conversation/${channel.id}")
                        }
                )
            }
        }
    }
}
