package chat.stoat.screens.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import chat.stoat.R
import chat.stoat.api.routes.bots.BotInfo
import chat.stoat.api.routes.bots.OwnedBotsResponse
import chat.stoat.api.routes.bots.createBot
import chat.stoat.api.routes.bots.deleteBot
import chat.stoat.api.routes.bots.editBot
import chat.stoat.api.routes.bots.fetchOwnedBots
import chat.stoat.core.model.schemas.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var botsResponse by remember { mutableStateOf<OwnedBotsResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }

    // Fetch owned bots on load
    LaunchedEffect(Unit) {
        try {
            botsResponse = withContext(Dispatchers.IO) { fetchOwnedBots() }
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
    }

    // Create bot dialog
    if (showCreateDialog) {
        var botName by remember { mutableStateOf("") }
        var createError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateDialog = false },
            title = { Text(stringResource(R.string.bot_management_create_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = botName,
                        onValueChange = { botName = it },
                        label = { Text(stringResource(R.string.bot_management_name)) },
                        placeholder = { Text(stringResource(R.string.bot_management_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating
                    )
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
                    onClick = {
                        if (botName.length in 2..32) {
                            isCreating = true
                            createError = null
                            // Launch coroutine from LaunchedEffect context
                        }
                    },
                    enabled = botName.length in 2..32 && !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.bot_management_create))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    enabled = !isCreating
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        // Handle creation in LaunchedEffect when isCreating becomes true
        if (isCreating) {
            LaunchedEffect(botName) {
                try {
                    val result = withContext(Dispatchers.IO) { createBot(botName) }
                    // Add to local list
                    botsResponse = botsResponse?.let { existing ->
                        OwnedBotsResponse(
                            bots = existing.bots + result.bot,
                            users = existing.users + result.user
                        )
                    } ?: OwnedBotsResponse(bots = listOf(result.bot), users = listOf(result.user))
                    showCreateDialog = false
                } catch (e: Exception) {
                    createError = e.message
                }
                isCreating = false
            }
        }
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
                        text = stringResource(R.string.bot_management_title),
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
                            contentDescription = stringResource(R.string.bot_management_create)
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
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                }

                botsResponse?.bots.isNullOrEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.icn_smart_toy_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.bot_management_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    val bots = botsResponse!!.bots
                    val users = botsResponse!!.users

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(bots, key = { it.id ?: "" }) { bot ->
                            val botUser = users.find { it.id == bot.id }
                            BotListItem(
                                bot = bot,
                                user = botUser,
                                context = context,
                                onDeleted = {
                                    botsResponse = botsResponse?.copy(
                                        bots = botsResponse!!.bots.filter { it.id != bot.id }
                                    )
                                },
                                onUpdated = { updated ->
                                    botsResponse = botsResponse?.copy(
                                        bots = botsResponse!!.bots.map {
                                            if (it.id == updated.id) updated else it
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotListItem(
    bot: BotInfo,
    user: User?,
    context: Context,
    onDeleted: () -> Unit,
    onUpdated: (BotInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Editable state
    var editName by remember(bot) { mutableStateOf(bot.name ?: "") }
    var editPublic by remember(bot) { mutableStateOf(bot.public ?: false) }
    var editAnalytics by remember(bot) { mutableStateOf(bot.analytics ?: false) }
    var editInteractionsUrl by remember(bot) { mutableStateOf(bot.interactionsUrl ?: "") }
    var editError by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.bot_management_delete)) },
            text = { Text(stringResource(R.string.bot_management_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { isDeleting = true },
                    enabled = !isDeleting
                ) {
                    Text(
                        stringResource(R.string.bot_management_delete),
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
            LaunchedEffect(bot.id) {
                try {
                    withContext(Dispatchers.IO) { deleteBot(bot.id ?: "") }
                    onDeleted()
                } catch (e: Exception) {
                    editError = e.message
                }
                isDeleting = false
                showDeleteDialog = false
            }
        }
    }

    // Save edits handler
    if (isEditing) {
        LaunchedEffect(editName, editPublic, editAnalytics, editInteractionsUrl) {
            try {
                val result = withContext(Dispatchers.IO) {
                    editBot(
                        botId = bot.id ?: "",
                        name = if (editName != bot.name) editName else null,
                        public = if (editPublic != bot.public) editPublic else null,
                        analytics = if (editAnalytics != bot.analytics) editAnalytics else null,
                        interactionsUrl = if (editInteractionsUrl.isNotBlank() && editInteractionsUrl != bot.interactionsUrl) editInteractionsUrl else null,
                        remove = if (editInteractionsUrl.isBlank() && bot.interactionsUrl != null) listOf("InteractionsURL") else null
                    )
                }
                onUpdated(result.bot)
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
        // Bot header — always visible
        ListItem(
            headlineContent = {
                Text(
                    bot.name ?: user?.username ?: "Bot",
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    bot.id ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.icn_smart_toy_24dp),
                    contentDescription = null,
                    tint = if (bot.public == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
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

        // Expanded details
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Token row
                bot.token?.let { token ->
                    Text(
                        stringResource(R.string.bot_management_token),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${token.take(12)}...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Bot Token", token))
                            Toast.makeText(context, R.string.bot_management_token_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.icn_content_copy_24dp),
                                contentDescription = "Copy"
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.bot_management_token_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Name editor
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.bot_management_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Public toggle
                ListItem(
                    headlineContent = { Text(stringResource(R.string.bot_management_public)) },
                    supportingContent = { Text(stringResource(R.string.bot_management_public_desc)) },
                    trailingContent = {
                        Switch(
                            checked = editPublic,
                            onCheckedChange = { editPublic = it }
                        )
                    },
                    modifier = Modifier.clickable { editPublic = !editPublic }
                )

                // Analytics toggle
                ListItem(
                    headlineContent = { Text(stringResource(R.string.bot_management_analytics)) },
                    supportingContent = { Text(stringResource(R.string.bot_management_analytics_desc)) },
                    trailingContent = {
                        Switch(
                            checked = editAnalytics,
                            onCheckedChange = { editAnalytics = it }
                        )
                    },
                    modifier = Modifier.clickable { editAnalytics = !editAnalytics }
                )

                // Interactions URL
                OutlinedTextField(
                    value = editInteractionsUrl,
                    onValueChange = { editInteractionsUrl = it },
                    label = { Text(stringResource(R.string.bot_management_interactions_url)) },
                    placeholder = { Text(stringResource(R.string.bot_management_interactions_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                if (editError != null) {
                    Text(
                        editError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Save changes
                    val hasChanges = editName != (bot.name ?: "") ||
                            editPublic != (bot.public ?: false) ||
                            editAnalytics != (bot.analytics ?: false) ||
                            editInteractionsUrl != (bot.interactionsUrl ?: "")

                    TextButton(
                        onClick = { isEditing = true },
                        enabled = hasChanges && !isEditing && editName.length in 2..32
                    ) {
                        if (isEditing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.server_settings_save))
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Delete button
                    TextButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Text(
                            stringResource(R.string.bot_management_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
