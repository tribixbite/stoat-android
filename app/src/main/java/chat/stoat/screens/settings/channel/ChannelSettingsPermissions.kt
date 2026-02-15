package chat.stoat.screens.settings.channel

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import chat.stoat.api.routes.server.setChannelPermissions
import chat.stoat.composables.generic.ListHeader
import chat.stoat.screens.settings.server.PermState
import chat.stoat.screens.settings.server.SERVER_PERMISSION_CATEGORIES
import kotlinx.coroutines.launch

/**
 * Channel permission overrides screen.
 * Lists roles with existing overrides. Users can tap a role to edit its
 * tri-state (Allow / Neutral / Deny) overrides, or add a new role override.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsPermissions(navController: NavController, channelId: String) {
    val channel = StoatAPI.channelCache[channelId]
    val serverId = channel?.server
    val server = serverId?.let { StoatAPI.serverCache[it] }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Role override state: roleId -> (allow, deny) pair
    val roleOverrides = remember(channelId) {
        val overrides = mutableMapOf<String, Pair<Long, Long>>()
        channel?.rolePermissions?.forEach { (roleId, perm) ->
            overrides[roleId] = Pair(perm.a, perm.d)
        }
        overrides
    }

    // Currently editing role ID (null = role list view)
    var editingRoleId by remember { mutableStateOf<String?>(null) }
    var editAllow by remember { mutableLongStateOf(0L) }
    var editDeny by remember { mutableLongStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }

    // Dialog for selecting a new role to add
    var showAddRoleDialog by remember { mutableStateOf(false) }

    // Roles that already have overrides
    val configuredRoleIds = roleOverrides.keys.toSet()

    // Available roles from the server (excluding already configured ones)
    val availableRoles = server?.roles?.entries
        ?.filter { it.key !in configuredRoleIds }
        ?.sortedBy { it.value.rank }
        ?: emptyList()

    // Add role dialog
    if (showAddRoleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRoleDialog = false },
            title = { Text(stringResource(R.string.channel_perm_select_role)) },
            text = {
                if (availableRoles.isEmpty()) {
                    Text(
                        stringResource(R.string.channel_perm_no_roles),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(availableRoles.toList()) { (roleId, role) ->
                            ListItem(
                                headlineContent = { Text(role.name ?: roleId) },
                                modifier = Modifier.clickable {
                                    // Add with neutral (0, 0) and enter edit mode
                                    roleOverrides[roleId] = Pair(0L, 0L)
                                    editingRoleId = roleId
                                    editAllow = 0L
                                    editDeny = 0L
                                    showAddRoleDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddRoleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Helper functions for tri-state editing
    fun getState(bit: PermissionBit): PermState {
        return when {
            editAllow and bit.value == bit.value -> PermState.Allow
            editDeny and bit.value == bit.value -> PermState.Deny
            else -> PermState.Neutral
        }
    }

    fun setState(bit: PermissionBit, state: PermState) {
        editAllow = editAllow and bit.value.inv()
        editDeny = editDeny and bit.value.inv()
        when (state) {
            PermState.Allow -> editAllow = editAllow or bit.value
            PermState.Deny -> editDeny = editDeny or bit.value
            PermState.Neutral -> {} // cleared above
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    val titleText = if (editingRoleId != null) {
                        server?.roles?.get(editingRoleId)?.name
                            ?: stringResource(R.string.channel_settings_permissions)
                    } else {
                        stringResource(R.string.channel_settings_permissions)
                    }
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editingRoleId != null) {
                            // Go back to role list
                            editingRoleId = null
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (editingRoleId != null) {
                // Save button when editing a role's permissions
                FloatingActionButton(
                    onClick = {
                        val roleId = editingRoleId ?: return@FloatingActionButton
                        isSaving = true
                        scope.launch {
                            try {
                                setChannelPermissions(channelId, roleId, editAllow, editDeny)
                                roleOverrides[roleId] = Pair(editAllow, editDeny)
                                // Update channel cache
                                StoatAPI.channelCache[channelId]?.let { ch ->
                                    val updatedPerms = (ch.rolePermissions ?: emptyMap()).toMutableMap()
                                    updatedPerms[roleId] = chat.stoat.core.model.schemas.PermissionDescription(
                                        a = editAllow, d = editDeny
                                    )
                                    StoatAPI.channelCache[channelId] = ch.copy(rolePermissions = updatedPerms)
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.channel_perm_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                                editingRoleId = null
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
            } else {
                // Add role override button
                ExtendedFloatingActionButton(
                    onClick = { showAddRoleDialog = true },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.icn_add_24dp),
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.channel_perm_add_role)) }
                )
            }
        },
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            channel?.let {
                if (editingRoleId != null) {
                    // Edit mode: tri-state permission toggles for selected role
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                } else {
                    // Role list view
                    if (roleOverrides.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.channel_perm_no_roles),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                ListHeader {
                                    Text(stringResource(R.string.channel_perm_role_header))
                                }
                            }
                            items(roleOverrides.entries.toList(), key = { it.key }) { (roleId, perms) ->
                                val role = server?.roles?.get(roleId)
                                val allowCount = SERVER_PERMISSION_CATEGORIES
                                    .flatMap { it.entries }
                                    .count { perms.first and it.bit.value == it.bit.value }
                                val denyCount = SERVER_PERMISSION_CATEGORIES
                                    .flatMap { it.entries }
                                    .count { perms.second and it.bit.value == it.bit.value }

                                ListItem(
                                    headlineContent = { Text(role?.name ?: roleId) },
                                    supportingContent = {
                                        val parts = mutableListOf<String>()
                                        if (allowCount > 0) parts.add("$allowCount allowed")
                                        if (denyCount > 0) parts.add("$denyCount denied")
                                        if (parts.isEmpty()) parts.add("No overrides set")
                                        Text(parts.joinToString(" · "))
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_badge_24dp),
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        editingRoleId = roleId
                                        editAllow = perms.first
                                        editDeny = perms.second
                                    }
                                )
                            }
                        }
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
