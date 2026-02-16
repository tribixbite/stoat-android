package com.tribixbite.stoatally.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.internals.PermissionBit
import com.tribixbite.stoatally.api.internals.Roles
import com.tribixbite.stoatally.api.internals.has
import com.tribixbite.stoatally.api.routes.channel.removeMember
import com.tribixbite.stoatally.api.routes.server.banMember
import com.tribixbite.stoatally.api.routes.server.editMember
import com.tribixbite.stoatally.api.routes.server.kickMember
import com.tribixbite.stoatally.api.routes.user.MutualInfo
import com.tribixbite.stoatally.api.routes.user.fetchMutualFriendsAndServers
import com.tribixbite.stoatally.composables.generic.SheetButton
import com.tribixbite.stoatally.core.model.schemas.Role
import com.tribixbite.stoatally.internals.Platform
import kotlinx.coroutines.launch

@Composable
fun ColumnScope.GroupDMMemberContextSheet(
    userId: String,
    channelId: String,
    dismissSheet: suspend () -> Unit,
    onRequestUpdateMembers: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(channel) {
        if (channel == null) {
            dismissSheet()
        }
    }

    if (channel == null) return

    if (channel.owner == StoatAPI.selfId && userId != StoatAPI.selfId) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(
                            R.string.member_context_sheet_remove_from_channel,
                            channel.name ?: stringResource(R.string.unknown)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.icn_person_off_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = {
                scope.launch {
                    removeMember(channelId, userId)
                    onRequestUpdateMembers()
                    dismissSheet()
                }
            }
        )
    }

    // Always shown — ensures the sheet isn't empty when no moderation permissions
    SheetButton(
        headlineContent = {
            Text(stringResource(R.string.user_info_sheet_copy_id))
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.icn_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(userId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )


}

@Composable
fun ColumnScope.ServerMemberContextSheet(
    userId: String,
    serverId: String,
    channelId: String,
    dismissSheet: suspend () -> Unit,
    onRequestUpdateMembers: suspend () -> Unit
) {
    val server = StoatAPI.serverCache[serverId]
    val channel = StoatAPI.channelCache[channelId]
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showKickConfirmation by remember { mutableStateOf(false) }
    var showBanConfirmation by remember { mutableStateOf(false) }
    var banReason by remember { mutableStateOf("") }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showRolesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(server) {
        if (server == null || channel == null) {
            dismissSheet()
        }
    }

    if (server == null || channel == null) return

    // Calculate permissions for the current user on this server
    val selfMember = StoatAPI.members.getMember(serverId, StoatAPI.selfId ?: "")
    val permissions = if (selfMember != null) {
        Roles.permissionFor(server, selfMember)
    } else 0L

    val isOwner = server.owner == StoatAPI.selfId
    val isSelf = userId == StoatAPI.selfId

    // Kick confirmation dialog
    if (showKickConfirmation) {
        val targetUser = StoatAPI.userCache[userId]
        val displayName = targetUser?.displayName ?: targetUser?.username ?: userId

        AlertDialog(
            onDismissRequest = { showKickConfirmation = false },
            title = { Text(stringResource(R.string.moderation_kick_title)) },
            text = {
                Text(stringResource(R.string.moderation_kick_confirm, displayName))
            },
            confirmButton = {
                Button(onClick = {
                    showKickConfirmation = false
                    scope.launch {
                        try {
                            kickMember(serverId, userId)
                            onRequestUpdateMembers()
                            dismissSheet()
                            Toast.makeText(context, context.getString(R.string.moderation_kick_success), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text(stringResource(R.string.moderation_kick_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Ban confirmation dialog with optional reason
    if (showBanConfirmation) {
        val targetUser = StoatAPI.userCache[userId]
        val displayName = targetUser?.displayName ?: targetUser?.username ?: userId

        AlertDialog(
            onDismissRequest = { showBanConfirmation = false },
            title = { Text(stringResource(R.string.moderation_ban_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.moderation_ban_confirm, displayName))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = banReason,
                        onValueChange = { banReason = it },
                        label = { Text(stringResource(R.string.moderation_ban_reason_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBanConfirmation = false
                    scope.launch {
                        try {
                            banMember(serverId, userId, banReason.ifBlank { null })
                            onRequestUpdateMembers()
                            dismissSheet()
                            Toast.makeText(context, context.getString(R.string.moderation_ban_success), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text(stringResource(R.string.moderation_ban_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBanConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Nickname edit dialog
    if (showNicknameDialog) {
        val targetMember = StoatAPI.members.getMember(serverId, userId)
        var nickname by remember { mutableStateOf(targetMember?.nickname ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showNicknameDialog = false },
            title = { Text(stringResource(R.string.member_nickname_title)) },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.member_nickname_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                if (nickname.isBlank()) {
                                    editMember(serverId, userId, remove = listOf("Nickname"))
                                } else {
                                    editMember(serverId, userId, nickname = nickname)
                                }
                                showNicknameDialog = false
                                Toast.makeText(context, context.getString(R.string.member_nickname_updated), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(stringResource(R.string.server_settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Role assignment dialog
    if (showRolesDialog) {
        val targetMember = StoatAPI.members.getMember(serverId, userId)
        val currentRoles = remember { mutableStateOf(targetMember?.roles?.toMutableSet() ?: mutableSetOf()) }
        val serverRoles = server.roles ?: emptyMap()
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showRolesDialog = false },
            title = { Text(stringResource(R.string.member_roles_title)) },
            text = {
                Column {
                    serverRoles.entries.sortedBy { it.value.rank }.forEach { (roleId, role) ->
                        val isAssigned = roleId in currentRoles.value
                        SheetButton(
                            headlineContent = {
                                Text(role.name ?: roleId)
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(
                                        if (isAssigned) R.drawable.icn_check_24dp
                                        else R.drawable.icn_badge_24dp
                                    ),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                val updated = currentRoles.value.toMutableSet()
                                if (isAssigned) updated.remove(roleId) else updated.add(roleId)
                                currentRoles.value = updated
                            },
                            special = isAssigned
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                editMember(serverId, userId, roles = currentRoles.value.toList())
                                showRolesDialog = false
                                Toast.makeText(context, context.getString(R.string.member_roles_updated), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(stringResource(R.string.server_settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRolesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit nickname (requires ManageNicknames permission or self ChangeNickname)
    if ((isSelf && permissions has PermissionBit.ChangeNickname) ||
        (!isSelf && (isOwner || permissions has PermissionBit.ManageNicknames))) {
        SheetButton(
            headlineContent = { Text(stringResource(R.string.member_nickname_button)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.icn_edit_24dp),
                    contentDescription = null
                )
            },
            onClick = { showNicknameDialog = true }
        )
    }

    // Assign roles (requires AssignRoles permission)
    if (!isSelf && (isOwner || permissions has PermissionBit.AssignRoles)) {
        SheetButton(
            headlineContent = { Text(stringResource(R.string.member_roles_button)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.icn_badge_24dp),
                    contentDescription = null
                )
            },
            onClick = { showRolesDialog = true }
        )
    }

    // Kick member (requires KickMembers permission)
    if (!isSelf && (isOwner || permissions has PermissionBit.KickMembers)) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(stringResource(R.string.moderation_kick_button))
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.icn_person_off_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = { showKickConfirmation = true }
        )
    }

    // Ban member (requires BanMembers permission)
    if (!isSelf && (isOwner || permissions has PermissionBit.BanMembers)) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(stringResource(R.string.moderation_ban_button))
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.icn_gavel_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = { showBanConfirmation = true }
        )
    }

    // Mutual friends & servers (only for other users)
    if (!isSelf) {
        var mutualInfo by remember { mutableStateOf<MutualInfo?>(null) }
        var mutualLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(userId) {
            try {
                mutualInfo = fetchMutualFriendsAndServers(userId)
            } catch (_: Exception) {
                // Silently fail — mutual info is non-critical
            }
            mutualLoaded = true
        }

        if (mutualLoaded && mutualInfo != null) {
            val info = mutualInfo!!
            val hasMutuals = info.users.isNotEmpty() || info.servers.isNotEmpty()

            if (hasMutuals) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (info.users.isNotEmpty()) {
                    val friendNames = info.users.mapNotNull { friendId ->
                        val u = StoatAPI.userCache[friendId]
                        u?.displayName ?: u?.username ?: friendId
                    }
                    SheetButton(
                        headlineContent = {
                            Text(stringResource(R.string.mutual_friends_count, info.users.size))
                        },
                        supportingContent = {
                            Text(
                                friendNames.joinToString(", "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.icn_group_24dp),
                                contentDescription = null
                            )
                        },
                        onClick = {}
                    )
                }

                if (info.servers.isNotEmpty()) {
                    val serverNames = info.servers.mapNotNull { sId ->
                        StoatAPI.serverCache[sId]?.name ?: sId
                    }
                    SheetButton(
                        headlineContent = {
                            Text(stringResource(R.string.mutual_servers_count, info.servers.size))
                        },
                        supportingContent = {
                            Text(
                                serverNames.joinToString(", "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.icn_language_24dp),
                                contentDescription = null
                            )
                        },
                        onClick = {}
                    )
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Copy user ID
    SheetButton(
        headlineContent = {
            Text(stringResource(R.string.user_info_sheet_copy_id))
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.icn_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(userId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )
}