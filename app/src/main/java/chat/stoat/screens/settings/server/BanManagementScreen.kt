package chat.stoat.screens.settings.server

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.routes.server.ServerBan
import chat.stoat.api.routes.server.fetchBans
import chat.stoat.api.routes.server.unbanMember
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.schemas.User
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanManagementScreen(
    navController: NavController,
    serverId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val bans = remember { mutableStateListOf<Pair<ServerBan, User?>>() }
    var showUnbanConfirm by remember { mutableStateOf<Pair<ServerBan, User?>?>(null) }

    LaunchedEffect(serverId) {
        try {
            val response = fetchBans(serverId)
            val userMap = response.users.associateBy { it.id }
            response.bans.forEach { ban ->
                val user = ban.id?.user?.let { userMap[it] }
                bans.add(ban to user)
            }
        } catch (e: Exception) {
            loadError = e.message
        }
        isLoading = false
    }

    // Unban confirmation
    showUnbanConfirm?.let { (ban, user) ->
        var isUnbanning by remember { mutableStateOf(false) }
        val displayName = user?.displayName ?: user?.username ?: ban.id?.user ?: "Unknown"

        AlertDialog(
            onDismissRequest = { if (!isUnbanning) showUnbanConfirm = null },
            title = { Text(stringResource(R.string.ban_unban_title)) },
            text = { Text(stringResource(R.string.ban_unban_confirm, displayName)) },
            confirmButton = {
                Button(
                    onClick = {
                        isUnbanning = true
                        scope.launch {
                            try {
                                unbanMember(serverId, ban.id?.user ?: "")
                                bans.removeAll { it.first.id?.user == ban.id?.user }
                                showUnbanConfirm = null
                                Toast.makeText(context, context.getString(R.string.ban_unban_success), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            isUnbanning = false
                        }
                    },
                    enabled = !isUnbanning
                ) {
                    Text(stringResource(R.string.ban_unban_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnbanConfirm = null }) {
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
                        text = stringResource(R.string.server_settings_bans),
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
        Box(Modifier.padding(pv)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            loadError ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                bans.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.ban_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListHeader {
                                Text(stringResource(R.string.ban_list_header, bans.size))
                            }
                        }
                        items(bans, key = { it.first.id?.user ?: "" }) { (ban, user) ->
                            val displayName = user?.displayName ?: user?.username ?: ban.id?.user ?: "Unknown"
                            ListItem(
                                headlineContent = { Text(displayName) },
                                supportingContent = ban.reason?.let { { Text(it) } },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.icn_person_off_24dp),
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { showUnbanConfirm = ban to user }) {
                                        Icon(
                                            painter = painterResource(R.drawable.icn_door_open_24dp),
                                            contentDescription = stringResource(R.string.ban_unban_button)
                                        )
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
