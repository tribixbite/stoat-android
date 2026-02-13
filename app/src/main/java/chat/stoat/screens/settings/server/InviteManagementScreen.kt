package chat.stoat.screens.settings.server

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.STOAT_INVITES
import chat.stoat.api.routes.server.ServerInvite
import chat.stoat.api.routes.server.deleteInvite
import chat.stoat.api.routes.server.fetchServerInvites
import chat.stoat.composables.generic.ListHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteManagementScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboardManager = LocalClipboardManager.current

    val invites = remember { mutableStateListOf<ServerInvite>() }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<ServerInvite?>(null) }

    LaunchedEffect(serverId) {
        try {
            val fetched = fetchServerInvites(serverId)
            invites.addAll(fetched)
        } catch (e: Exception) {
            loadError = e.message
        }
        isLoading = false
    }

    // Delete confirmation
    showDeleteConfirm?.let { invite ->
        var isDeleting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = null },
            title = { Text(stringResource(R.string.invite_delete_title)) },
            text = { Text(stringResource(R.string.invite_delete_confirm, invite.id)) },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            val error = deleteInvite(invite.id)
                            if (error == null) {
                                invites.removeAll { it.id == invite.id }
                                showDeleteConfirm = null
                                Toast.makeText(context, context.getString(R.string.invite_deleted), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isDeleting = false
                        }
                    },
                    enabled = !isDeleting
                ) { Text(stringResource(R.string.invite_delete_button)) }
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
                        text = stringResource(R.string.invite_management_title),
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
                invites.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.invite_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListHeader {
                                Text(stringResource(R.string.invite_list_header, invites.size))
                            }
                        }
                        items(invites, key = { it.id }) { invite ->
                            val creatorName = invite.creator?.let {
                                StoatAPI.userCache[it]?.displayName
                                    ?: StoatAPI.userCache[it]?.username
                                    ?: it
                            }
                            val channelName = invite.channel?.let {
                                StoatAPI.channelCache[it]?.name ?: it
                            }

                            ListItem(
                                headlineContent = {
                                    Text("$STOAT_INVITES/${invite.id}")
                                },
                                supportingContent = {
                                    val details = buildString {
                                        channelName?.let { append("#$it") }
                                        creatorName?.let {
                                            if (isNotEmpty()) append(" · ")
                                            append(context.getString(R.string.invite_created_by, it))
                                        }
                                    }
                                    if (details.isNotEmpty()) {
                                        Text(details)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { showDeleteConfirm = invite }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_delete_24dp),
                                            contentDescription = stringResource(R.string.invite_delete_title),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    // Copy invite link to clipboard
                                    clipboardManager.setText(AnnotatedString("$STOAT_INVITES/${invite.id}"))
                                    Toast.makeText(context, context.getString(R.string.invite_link_copied), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
