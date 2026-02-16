package com.tribixbite.stoatally.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.routes.account.SessionInfo
import com.tribixbite.stoatally.api.routes.account.fetchSessions
import com.tribixbite.stoatally.api.routes.account.renameSession
import com.tribixbite.stoatally.api.routes.account.revokeAllSessions
import com.tribixbite.stoatally.api.routes.account.revokeSession
import com.tribixbite.stoatally.composables.generic.ListHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionManagementScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val sessions = remember { mutableStateListOf<SessionInfo>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showRevokeConfirm by remember { mutableStateOf<SessionInfo?>(null) }
    var showRevokeAllConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<SessionInfo?>(null) }

    LaunchedEffect(Unit) {
        try {
            val fetched = fetchSessions()
            sessions.addAll(fetched)
        } catch (e: Exception) {
            loadError = e.message
        }
        isLoading = false
    }

    // Revoke single session dialog
    showRevokeConfirm?.let { session ->
        var isRevoking by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isRevoking) showRevokeConfirm = null },
            title = { Text(stringResource(R.string.session_revoke_title)) },
            text = { Text(stringResource(R.string.session_revoke_confirm, session.name ?: session.id)) },
            confirmButton = {
                Button(
                    onClick = {
                        isRevoking = true
                        scope.launch {
                            val error = revokeSession(session.id)
                            if (error == null) {
                                sessions.removeAll { it.id == session.id }
                                showRevokeConfirm = null
                                Toast.makeText(context, context.getString(R.string.session_revoked), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isRevoking = false
                        }
                    },
                    enabled = !isRevoking
                ) { Text(stringResource(R.string.session_revoke_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Revoke all sessions dialog
    if (showRevokeAllConfirm) {
        var isRevoking by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isRevoking) showRevokeAllConfirm = false },
            title = { Text(stringResource(R.string.session_revoke_all_title)) },
            text = { Text(stringResource(R.string.session_revoke_all_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        isRevoking = true
                        scope.launch {
                            val error = revokeAllSessions()
                            if (error == null) {
                                // Keep only the first session (likely current)
                                if (sessions.size > 1) {
                                    val first = sessions.first()
                                    sessions.clear()
                                    sessions.add(first)
                                }
                                showRevokeAllConfirm = false
                                Toast.makeText(context, context.getString(R.string.session_revoked_all), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isRevoking = false
                        }
                    },
                    enabled = !isRevoking
                ) { Text(stringResource(R.string.session_revoke_all_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Rename session dialog
    showRenameDialog?.let { session ->
        var name by remember { mutableStateOf(session.name ?: "") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showRenameDialog = null },
            title = { Text(stringResource(R.string.session_rename_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.session_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val error = renameSession(session.id, name)
                            if (error == null) {
                                val idx = sessions.indexOfFirst { it.id == session.id }
                                if (idx >= 0) {
                                    sessions[idx] = session.copy(name = name)
                                }
                                showRenameDialog = null
                                Toast.makeText(context, context.getString(R.string.session_renamed), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving && name.isNotBlank()
                ) { Text(stringResource(R.string.server_settings_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
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
                        text = stringResource(R.string.session_management_title),
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
                loadError != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = loadError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListHeader {
                                Text(stringResource(R.string.session_list_header, sessions.size))
                            }
                        }
                        items(sessions, key = { it.id }) { session ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        session.name ?: stringResource(R.string.session_unnamed),
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        session.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.icn_devices_24dp),
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { showRevokeConfirm = session }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_close_24dp),
                                            contentDescription = stringResource(R.string.session_revoke_title),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    showRenameDialog = session
                                }
                            )
                        }

                        // Revoke all sessions button
                        if (sessions.size > 1) {
                            item {
                                ListHeader {
                                    Text(stringResource(R.string.session_danger_zone))
                                }
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.session_revoke_all_button),
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    supportingContent = {
                                        Text(stringResource(R.string.session_revoke_all_hint))
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_delete_24dp),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        showRevokeAllConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
