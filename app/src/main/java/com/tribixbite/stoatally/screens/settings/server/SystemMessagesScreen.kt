package com.tribixbite.stoatally.screens.settings.server

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.routes.server.editServer
import com.tribixbite.stoatally.composables.generic.ListHeader
import com.tribixbite.stoatally.core.model.schemas.ChannelType
import kotlinx.coroutines.launch

/**
 * Screen to configure system message channels for a server.
 * Allows selecting which text channel receives join/leave/kick/ban notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMessagesScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val server = StoatAPI.serverCache[serverId]

    // Text channels available for selection
    val textChannels = remember(serverId) {
        server?.channels?.mapNotNull { channelId ->
            StoatAPI.channelCache[channelId]?.let { ch ->
                if (ch.channelType == ChannelType.TextChannel) channelId to (ch.name ?: channelId)
                else null
            }
        }?.sortedBy { it.second } ?: emptyList()
    }

    // Current system message channel selections
    var userJoined by remember { mutableStateOf(server?.systemMessages?.userJoined) }
    var userLeft by remember { mutableStateOf(server?.systemMessages?.userLeft) }
    var userKicked by remember { mutableStateOf(server?.systemMessages?.userKicked) }
    var userBanned by remember { mutableStateOf(server?.systemMessages?.userBanned) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        server?.systemMessages?.let { sm ->
            userJoined = sm.userJoined
            userLeft = sm.userLeft
            userKicked = sm.userKicked
            userBanned = sm.userBanned
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        stringResource(R.string.server_settings_system_messages),
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
                            // Build system_messages map — null values clear the setting
                            val smMap = mapOf(
                                "user_joined" to userJoined,
                                "user_left" to userLeft,
                                "user_kicked" to userKicked,
                                "user_banned" to userBanned
                            )
                            editServer(serverId, systemMessages = smMap)
                            Toast.makeText(
                                context,
                                context.getString(R.string.system_messages_saved),
                                Toast.LENGTH_SHORT
                            ).show()
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
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            server?.let {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    ListHeader {
                        Text(stringResource(R.string.server_settings_system_messages_desc))
                    }

                    ChannelDropdown(
                        label = stringResource(R.string.system_messages_user_joined),
                        selectedChannelId = userJoined,
                        channels = textChannels,
                        onSelect = { userJoined = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    ChannelDropdown(
                        label = stringResource(R.string.system_messages_user_left),
                        selectedChannelId = userLeft,
                        channels = textChannels,
                        onSelect = { userLeft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    ChannelDropdown(
                        label = stringResource(R.string.system_messages_user_kicked),
                        selectedChannelId = userKicked,
                        channels = textChannels,
                        onSelect = { userKicked = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    ChannelDropdown(
                        label = stringResource(R.string.system_messages_user_banned),
                        selectedChannelId = userBanned,
                        channels = textChannels,
                        onSelect = { userBanned = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/**
 * Dropdown selector for choosing a text channel. Includes a "None" option to clear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDropdown(
    label: String,
    selectedChannelId: String?,
    channels: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = channels.find { it.first == selectedChannelId }?.second

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedName ?: stringResource(R.string.system_messages_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "None" option to clear the channel
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.system_messages_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            channels.forEach { (channelId, channelName) ->
                DropdownMenuItem(
                    text = { Text("# $channelName") },
                    onClick = {
                        onSelect(channelId)
                        expanded = false
                    }
                )
            }
        }
    }
}
