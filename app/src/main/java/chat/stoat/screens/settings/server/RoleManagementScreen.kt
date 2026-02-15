package chat.stoat.screens.settings.server

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.server.createRole
import chat.stoat.api.routes.server.deleteRole
import chat.stoat.api.routes.server.editRole
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.schemas.Role
import kotlinx.coroutines.launch

/**
 * Parse a CSS hex colour string (#RRGGBB or #AARRGGBB) into a Compose Color.
 * Returns null on invalid input.
 */
private fun parseColour(hex: String?): Color? {
    if (hex == null) return null
    val clean = hex.removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#FF$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleManagementScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val server = StoatAPI.serverCache[serverId]

    // Mutable snapshot of roles keyed by ID for recomposition
    val roles = remember { mutableStateListOf<Pair<String, Role>>() }

    LaunchedEffect(serverId) {
        server?.roles?.entries?.sortedBy { it.value.rank }?.forEach { (id, role) ->
            roles.add(id to role)
        }
    }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Pair<String, Role>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Pair<String, Role>?>(null) }

    // Create role dialog
    if (showCreateDialog) {
        var newRoleName by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateDialog = false },
            title = { Text(stringResource(R.string.role_create_title)) },
            text = {
                OutlinedTextField(
                    value = newRoleName,
                    onValueChange = { newRoleName = it },
                    label = { Text(stringResource(R.string.role_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCreating = true
                        scope.launch {
                            try {
                                val result = createRole(serverId, newRoleName)
                                roles.add(result.id to result.role)
                                // Update cache
                                StoatAPI.serverCache[serverId]?.let { s ->
                                    val updated = (s.roles ?: emptyMap()).toMutableMap()
                                    updated[result.id] = result.role
                                    StoatAPI.serverCache[serverId] = s.copy(roles = updated)
                                }
                                showCreateDialog = false
                                Toast.makeText(context, context.getString(R.string.role_created), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            isCreating = false
                        }
                    },
                    enabled = newRoleName.isNotBlank() && !isCreating
                ) {
                    Text(stringResource(R.string.role_create_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit role dialog
    showEditDialog?.let { (roleId, role) ->
        var editName by remember { mutableStateOf(role.name ?: "") }
        var editColour by remember { mutableStateOf(role.colour ?: "") }
        var editHoist by remember { mutableStateOf(role.hoist ?: false) }
        var editRank by remember { mutableStateOf(role.rank?.toInt()?.toString() ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showEditDialog = null },
            title = { Text(stringResource(R.string.role_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.role_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editColour,
                        onValueChange = { editColour = it },
                        label = { Text(stringResource(R.string.role_colour_label)) },
                        placeholder = { Text("#FF0000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Colour preview
                    parseColour(editColour)?.let { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                    // Hoist toggle — display separately in member list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.role_hoist_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = editHoist,
                            onCheckedChange = { editHoist = it }
                        )
                    }
                    // Rank field
                    OutlinedTextField(
                        value = editRank,
                        onValueChange = { editRank = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.role_rank_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                val nameParam = if (editName != role.name) editName else null
                                val colourParam = if (editColour.isNotBlank() && editColour != role.colour) editColour else null
                                val removeParam = if (editColour.isBlank() && role.colour != null) listOf("Colour") else null
                                val hoistParam = if (editHoist != (role.hoist ?: false)) editHoist else null
                                val rankParam = editRank.toDoubleOrNull()?.let { r ->
                                    if (r != role.rank) r else null
                                }

                                val updated = editRole(
                                    serverId, roleId,
                                    name = nameParam,
                                    colour = colourParam,
                                    hoist = hoistParam,
                                    rank = rankParam,
                                    remove = removeParam
                                )
                                // Update local list
                                val idx = roles.indexOfFirst { it.first == roleId }
                                if (idx >= 0) roles[idx] = roleId to updated
                                // Update cache
                                StoatAPI.serverCache[serverId]?.let { s ->
                                    val map = (s.roles ?: emptyMap()).toMutableMap()
                                    map[roleId] = updated
                                    StoatAPI.serverCache[serverId] = s.copy(roles = map)
                                }
                                showEditDialog = null
                                Toast.makeText(context, context.getString(R.string.role_updated), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = editName.isNotBlank() && !isSaving
                ) {
                    Text(stringResource(R.string.server_settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { (roleId, role) ->
        var isDeleting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = null },
            title = { Text(stringResource(R.string.role_delete_title)) },
            text = {
                Text(stringResource(R.string.role_delete_confirm, role.name ?: roleId))
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                deleteRole(serverId, roleId)
                                roles.removeAll { it.first == roleId }
                                // Update cache
                                StoatAPI.serverCache[serverId]?.let { s ->
                                    val map = (s.roles ?: emptyMap()).toMutableMap()
                                    map.remove(roleId)
                                    StoatAPI.serverCache[serverId] = s.copy(roles = map)
                                }
                                showDeleteConfirm = null
                                Toast.makeText(context, context.getString(R.string.role_deleted), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            isDeleting = false
                        }
                    },
                    enabled = !isDeleting
                ) {
                    Text(stringResource(R.string.role_delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
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
                        text = stringResource(R.string.server_settings_roles),
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
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.icn_add_24dp),
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.role_create_button)) }
            )
        }
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            if (roles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.role_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        ListHeader {
                            Text(stringResource(R.string.role_list_header, roles.size))
                        }
                    }
                    items(roles, key = { it.first }) { (roleId, role) ->
                        val roleColor = parseColour(role.colour)
                        ListItem(
                            headlineContent = {
                                Text(
                                    role.name ?: roleId,
                                    color = roleColor ?: MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                val parts = mutableListOf<String>()
                                role.rank?.let { parts.add("Rank ${it.toInt()}") }
                                if (role.hoist == true) parts.add("Hoisted")
                                if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            roleColor ?: MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        navController.navigate("settings/server/$serverId/roles/$roleId/permissions")
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_lock_24dp),
                                            contentDescription = stringResource(R.string.perm_role_permissions_title)
                                        )
                                    }
                                    IconButton(onClick = { showEditDialog = roleId to role }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_edit_24dp),
                                            contentDescription = stringResource(R.string.role_edit_title)
                                        )
                                    }
                                    IconButton(onClick = { showDeleteConfirm = roleId to role }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_delete_24dp),
                                            contentDescription = stringResource(R.string.role_delete_title),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/roles/$roleId/permissions")
                            }
                        )
                    }
                }
            }
        }
    }
}
