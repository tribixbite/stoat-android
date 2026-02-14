package chat.stoat.screens.settings.server

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.discord.BridgeLinkInfo
import chat.stoat.api.routes.discord.DiscordChannelInfo
import chat.stoat.api.routes.discord.DiscordGuildPreview
import chat.stoat.api.routes.discord.createBridgeLink
import chat.stoat.api.routes.discord.deleteBridgeLink
import chat.stoat.api.routes.discord.fetchBotGuilds
import chat.stoat.api.routes.discord.fetchGuildChannels
import chat.stoat.api.routes.discord.fetchGuildLinks
import chat.stoat.composables.generic.ListHeader
import kotlinx.coroutines.launch

// Reuse bot URL defaults from import screen
private const val DEFAULT_BOT_API_URL = "http://localhost:3210"

/**
 * Bridge Settings screen — manage message bridge links between Discord and Stoat channels.
 * Users can view, create, and delete channel bridges via the stoatcord-bot API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeSettingsScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // --- State ---
    var botApiUrl by remember { mutableStateOf(DEFAULT_BOT_API_URL) }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Guild selection
    var isLoading by remember { mutableStateOf(false) }
    val guilds = remember { mutableStateListOf<DiscordGuildPreview>() }
    var selectedGuild by remember { mutableStateOf<DiscordGuildPreview?>(null) }

    // Active links for selected guild
    val activeLinks = remember { mutableStateListOf<BridgeLinkInfo>() }
    var isFetchingLinks by remember { mutableStateOf(false) }

    // Discord channels for linking
    val discordChannels = remember { mutableStateListOf<DiscordChannelInfo>() }

    // New link creation
    var showLinkForm by remember { mutableStateOf(false) }
    var selectedDiscordChannel by remember { mutableStateOf<DiscordChannelInfo?>(null) }
    var selectedStoatChannelId by remember { mutableStateOf("") }
    var isCreatingLink by remember { mutableStateOf(false) }

    // Get stoat channels for this server from the API cache
    val stoatChannels = StoatAPI.channelCache.values
        .filter { it.server == serverId }
        .sortedBy { it.name }

    /** Refresh links for the selected guild */
    fun refreshLinks() {
        val guild = selectedGuild ?: return
        scope.launch {
            isFetchingLinks = true
            try {
                activeLinks.clear()
                activeLinks.addAll(fetchGuildLinks(botApiUrl, apiKey, guild.id))
            } catch (e: Exception) {
                error = "Failed to fetch links: ${e.message}"
            }
            isFetchingLinks = false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.bridge_settings_title),
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
            // --- Bot Connection ---
            item {
                ListHeader { Text(stringResource(R.string.bridge_settings_connection)) }
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

            // Fetch guilds
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            guilds.clear()
                            selectedGuild = null
                            activeLinks.clear()
                            discordChannels.clear()
                            try {
                                guilds.addAll(fetchBotGuilds(botApiUrl, apiKey))
                                if (guilds.isEmpty()) {
                                    error = context.getString(R.string.discord_import_no_servers)
                                }
                            } catch (e: Exception) {
                                error = "${context.getString(R.string.discord_import_error_api)}: ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    enabled = botApiUrl.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isLoading) {
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

            // --- Guild selection ---
            if (guilds.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListHeader { Text(stringResource(R.string.discord_import_select_server)) }
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
                            selectedGuild = guild
                            // Fetch channels and existing links
                            scope.launch {
                                isFetchingLinks = true
                                error = null
                                activeLinks.clear()
                                discordChannels.clear()
                                try {
                                    val result = fetchGuildChannels(botApiUrl, apiKey, guild.id)
                                    discordChannels.addAll(
                                        result.channels.filter { it.type == "text" || it.type == "announcement" }
                                    )
                                    activeLinks.addAll(fetchGuildLinks(botApiUrl, apiKey, guild.id))
                                } catch (e: Exception) {
                                    error = "Failed to fetch guild data: ${e.message}"
                                }
                                isFetchingLinks = false
                            }
                        },
                        tonalElevation = if (guild.id == selectedGuild?.id) 4.dp else 0.dp
                    )
                }
            }

            // Loading
            if (isFetchingLinks) {
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

            // --- Active Bridge Links ---
            if (selectedGuild != null && !isFetchingLinks) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListHeader { Text(stringResource(R.string.bridge_settings_active_links)) }
                }

                if (activeLinks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.bridge_settings_no_links),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                items(activeLinks.toList(), key = { it.discordChannelId }) { link ->
                    val stoatChannel = StoatAPI.channelCache[link.stoatChannelId]
                    ListItem(
                        headlineContent = {
                            Text("#${link.discordChannelName ?: link.discordChannelId}")
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = "Stoat: #${stoatChannel?.name ?: link.stoatChannelId}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (link.hasWebhook) {
                                    Text(
                                        text = stringResource(R.string.bridge_settings_bidirectional),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.icn_link_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        deleteBridgeLink(botApiUrl, apiKey, link.discordChannelId)
                                        activeLinks.removeAll { it.discordChannelId == link.discordChannelId }
                                        Toast.makeText(context, "Bridge removed", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        error = "Failed to remove link: ${e.message}"
                                    }
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.icn_delete_24dp),
                                    contentDescription = stringResource(R.string.bridge_settings_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }

                // --- Add New Link ---
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListHeader { Text(stringResource(R.string.bridge_settings_add_link)) }
                }

                item {
                    AnimatedVisibility(visible = !showLinkForm) {
                        OutlinedButton(
                            onClick = { showLinkForm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.icn_add_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.bridge_settings_new_bridge))
                        }
                    }
                }

                // Link creation form
                if (showLinkForm) {
                    // Discord channel dropdown
                    item {
                        var dcExpanded by remember { mutableStateOf(false) }
                        // Filter out already-linked Discord channels
                        val linkedDiscordIds = activeLinks.map { it.discordChannelId }.toSet()
                        val availableDiscordChannels = discordChannels.filter { it.id !in linkedDiscordIds }

                        ExposedDropdownMenuBox(
                            expanded = dcExpanded,
                            onExpandedChange = { dcExpanded = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedDiscordChannel?.let { "#${it.name}" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.bridge_settings_discord_channel)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dcExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dcExpanded,
                                onDismissRequest = { dcExpanded = false }
                            ) {
                                availableDiscordChannels.forEach { ch ->
                                    DropdownMenuItem(
                                        text = { Text("#${ch.name}") },
                                        onClick = {
                                            selectedDiscordChannel = ch
                                            dcExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Stoat channel dropdown
                    item {
                        var scExpanded by remember { mutableStateOf(false) }
                        val linkedStoatIds = activeLinks.map { it.stoatChannelId }.toSet()
                        val availableStoatChannels = stoatChannels.filter { (it.id ?: "") !in linkedStoatIds }
                        val selectedStoatChannel = stoatChannels.find { it.id == selectedStoatChannelId }

                        ExposedDropdownMenuBox(
                            expanded = scExpanded,
                            onExpandedChange = { scExpanded = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedStoatChannel?.let { "#${it.name}" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.bridge_settings_stoat_channel)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = scExpanded,
                                onDismissRequest = { scExpanded = false }
                            ) {
                                availableStoatChannels.forEach { ch ->
                                    DropdownMenuItem(
                                        text = { Text("#${ch.name ?: ch.id}") },
                                        onClick = {
                                            selectedStoatChannelId = ch.id ?: ""
                                            scExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Create button
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showLinkForm = false
                                    selectedDiscordChannel = null
                                    selectedStoatChannelId = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }

                            Button(
                                onClick = {
                                    val dc = selectedDiscordChannel ?: return@Button
                                    if (selectedStoatChannelId.isBlank()) return@Button

                                    scope.launch {
                                        isCreatingLink = true
                                        error = null
                                        try {
                                            createBridgeLink(
                                                botApiUrl, apiKey,
                                                dc.id, selectedStoatChannelId
                                            )
                                            // Refresh links
                                            showLinkForm = false
                                            selectedDiscordChannel = null
                                            selectedStoatChannelId = ""
                                            refreshLinks()
                                            Toast.makeText(
                                                context, "Bridge created", Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            error = "Failed to create link: ${e.message}"
                                        }
                                        isCreatingLink = false
                                    }
                                },
                                enabled = selectedDiscordChannel != null
                                        && selectedStoatChannelId.isNotBlank()
                                        && !isCreatingLink,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isCreatingLink) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.bridge_settings_create_link))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
