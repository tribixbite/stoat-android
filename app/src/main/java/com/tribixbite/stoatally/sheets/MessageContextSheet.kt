package com.tribixbite.stoatally.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.tribixbite.stoatally.BuildConfig
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.STOAT_WEB_APP
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.internals.PermissionBit
import com.tribixbite.stoatally.api.internals.Roles
import com.tribixbite.stoatally.api.internals.has
import com.tribixbite.stoatally.api.internals.ULID
import com.tribixbite.stoatally.api.routes.channel.ackChannel
import com.tribixbite.stoatally.api.routes.channel.bulkDeleteMessages
import com.tribixbite.stoatally.api.routes.channel.deleteMessage
import com.tribixbite.stoatally.api.routes.channel.pinMessage
import com.tribixbite.stoatally.api.routes.channel.react
import com.tribixbite.stoatally.api.routes.channel.removeAllReactions
import com.tribixbite.stoatally.api.routes.channel.unpinMessage
import com.tribixbite.stoatally.api.settings.Experiments
import com.tribixbite.stoatally.callbacks.UiCallbacks
import com.tribixbite.stoatally.composables.chat.Message
import com.tribixbite.stoatally.composables.generic.SheetButton
import com.tribixbite.stoatally.internals.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageContextSheet(
    messageId: String,
    onHideSheet: suspend () -> Unit,
    onReportMessage: () -> Unit
) {
    val message = StoatAPI.messageCache[messageId]
    if (message == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showShareSheet by remember { mutableStateOf(false) }
    var showReactSheet by remember { mutableStateOf(false) }
    var showDeleteMessageConfirmation by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    var showInspectASTSheet by remember { mutableStateOf(false) }
    val showInspectASTSheetButton =
        BuildConfig.DEBUG || (message.content != null && Experiments.useKotlinBasedMarkdownRenderer.isEnabled)

    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            sheetState = shareSheetState,
            onDismissRequest = {
                showShareSheet = false
            }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.icn_content_copy_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.message_context_sheet_actions_copy)
                        )
                    },
                    onClick = {
                        if (message.content.isNullOrEmpty()) {
                            coroutineScope.launch {
                                shareSheetState.hide()
                                onHideSheet()
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.message_context_sheet_actions_copy_failed_empty
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@SheetButton
                        }

                        if (Platform.needsShowClipboardNotification()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        coroutineScope.launch {
                            shareSheetState.hide()
                        }
                        coroutineScope.launch {
                            clipboardManager.setText(AnnotatedString(message.content!!))
                            onHideSheet()
                        }
                    }
                )

                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.icn_link_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(
                                id = R.string.message_context_sheet_actions_copy_link
                            )
                        )
                    },
                    onClick = {
                        if (message.content.isNullOrEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.message_context_sheet_actions_copy_failed_empty
                                ),
                                Toast.LENGTH_SHORT
                            ).show()

                            coroutineScope.launch {
                                shareSheetState.hide()
                            }
                            coroutineScope.launch {
                                onHideSheet()
                            }

                            return@SheetButton
                        }

                        val server = StoatAPI.serverCache.values.find { server ->
                            server.channels?.contains(message.channel) ?: false
                        }
                        val messageLink =
                            "$STOAT_WEB_APP/server/${server?.id}/channel/${message.channel}/${message.id}"

                        clipboardManager.setText(AnnotatedString(messageLink))
                        if (Platform.needsShowClipboardNotification()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.message_context_sheet_actions_copy_link_copied
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        coroutineScope.launch {
                            shareSheetState.hide()
                        }
                        coroutineScope.launch {
                            onHideSheet()
                        }
                    }
                )

                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.icn_identifier_copy_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(
                                id = R.string.message_context_sheet_actions_copy_id
                            )
                        )
                    },
                    onClick = {
                        if (message.id == null) return@SheetButton

                        clipboardManager.setText(AnnotatedString(message.id!!))

                        if (Platform.needsShowClipboardNotification()) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.message_context_sheet_actions_copy_id_copied
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        coroutineScope.launch {
                            shareSheetState.hide()
                        }
                        coroutineScope.launch {
                            onHideSheet()
                        }
                    }
                )
            }


        }
    }

    if (showReactSheet) {
        val reactSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            sheetState = reactSheetState,
            onDismissRequest = {
                showReactSheet = false
            }
        ) {
            ReactSheet(messageId) {
                if (it == null) return@ReactSheet

                coroutineScope.launch {
                    message.channel?.let { channelId ->
                        react(channelId, messageId, it)
                    }

                    reactSheetState.hide()
                    onHideSheet()
                }
            }
        }
    }

    if (showDeleteMessageConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteMessageConfirmation = false
            },
            title = {
                Text(
                    text = stringResource(R.string.message_context_sheet_actions_delete_confirmation_title)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.message_context_sheet_actions_delete_confirmation_body)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteMessageConfirmation = false
                        coroutineScope.launch {
                            onHideSheet()
                            message.channel?.let { channelId ->
                                deleteMessage(channelId, messageId)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.message_context_sheet_actions_delete_confirmation_yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteMessageConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.message_context_sheet_actions_delete_confirmation_no))
                }
            }
        )
    }

    // Bulk delete dialog — find recent messages in cache and delete in batch
    if (showBulkDeleteConfirmation) {
        val channelId = message.channel
        // Collect up to 100 recent message IDs from cache for this channel
        val channelMessages = remember {
            StoatAPI.messageCache.values
                .filter { it.channel == channelId && it.id != null }
                .sortedByDescending { it.id } // ULID sorts chronologically
                .take(100)
                .map { it.id!! }
        }

        var deleteCount by remember { mutableStateOf(10) }
        var isDeleting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isDeleting) showBulkDeleteConfirmation = false },
            title = { Text(stringResource(R.string.bulk_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.bulk_delete_description, deleteCount))
                    Spacer(modifier = Modifier.height(12.dp))
                    // Count selector: row of preset buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 25, 50, 100).forEach { count ->
                            val available = minOf(count, channelMessages.size)
                            TextButton(
                                onClick = { deleteCount = available },
                                enabled = channelMessages.size >= count
                            ) {
                                Text(
                                    "$count",
                                    color = if (deleteCount == available)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.bulk_delete_available, channelMessages.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        coroutineScope.launch {
                            try {
                                val idsToDelete = channelMessages.take(deleteCount)
                                if (channelId != null && idsToDelete.isNotEmpty()) {
                                    bulkDeleteMessages(channelId, idsToDelete)
                                }
                                showBulkDeleteConfirmation = false
                                onHideSheet()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.bulk_delete_success, idsToDelete.size),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            isDeleting = false
                        }
                    },
                    enabled = !isDeleting && channelMessages.isNotEmpty()
                ) {
                    Text(stringResource(R.string.bulk_delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showInspectASTSheet) {
        val inspectASTSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            sheetState = inspectASTSheetState,
            onDismissRequest = {
                showInspectASTSheet = false
            }
        ) {
            JBMDebuggerSheet(message.content ?: "")
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
        ) {
            Message(
                message = message.copy(
                    tail = false,
                    masquerade = null
                )
            )

            HorizontalDivider()
        }

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.icn_reply_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_reply),
                )
            },
            onClick = {
                coroutineScope.launch {
                    UiCallbacks.replyToMessage(messageId)
                    onHideSheet()
                }
            }
        )

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.icn_add_reaction_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_react),
                )
            },
            onClick = {
                showReactSheet = true
            }
        )

        // Pin/unpin message and remove reactions (requires ManageMessages permission)
        val hasManageMessages = (message.channel?.let {
            val channel = StoatAPI.channelCache[it] ?: return@let null
            Roles.permissionFor(
                channel,
                StoatAPI.userCache[StoatAPI.selfId]
            )
        } ?: 0) has PermissionBit.ManageMessages

        if (hasManageMessages) {
            val isPinned = message.pinned == true
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pin_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(
                            if (isPinned) R.string.message_context_sheet_actions_unpin
                            else R.string.message_context_sheet_actions_pin
                        ),
                    )
                },
                onClick = {
                    coroutineScope.launch {
                        try {
                            message.channel?.let { channelId ->
                                if (isPinned) {
                                    unpinMessage(channelId, messageId)
                                } else {
                                    pinMessage(channelId, messageId)
                                }
                            }
                            onHideSheet()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )

            // Remove all reactions (only show if message has reactions)
            if (!message.reactions.isNullOrEmpty()) {
                SheetButton(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.icn_close_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.message_context_sheet_actions_remove_reactions),
                        )
                    },
                    onClick = {
                        coroutineScope.launch {
                            try {
                                message.channel?.let { channelId ->
                                    removeAllReactions(channelId, messageId)
                                }
                                onHideSheet()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Failed: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        if (message.author == StoatAPI.selfId) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.icn_edit_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_edit),
                    )
                },
                onClick = {
                    coroutineScope.launch {
                        UiCallbacks.editMessage(messageId)
                        onHideSheet()
                    }
                }
            )
        }

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.icn_visibility_off_24dp),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_mark_unread),
                )
            },
            onClick = {
                // Ack to a synthetic ULID just before this message so the channel
                // appears unread from this message onwards.
                val channelId = message.channel
                val msgId = message.id
                if (channelId != null && msgId != null) {
                    coroutineScope.launch {
                        try {
                            val ts = ULID.asTimestamp(msgId)
                            val beforeId = ULID.makeSpecial(
                                maxOf(ts - 1, 0),
                                ByteArray(10) // all zeros — sorts before any real ULID at this ms
                            )
                            ackChannel(channelId, beforeId)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: e.toString(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        onHideSheet()
                    }
                }
            }
        )

        if (showInspectASTSheetButton) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.icn_account_tree_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = "Inspect AST"
                    )
                },
                onClick = {
                    showInspectASTSheet = true
                },
                special = true
            )
        }

        // Copy text directly (upstream #40: copy outside share sub-menu)
        if (!message.content.isNullOrEmpty()) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.icn_content_copy_24dp),
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_copy),
                    )
                },
                onClick = {
                    if (Platform.needsShowClipboardNotification()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    clipboardManager.setText(AnnotatedString(message.content!!))
                    coroutineScope.launch { onHideSheet() }
                }
            )
        }

        // Copy message ID directly (upstream #40)
        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_content_copy_id_24dp),
                    contentDescription = null,
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.message_context_sheet_actions_copy_id),
                )
            },
            onClick = {
                if (Platform.needsShowClipboardNotification()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.message_context_sheet_actions_copy_id_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                clipboardManager.setText(AnnotatedString(messageId))
                coroutineScope.launch { onHideSheet() }
            }
        )

        SheetButton(
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.icn_ios_share_24dp),
                    contentDescription = null,
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.share),
                )
            },
            onClick = {
                showShareSheet = true
            }
        )

        if (hasManageMessages || message.author == StoatAPI.selfId) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.icn_delete_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_delete),
                    )
                },
                dangerous = true,
                onClick = {
                    showDeleteMessageConfirmation = true
                }
            )
        }

        // Bulk delete (moderator-only)
        if (hasManageMessages) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.icn_delete_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.bulk_delete_title),
                    )
                },
                dangerous = true,
                onClick = {
                    showBulkDeleteConfirmation = true
                }
            )
        }

        if (message.author != StoatAPI.selfId) {
            SheetButton(
                leadingContent = {
                    Icon(
                        painter = painterResource(id = R.drawable.icn_report_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.message_context_sheet_actions_report),
                    )
                },
                dangerous = true,
                onClick = {
                    coroutineScope.launch {
                        onReportMessage()
                    }
                },
            )
        }


    }
}
