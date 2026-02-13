package chat.stoat.screens.settings.server

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.routes.server.setDefaultPermissions
import chat.stoat.api.routes.server.setServerPermissions
import chat.stoat.composables.generic.ListHeader
import kotlinx.coroutines.launch

/**
 * A single permission entry with display info.
 */
data class PermissionEntry(
    val bit: PermissionBit,
    val nameRes: Int,
    val descriptionRes: Int
)

/**
 * A category of permissions.
 */
data class PermissionCategory(
    val nameRes: Int,
    val entries: List<PermissionEntry>
)

/** All server-level permission categories matching the web UI layout. */
val SERVER_PERMISSION_CATEGORIES = listOf(
    PermissionCategory(
        nameRes = R.string.perm_category_admin,
        entries = listOf(
            PermissionEntry(PermissionBit.ManageChannel, R.string.perm_manage_channel, R.string.perm_manage_channel_desc),
            PermissionEntry(PermissionBit.ManageServer, R.string.perm_manage_server, R.string.perm_manage_server_desc),
            PermissionEntry(PermissionBit.ManagePermissions, R.string.perm_manage_permissions, R.string.perm_manage_permissions_desc),
            PermissionEntry(PermissionBit.ManageRole, R.string.perm_manage_role, R.string.perm_manage_role_desc),
            PermissionEntry(PermissionBit.ManageCustomisation, R.string.perm_manage_customisation, R.string.perm_manage_customisation_desc),
        )
    ),
    PermissionCategory(
        nameRes = R.string.perm_category_members,
        entries = listOf(
            PermissionEntry(PermissionBit.KickMembers, R.string.perm_kick_members, R.string.perm_kick_members_desc),
            PermissionEntry(PermissionBit.BanMembers, R.string.perm_ban_members, R.string.perm_ban_members_desc),
            PermissionEntry(PermissionBit.TimeoutMembers, R.string.perm_timeout_members, R.string.perm_timeout_members_desc),
            PermissionEntry(PermissionBit.AssignRoles, R.string.perm_assign_roles, R.string.perm_assign_roles_desc),
            PermissionEntry(PermissionBit.ChangeNickname, R.string.perm_change_nickname, R.string.perm_change_nickname_desc),
            PermissionEntry(PermissionBit.ManageNicknames, R.string.perm_manage_nicknames, R.string.perm_manage_nicknames_desc),
            PermissionEntry(PermissionBit.ChangeAvatar, R.string.perm_change_avatar, R.string.perm_change_avatar_desc),
            PermissionEntry(PermissionBit.RemoveAvatars, R.string.perm_remove_avatars, R.string.perm_remove_avatars_desc),
        )
    ),
    PermissionCategory(
        nameRes = R.string.perm_category_channels,
        entries = listOf(
            PermissionEntry(PermissionBit.ViewChannel, R.string.perm_view_channel, R.string.perm_view_channel_desc),
            PermissionEntry(PermissionBit.ReadMessageHistory, R.string.perm_read_message_history, R.string.perm_read_message_history_desc),
            PermissionEntry(PermissionBit.SendMessage, R.string.perm_send_message, R.string.perm_send_message_desc),
            PermissionEntry(PermissionBit.ManageMessages, R.string.perm_manage_messages, R.string.perm_manage_messages_desc),
            PermissionEntry(PermissionBit.ManageWebhooks, R.string.perm_manage_webhooks, R.string.perm_manage_webhooks_desc),
            PermissionEntry(PermissionBit.InviteOthers, R.string.perm_invite_others, R.string.perm_invite_others_desc),
        )
    ),
    PermissionCategory(
        nameRes = R.string.perm_category_messaging,
        entries = listOf(
            PermissionEntry(PermissionBit.SendEmbeds, R.string.perm_send_embeds, R.string.perm_send_embeds_desc),
            PermissionEntry(PermissionBit.UploadFiles, R.string.perm_upload_files, R.string.perm_upload_files_desc),
            PermissionEntry(PermissionBit.Masquerade, R.string.perm_masquerade, R.string.perm_masquerade_desc),
            PermissionEntry(PermissionBit.React, R.string.perm_react, R.string.perm_react_desc),
            PermissionEntry(PermissionBit.MentionEveryone, R.string.perm_mention_everyone, R.string.perm_mention_everyone_desc),
            PermissionEntry(PermissionBit.MentionRoles, R.string.perm_mention_roles, R.string.perm_mention_roles_desc),
        )
    ),
    PermissionCategory(
        nameRes = R.string.perm_category_voice,
        entries = listOf(
            PermissionEntry(PermissionBit.Connect, R.string.perm_connect, R.string.perm_connect_desc),
            PermissionEntry(PermissionBit.Speak, R.string.perm_speak, R.string.perm_speak_desc),
            PermissionEntry(PermissionBit.Video, R.string.perm_video, R.string.perm_video_desc),
            PermissionEntry(PermissionBit.MuteMembers, R.string.perm_mute_members, R.string.perm_mute_members_desc),
            PermissionEntry(PermissionBit.DeafenMembers, R.string.perm_deafen_members, R.string.perm_deafen_members_desc),
            PermissionEntry(PermissionBit.MoveMembers, R.string.perm_move_members, R.string.perm_move_members_desc),
            PermissionEntry(PermissionBit.Listen, R.string.perm_listen, R.string.perm_listen_desc),
        )
    ),
)

/**
 * Tri-state for role permission overrides.
 * Allow = explicitly granted, Deny = explicitly denied, Neutral = inherited from default.
 */
enum class PermState { Allow, Neutral, Deny }

/**
 * Screen for editing default server permissions (simple on/off toggles).
 * Used with roleId = null to indicate default permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultPermissionsEditorScreen(
    navController: NavController,
    serverId: String
) {
    val server = StoatAPI.serverCache[serverId]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var currentPerms by remember { mutableLongStateOf(server?.defaultPermissions ?: 0L) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        server?.defaultPermissions?.let { currentPerms = it }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        stringResource(R.string.perm_default_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isSaving = true
                    scope.launch {
                        try {
                            setDefaultPermissions(serverId, currentPerms)
                            // Update local cache
                            StoatAPI.serverCache[serverId]?.let { s ->
                                StoatAPI.serverCache[serverId] = s.copy(defaultPermissions = currentPerms)
                            }
                            Toast.makeText(context, context.getString(R.string.perm_saved), Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.icn_check_24dp),
                        contentDescription = stringResource(R.string.server_settings_save)
                    )
                }
            }
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            SERVER_PERMISSION_CATEGORIES.forEach { category ->
                item(key = "header_${category.nameRes}") {
                    ListHeader {
                        Text(stringResource(category.nameRes))
                    }
                }
                items(category.entries, key = { it.bit.name }) { entry ->
                    val isEnabled = currentPerms and entry.bit.value == entry.bit.value

                    ListItem(
                        headlineContent = { Text(stringResource(entry.nameRes)) },
                        supportingContent = {
                            Text(
                                stringResource(entry.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    currentPerms = if (checked) {
                                        currentPerms or entry.bit.value
                                    } else {
                                        currentPerms and entry.bit.value.inv()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            currentPerms = if (isEnabled) {
                                currentPerms and entry.bit.value.inv()
                            } else {
                                currentPerms or entry.bit.value
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Screen for editing role permission overrides (tri-state: allow/neutral/deny).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolePermissionsEditorScreen(
    navController: NavController,
    serverId: String,
    roleId: String
) {
    val server = StoatAPI.serverCache[serverId]
    val role = server?.roles?.get(roleId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var allowBits by remember { mutableLongStateOf(role?.permissions?.a ?: 0L) }
    var denyBits by remember { mutableLongStateOf(role?.permissions?.d ?: 0L) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(serverId, roleId) {
        role?.permissions?.let {
            allowBits = it.a
            denyBits = it.d
        }
    }

    fun getState(bit: PermissionBit): PermState {
        return when {
            allowBits and bit.value == bit.value -> PermState.Allow
            denyBits and bit.value == bit.value -> PermState.Deny
            else -> PermState.Neutral
        }
    }

    fun setState(bit: PermissionBit, state: PermState) {
        // Clear both first
        allowBits = allowBits and bit.value.inv()
        denyBits = denyBits and bit.value.inv()
        // Set new state
        when (state) {
            PermState.Allow -> allowBits = allowBits or bit.value
            PermState.Deny -> denyBits = denyBits or bit.value
            PermState.Neutral -> {} // already cleared
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        role?.name ?: stringResource(R.string.perm_role_permissions_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isSaving = true
                    scope.launch {
                        try {
                            setServerPermissions(serverId, roleId, allowBits, denyBits)
                            // Update local cache
                            StoatAPI.serverCache[serverId]?.let { s ->
                                val updatedRoles = (s.roles ?: emptyMap()).toMutableMap()
                                updatedRoles[roleId] = (updatedRoles[roleId] ?: role)?.copy(
                                    permissions = chat.stoat.core.model.schemas.PermissionDescription(
                                        a = allowBits, d = denyBits
                                    )
                                ) ?: return@let
                                StoatAPI.serverCache[serverId] = s.copy(roles = updatedRoles)
                            }
                            Toast.makeText(context, context.getString(R.string.perm_saved), Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.icn_check_24dp),
                        contentDescription = stringResource(R.string.server_settings_save)
                    )
                }
            }
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            SERVER_PERMISSION_CATEGORIES.forEach { category ->
                item(key = "header_${category.nameRes}") {
                    ListHeader {
                        Text(stringResource(category.nameRes))
                    }
                }
                items(category.entries, key = { it.bit.name }) { entry ->
                    val state = getState(entry.bit)

                    ListItem(
                        headlineContent = { Text(stringResource(entry.nameRes)) },
                        supportingContent = {
                            Column {
                                Text(
                                    stringResource(entry.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Tri-state segmented button: Allow / Neutral / Deny
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    SegmentedButton(
                                        selected = state == PermState.Allow,
                                        onClick = { setState(entry.bit, PermState.Allow) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                        icon = {}
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.icn_check_24dp),
                                                contentDescription = null,
                                                tint = if (state == PermState.Allow) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    SegmentedButton(
                                        selected = state == PermState.Neutral,
                                        onClick = { setState(entry.bit, PermState.Neutral) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                        icon = {}
                                    ) {
                                        Text("—")
                                    }
                                    SegmentedButton(
                                        selected = state == PermState.Deny,
                                        onClick = { setState(entry.bit, PermState.Deny) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                        icon = {}
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.icn_close_24dp),
                                                contentDescription = null,
                                                tint = if (state == PermState.Deny) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
