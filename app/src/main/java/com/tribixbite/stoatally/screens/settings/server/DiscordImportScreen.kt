package com.tribixbite.stoatally.screens.settings.server

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tribixbite.stoatally.api.routes.discord.DiscordChannelInfo
import com.tribixbite.stoatally.api.routes.discord.DiscordGuildPreview
import com.tribixbite.stoatally.api.routes.discord.fetchBotGuilds
import com.tribixbite.stoatally.api.routes.discord.fetchGuildChannels
import com.tribixbite.stoatally.api.routes.server.createChannel
import com.tribixbite.stoatally.composables.generic.ListHeader
import com.tribixbite.stoatally.push.PushManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Discord bot OAuth2 add URL for stoatcord-bot
// Permissions: View Channels, Send Messages, Read Message History, Manage Webhooks
private const val BOT_ADD_URL =
    "https://discord.com/oauth2/authorize?client_id=1472115292925857865&permissions=536939520&scope=bot"

/**
 * Discord Import Wizard screen.
 * Lets users import channel structure from a Discord server via the stoatcord-bot.
 *
 * Flow: Add bot to Discord → Fetch servers → Select channels → Import
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordImportScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // --- State ---
    var botApiUrl by remember { mutableStateOf(PushManager.DEFAULT_BOT_URL) }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Step 1: Server list
    var isFetchingServers by remember { mutableStateOf(false) }
    val guilds = remember { mutableStateListOf<DiscordGuildPreview>() }
    var selectedGuildId by remember { mutableStateOf<String?>(null) }

    // Step 2: Channel list
    var isFetchingChannels by remember { mutableStateOf(false) }
    val channels = remember { mutableStateListOf<SelectableChannel>() }

    // Step 3: Import progress
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }
    var importSuccessCount by remember { mutableIntStateOf(0) }
    var importErrorCount by remember { mutableIntStateOf(0) }
    var importDone by remember { mutableStateOf(false) }

    val selectedCount = channels.count { it.selected }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.discord_import_title),
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
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .imePadding()
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // --- Section: Add Bot & Configure ---
            item {
                ListHeader {
                    Text("1. Connect to Stoatcord Bot")
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BOT_ADD_URL))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.icn_link_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.discord_import_add_bot))
                }
            }

            item {
                Text(
                    text = stringResource(R.string.discord_import_add_bot_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = botApiUrl,
                    onValueChange = { botApiUrl = it },
                    label = { Text(stringResource(R.string.discord_import_api_url)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Error display
            if (error != null) {
                item {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Fetch servers button
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isFetchingServers = true
                            error = null
                            guilds.clear()
                            selectedGuildId = null
                            channels.clear()
                            try {
                                val result = fetchBotGuilds(botApiUrl, apiKey)
                                guilds.addAll(result)
                                if (result.isEmpty()) {
                                    error = context.getString(R.string.discord_import_no_servers)
                                }
                            } catch (e: Exception) {
                                error = "${context.getString(R.string.discord_import_error_api)}: ${e.message}"
                            }
                            isFetchingServers = false
                        }
                    },
                    enabled = botApiUrl.isNotBlank() && !isFetchingServers && !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isFetchingServers) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.discord_import_fetch_servers))
                }
            }

            // --- Section: Server List ---
            if (guilds.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListHeader {
                        Text(stringResource(R.string.discord_import_select_server))
                    }
                }

                items(guilds, key = { it.id }) { guild ->
                    ListItem(
                        headlineContent = { Text(guild.name) },
                        supportingContent = {
                            Text("${guild.memberCount} members, ${guild.channelCount} channels")
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.icn_tag_24dp),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable {
                            selectedGuildId = guild.id
                            // Fetch channels for this guild
                            scope.launch {
                                isFetchingChannels = true
                                error = null
                                channels.clear()
                                try {
                                    val result = fetchGuildChannels(botApiUrl, apiKey, guild.id)
                                    channels.addAll(
                                        result.channels.map { ch ->
                                            SelectableChannel(
                                                id = ch.id,
                                                name = ch.name,
                                                type = ch.type,
                                                category = ch.category,
                                                selected = true
                                            )
                                        }
                                    )
                                } catch (e: Exception) {
                                    error = "Failed to fetch channels: ${e.message}"
                                }
                                isFetchingChannels = false
                            }
                        },
                        tonalElevation = if (guild.id == selectedGuildId) 4.dp else 0.dp
                    )
                }
            }

            // Loading indicator for channels
            if (isFetchingChannels) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // --- Section: Channel Checklist ---
            if (channels.isNotEmpty() && !isFetchingChannels) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListHeader {
                        Text(stringResource(R.string.discord_import_channels_header))
                    }
                }

                // Select all / deselect all
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = {
                            for (i in channels.indices) {
                                channels[i] = channels[i].copy(selected = true)
                            }
                        }) {
                            Text(stringResource(R.string.discord_import_select_all))
                        }
                        TextButton(onClick = {
                            for (i in channels.indices) {
                                channels[i] = channels[i].copy(selected = false)
                            }
                        }) {
                            Text(stringResource(R.string.discord_import_deselect_all))
                        }
                    }
                }

                // Channel list with checkboxes
                items(channels.size) { index ->
                    val ch = channels[index]
                    ListItem(
                        headlineContent = { Text(ch.name) },
                        supportingContent = ch.category?.let {
                            { Text(it, style = MaterialTheme.typography.bodySmall) }
                        },
                        leadingContent = {
                            Checkbox(
                                checked = ch.selected,
                                onCheckedChange = { checked ->
                                    channels[index] = ch.copy(selected = checked)
                                }
                            )
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(
                                    when (ch.type) {
                                        "voice", "stage" -> R.drawable.icn_volume_up_24dp
                                        else -> R.drawable.icn_tag_24dp
                                    }
                                ),
                                contentDescription = ch.type,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                // --- Import button + progress ---
                item {
                    Spacer(Modifier.height(8.dp))
                }

                // Progress indicator during import
                if (isImporting) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = {
                                    if (importTotal > 0) importProgress.toFloat() / importTotal.toFloat()
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = context.getString(
                                    R.string.discord_import_progress,
                                    importProgress,
                                    importTotal
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Import result
                if (importDone) {
                    item {
                        Text(
                            text = context.getString(
                                R.string.discord_import_complete,
                                importSuccessCount,
                                importErrorCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (importErrorCount == 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Import button
                item {
                    Button(
                        onClick = {
                            val selected = channels.filter { it.selected }
                            if (selected.isEmpty()) return@Button

                            scope.launch {
                                isImporting = true
                                importDone = false
                                importProgress = 0
                                importTotal = selected.size
                                importSuccessCount = 0
                                importErrorCount = 0
                                error = null

                                for ((index, ch) in selected.withIndex()) {
                                    importProgress = index + 1
                                    try {
                                        val stoatType = when (ch.type) {
                                            "voice", "stage" -> "Voice"
                                            else -> "Text"
                                        }
                                        createChannel(
                                            serverId = serverId,
                                            name = ch.name,
                                            type = stoatType
                                        )
                                        importSuccessCount++
                                    } catch (e: Exception) {
                                        importErrorCount++
                                    }
                                    // Rate limit: server bucket is 5 req/10s
                                    if (index < selected.lastIndex) {
                                        delay(2500)
                                    }
                                }

                                isImporting = false
                                importDone = true

                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.discord_import_complete,
                                        importSuccessCount,
                                        importErrorCount
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = selectedCount > 0 && !isImporting && !importDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.discord_import_start, selectedCount)
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/** Channel entry with selection state for the checklist */
data class SelectableChannel(
    val id: String,
    val name: String,
    val type: String,
    val category: String?,
    val selected: Boolean = true
)
