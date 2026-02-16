package com.tribixbite.stoatally.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.internals.PermissionBit
import com.tribixbite.stoatally.api.internals.Roles
import com.tribixbite.stoatally.api.internals.has
import com.tribixbite.stoatally.api.routes.server.leaveOrDeleteServer
import com.tribixbite.stoatally.api.settings.SyncedSettings
import com.tribixbite.stoatally.composables.generic.SheetButton
import com.tribixbite.stoatally.composables.markdown.RichMarkdown
import com.tribixbite.stoatally.composables.screens.settings.ServerOverview
import com.tribixbite.stoatally.internals.Platform
import kotlinx.coroutines.launch

@Composable
fun ServerContextSheet(
    serverId: String,
    onReportServer: () -> Unit,
    onHideSheet: suspend () -> Unit,
    onOpenServerSettings: (() -> Unit)? = null,
    onSearchServer: (() -> Unit)? = null
) {
    val server = StoatAPI.serverCache[serverId]

    if (server == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var leaveSilently by remember { mutableStateOf(false) }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showLeaveConfirmation = false
            },
            title = {
                Text(
                    text = stringResource(
                        id = R.string.server_context_sheet_actions_leave_confirm,
                        server.name ?: stringResource(R.string.unknown)
                    )
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(
                            id = R.string.server_context_sheet_actions_leave_confirm_eyebrow
                        )
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, end = 0.dp, top = 16.dp, bottom = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = leaveSilently,
                            onCheckedChange = { leaveSilently = it }
                        )
                        Text(
                            text = stringResource(
                                id = R.string.server_context_sheet_actions_leave_silently
                            ),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            onHideSheet()
                        }
                        coroutineScope.launch {
                            leaveOrDeleteServer(serverId, leaveSilently)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.server_context_sheet_actions_leave_confirm_yes
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirmation = false
                    }
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.server_context_sheet_actions_leave_confirm_no
                        )
                    )
                }
            }
        )
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
        ) {
            ServerOverview(server)

            SelectionContainer {
                RichMarkdown(
                    input = if (server.description?.isBlank() == false) {
                        server.description!!
                    } else {
                        stringResource(
                            R.string.server_context_sheet_description_empty
                        )
                    }
                )
            }

            HorizontalDivider()
        }

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.icn_identifier_copy_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.server_context_sheet_actions_copy_id)
                )
            },
            onClick = {
                if (server.id == null) return@SheetButton

                clipboardManager.setText(AnnotatedString(server.id!!))

                if (Platform.needsShowClipboardNotification()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.server_context_sheet_actions_copy_id_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                coroutineScope.launch {
                    onHideSheet()
                }
            }
        )

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.icn_mark_chat_read_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.server_context_sheet_actions_mark_read)
                )
            },
            onClick = {
                coroutineScope.launch {
                    server.id?.let {
                        StoatAPI.unreads.markServerAsRead(it, sync = true)
                    }
                    onHideSheet()
                }
            }
        )

        // Search entire server
        if (onSearchServer != null) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.icn_search_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.search_server)
                    )
                },
                onClick = {
                    coroutineScope.launch {
                        onHideSheet()
                    }
                    onSearchServer()
                }
            )
        }

        // Mute/unmute server toggle
        val isServerMuted = server.id?.let {
            SyncedSettings.notifications.server[it] == "muted"
        } ?: false

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(
                        id = if (isServerMuted) R.drawable.icn_volume_up_24dp
                        else R.drawable.icn_notification_settings_24dp
                    ),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = if (isServerMuted) {
                        stringResource(id = R.string.server_context_sheet_actions_unmute)
                    } else {
                        stringResource(id = R.string.server_context_sheet_actions_mute)
                    }
                )
            },
            onClick = {
                coroutineScope.launch {
                    server.id?.let { sid ->
                        val currentServerMap = SyncedSettings.notifications.server.toMutableMap()
                        if (isServerMuted) {
                            currentServerMap.remove(sid)
                        } else {
                            currentServerMap[sid] = "muted"
                        }
                        SyncedSettings.updateNotifications(
                            SyncedSettings.notifications.copy(server = currentServerMap)
                        )
                    }
                    onHideSheet()
                }
            }
        )

        // Server Settings — show for owner or members with ManageServer permission
        if (onOpenServerSettings != null) {
            val selfMember = StoatAPI.members.getMember(serverId, StoatAPI.selfId ?: "")
            val permissions = if (selfMember != null) {
                Roles.permissionFor(server, selfMember)
            } else 0L
            val isOwner = server.owner == StoatAPI.selfId

            if (isOwner || permissions has PermissionBit.ManageServer) {
                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.icn_settings_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.server_settings_title)
                        )
                    },
                    onClick = {
                        coroutineScope.launch {
                            onHideSheet()
                        }
                        onOpenServerSettings()
                    }
                )
            }
        }

        if (server.owner != StoatAPI.selfId) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.icn_report_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.server_context_sheet_actions_report),
                    )
                },
                dangerous = true,
                onClick = {
                    onReportServer()
                }
            )

            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.icn_door_open_24dp),
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.server_context_sheet_actions_leave)
                    )
                },
                dangerous = true,
                onClick = {
                    showLeaveConfirmation = true
                }
            )
        }
    }
}
