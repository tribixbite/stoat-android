package com.tribixbite.stoatally.screens.settings.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.routes.webhooks.createWebhook
import com.tribixbite.stoatally.api.routes.webhooks.deleteWebhook
import com.tribixbite.stoatally.api.routes.webhooks.editWebhook
import com.tribixbite.stoatally.api.routes.webhooks.executeWebhook
import com.tribixbite.stoatally.api.routes.webhooks.fetchChannelWebhooks
import com.tribixbite.stoatally.core.model.schemas.Channel
import com.tribixbite.stoatally.core.model.schemas.ChannelType
import com.tribixbite.stoatally.core.model.schemas.Webhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Webhook management for a server — shows webhooks across all channels.
 * Users can create, edit, and delete webhooks for channels they have ManageWebhooks on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookManagementScreen(navController: NavController, serverId: String) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val server = StoatAPI.serverCache[serverId]

    var webhooks by remember { mutableStateOf<List<Webhook>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Get text channels in this server
    val textChannels = remember(serverId) {
        server?.channels?.mapNotNull { channelId ->
            StoatAPI.channelCache[channelId]
        }?.filter { it.channelType == ChannelType.TextChannel } ?: emptyList()
    }

    // Fetch webhooks from all text channels in parallel
    LaunchedEffect(serverId) {
        try {
            val allWebhooks = coroutineScope {
                textChannels.map { channel ->
                    async(Dispatchers.IO) {
                        try {
                            fetchChannelWebhooks(channel.id ?: "")
                        } catch (_: Exception) {
                            // May not have permission on all channels — skip
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
            webhooks = allWebhooks
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
    }

    // Create webhook dialog
    if (showCreateDialog) {
        CreateWebhookDialog(
            textChannels = textChannels,
            onDismiss = { showCreateDialog = false },
            onCreated = { webhook ->
                webhooks = webhooks + webhook
                showCreateDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.webhook_management_title),
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
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_add_24dp),
                            contentDescription = stringResource(R.string.webhook_management_create)
                        )
                    }
                }
            )
        },
    ) { pv ->
        Box(Modifier.padding(pv)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                error != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }
                }

                webhooks.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.icn_link_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.webhook_management_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(webhooks, key = { it.id ?: "" }) { webhook ->
                            val channel = webhook.channelId?.let { StoatAPI.channelCache[it] }
                            WebhookListItem(
                                webhook = webhook,
                                channelName = channel?.name ?: webhook.channelId ?: "",
                                context = context,
                                onDeleted = {
                                    webhooks = webhooks.filter { it.id != webhook.id }
                                },
                                onUpdated = { updated ->
                                    webhooks = webhooks.map {
                                        if (it.id == updated.id) updated else it
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWebhookDialog(
    textChannels: List<Channel>,
    onDismiss: () -> Unit,
    onCreated: (Webhook) -> Unit
) {
    var webhookName by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var channelExpanded by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.webhook_management_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = webhookName,
                    onValueChange = { webhookName = it },
                    label = { Text(stringResource(R.string.webhook_management_name)) },
                    placeholder = { Text(stringResource(R.string.webhook_management_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating
                )

                Spacer(Modifier.height(12.dp))

                // Channel selector dropdown
                ExposedDropdownMenuBox(
                    expanded = channelExpanded,
                    onExpandedChange = { channelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedChannel?.let { "#${it.name}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.webhook_management_channel)) },
                        placeholder = { Text(stringResource(R.string.webhook_management_select_channel)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        enabled = !isCreating
                    )
                    ExposedDropdownMenu(
                        expanded = channelExpanded,
                        onDismissRequest = { channelExpanded = false }
                    ) {
                        textChannels.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text("#${channel.name ?: channel.id}") },
                                onClick = {
                                    selectedChannel = channel
                                    channelExpanded = false
                                }
                            )
                        }
                    }
                }

                if (createError != null) {
                    Text(
                        createError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { isCreating = true },
                enabled = webhookName.isNotBlank() && selectedChannel != null && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.webhook_management_create))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (isCreating) {
        LaunchedEffect(webhookName, selectedChannel?.id) {
            try {
                val result = withContext(Dispatchers.IO) {
                    createWebhook(selectedChannel?.id ?: "", webhookName)
                }
                onCreated(result)
            } catch (e: Exception) {
                createError = e.message
            }
            isCreating = false
        }
    }
}

@Composable
private fun WebhookListItem(
    webhook: Webhook,
    channelName: String,
    context: Context,
    onDeleted: () -> Unit,
    onUpdated: (Webhook) -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    var editName by remember(webhook) { mutableStateOf(webhook.name ?: "") }
    var editError by remember { mutableStateOf<String?>(null) }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.webhook_management_delete)) },
            text = { Text(stringResource(R.string.webhook_management_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { isDeleting = true },
                    enabled = !isDeleting
                ) {
                    Text(
                        stringResource(R.string.webhook_management_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        if (isDeleting) {
            LaunchedEffect(webhook.id) {
                try {
                    withContext(Dispatchers.IO) { deleteWebhook(webhook.id ?: "") }
                    onDeleted()
                } catch (e: Exception) {
                    editError = e.message
                }
                isDeleting = false
                showDeleteDialog = false
            }
        }
    }

    // Test webhook dialog
    if (showTestDialog) {
        var testContent by remember { mutableStateOf("") }
        var isTesting by remember { mutableStateOf(false) }
        var testError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isTesting) showTestDialog = false },
            title = { Text(stringResource(R.string.webhook_management_test_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = testContent,
                        onValueChange = { testContent = it },
                        label = { Text(stringResource(R.string.webhook_management_test_content)) },
                        placeholder = { Text(stringResource(R.string.webhook_management_test_content_hint)) },
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTesting
                    )
                    if (testError != null) {
                        Text(
                            testError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isTesting = true
                        testError = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    executeWebhook(
                                        webhookId = webhook.id ?: "",
                                        token = webhook.token ?: "",
                                        content = testContent.ifBlank { null }
                                    )
                                }
                                Toast.makeText(context, R.string.webhook_management_test_success, Toast.LENGTH_SHORT).show()
                                showTestDialog = false
                            } catch (e: Exception) {
                                testError = e.message
                            }
                            isTesting = false
                        }
                    },
                    enabled = testContent.isNotBlank() && !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.webhook_management_test))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTestDialog = false },
                    enabled = !isTesting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Save edits
    if (isEditing) {
        LaunchedEffect(editName) {
            try {
                val result = withContext(Dispatchers.IO) {
                    editWebhook(
                        webhookId = webhook.id ?: "",
                        name = if (editName != webhook.name) editName else null
                    )
                }
                onUpdated(result)
            } catch (e: Exception) {
                editError = e.message
            }
            isEditing = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    webhook.name ?: "Webhook",
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    "#$channelName",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.icn_link_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(
                        R.drawable.icn_keyboard_arrow_right_24dp
                    ),
                    contentDescription = null
                )
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Webhook ID
                Text(
                    "ID: ${webhook.id ?: ""}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Token (if available)
                webhook.token?.let { token ->
                    Text(
                        stringResource(R.string.webhook_management_token),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${token.take(16)}...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Webhook Token", token))
                            Toast.makeText(context, R.string.webhook_management_token_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.icn_content_copy_24dp),
                                contentDescription = "Copy token"
                            )
                        }
                    }

                    // Copy full webhook URL
                    TextButton(onClick = {
                        val url = "https://api.stoat.chat/webhooks/${webhook.id}/$token"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Webhook URL", url))
                        Toast.makeText(context, R.string.webhook_management_url_copied, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_content_copy_24dp),
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.webhook_management_url))
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // Name editor
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.webhook_management_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (editError != null) {
                    Text(
                        editError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { isEditing = true },
                        enabled = editName != (webhook.name ?: "") && editName.isNotBlank() && !isEditing
                    ) {
                        if (isEditing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.server_settings_save))
                        }
                    }

                    // Test webhook button — only if token is available
                    if (webhook.token != null) {
                        TextButton(onClick = { showTestDialog = true }) {
                            Text(stringResource(R.string.webhook_management_test))
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text(
                            stringResource(R.string.webhook_management_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
