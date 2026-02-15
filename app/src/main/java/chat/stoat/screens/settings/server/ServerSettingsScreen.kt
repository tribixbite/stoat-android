package chat.stoat.screens.settings.server

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.activities.StoatTweenFloat
import chat.stoat.api.STOAT_FILES
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.Roles
import chat.stoat.api.internals.has
import chat.stoat.api.routes.microservices.autumn.uploadToAutumn
import chat.stoat.api.routes.server.editServer
import chat.stoat.composables.generic.InlineMediaPicker
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.schemas.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    @ApplicationContext val context: Context
) : ViewModel() {
    var initialServer by mutableStateOf<Server?>(null)

    var serverName by mutableStateOf("")
    var serverDescription by mutableStateOf("")

    var iconModel by mutableStateOf<Any?>(null)
    var iconIsUploading by mutableStateOf(false)
    var iconUploadProgress by mutableFloatStateOf(0f)

    var bannerModel by mutableStateOf<Any?>(null)
    var bannerIsUploading by mutableStateOf(false)
    var bannerUploadProgress by mutableFloatStateOf(0f)

    var uploadError by mutableStateOf<String?>(null)
    var updateError by mutableStateOf<String?>(null)

    fun populateWithServer(serverId: String) {
        val server = StoatAPI.serverCache[serverId]
        initialServer = server
        server?.let {
            serverName = it.name ?: ""
            serverDescription = it.description ?: ""
            iconModel = it.icon?.let { icon -> "$STOAT_FILES/icons/${icon.id}" }
            bannerModel = it.banner?.let { banner -> "$STOAT_FILES/banners/${banner.id}" }
        }
    }

    private fun unsetIcon() {
        iconIsUploading = true
        iconUploadProgress = 0f
        uploadError = null

        initialServer?.id?.let { serverId ->
            viewModelScope.launch {
                try {
                    editServer(serverId, remove = listOf("Icon"))
                    iconModel = null
                } catch (e: Exception) {
                    updateError = e.message
                }
                iconIsUploading = false
            }
        } ?: run { iconIsUploading = false }
    }

    fun pickIcon(newModel: Any?) {
        iconModel = newModel
        uploadError = null
        iconUploadProgress = 0f

        val uri = when (newModel) {
            is Uri -> newModel
            is String -> Uri.parse(newModel)
            else -> null
        } ?: run {
            unsetIcon()
            return
        }

        val mFile = File(context.cacheDir, uri.lastPathSegment ?: "icon")
        mFile.outputStream().use { output ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(output)
            }
        }
        val mime = context.contentResolver.getType(uri)

        viewModelScope.launch {
            iconIsUploading = true
            try {
                val id = uploadToAutumn(
                    mFile,
                    uri.lastPathSegment ?: "icon",
                    "icons",
                    ContentType.parse(mime ?: "image/*"),
                    onProgress = { soFar, outOf ->
                        iconUploadProgress = soFar.toFloat() / outOf.toFloat()
                    }
                )
                editServer(initialServer?.id ?: "", icon = id)
                iconIsUploading = false
            } catch (e: Exception) {
                uploadError = e.message
                iconUploadProgress = 0f
                iconIsUploading = false
            }
        }
    }

    private fun unsetBanner() {
        bannerIsUploading = true
        bannerUploadProgress = 0f
        uploadError = null

        initialServer?.id?.let { serverId ->
            viewModelScope.launch {
                try {
                    editServer(serverId, remove = listOf("Banner"))
                    bannerModel = null
                } catch (e: Exception) {
                    updateError = e.message
                }
                bannerIsUploading = false
            }
        } ?: run { bannerIsUploading = false }
    }

    fun pickBanner(newModel: Any?) {
        bannerModel = newModel
        uploadError = null
        bannerUploadProgress = 0f

        val uri = when (newModel) {
            is Uri -> newModel
            is String -> Uri.parse(newModel)
            else -> null
        } ?: run {
            unsetBanner()
            return
        }

        val mFile = File(context.cacheDir, uri.lastPathSegment ?: "banner")
        mFile.outputStream().use { output ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(output)
            }
        }
        val mime = context.contentResolver.getType(uri)

        viewModelScope.launch {
            bannerIsUploading = true
            try {
                val id = uploadToAutumn(
                    mFile,
                    uri.lastPathSegment ?: "banner",
                    "banners",
                    ContentType.parse(mime ?: "image/*"),
                    onProgress = { soFar, outOf ->
                        bannerUploadProgress = soFar.toFloat() / outOf.toFloat()
                    }
                )
                editServer(initialServer?.id ?: "", banner = id)
                bannerIsUploading = false
            } catch (e: Exception) {
                uploadError = e.message
                bannerUploadProgress = 0f
                bannerIsUploading = false
            }
        }
    }

    fun updateServer() {
        updateError = null
        viewModelScope.launch {
            try {
                editServer(
                    initialServer?.id ?: "",
                    name = if (serverName != initialServer?.name) serverName else null,
                    description = if (serverDescription != (initialServer?.description ?: "")) serverDescription else null
                )
                initialServer = initialServer?.copy(
                    name = serverName,
                    description = serverDescription
                )
            } catch (e: Exception) {
                updateError = e.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    navController: NavController,
    serverId: String,
    viewModel: ServerSettingsViewModel = hiltViewModel()
) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(serverId) {
        viewModel.populateWithServer(serverId)
    }

    // Check if user has ManageServer permission
    val selfMember = StoatAPI.members.getMember(serverId, StoatAPI.selfId ?: "")
    val permissions = server?.let { s ->
        selfMember?.let { m -> Roles.permissionFor(s, m) }
    } ?: 0L
    val canManage = server?.owner == StoatAPI.selfId || permissions has PermissionBit.ManageServer

    val serverInfoUpdated by derivedStateOf {
        server?.let { s ->
            (s.name ?: "") != viewModel.serverName ||
                    (s.description ?: "") != viewModel.serverDescription
        } ?: false
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.server_settings_title),
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
        },
        floatingActionButton = {
            if (canManage) {
                AnimatedVisibility(
                    visible = serverInfoUpdated,
                    enter = scaleIn(animationSpec = StoatTweenFloat),
                    exit = scaleOut(animationSpec = StoatTweenFloat)
                ) {
                    FloatingActionButton(onClick = { viewModel.updateServer() }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_check_24dp),
                            contentDescription = stringResource(R.string.server_settings_save)
                        )
                    }
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
                ) {
                    // Server info section
                    if (canManage) {
                        ListHeader {
                            Text(stringResource(R.string.server_settings_info))
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            InlineMediaPicker(
                                currentModel = viewModel.iconModel,
                                onPick = { viewModel.pickIcon(it) },
                                circular = true,
                                mimeType = "image/*",
                                canRemove = true,
                                enabled = !viewModel.iconIsUploading,
                                onRemove = { viewModel.pickIcon(null) },
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        AnimatedVisibility(visible = viewModel.iconIsUploading) {
                            LinearProgressIndicator(
                                progress = { viewModel.iconUploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        // Banner picker — wider aspect ratio for banners
                        ListHeader {
                            Text(stringResource(R.string.server_settings_banner))
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            InlineMediaPicker(
                                currentModel = viewModel.bannerModel,
                                onPick = { viewModel.pickBanner(it) },
                                circular = false,
                                mimeType = "image/*",
                                canRemove = true,
                                enabled = !viewModel.bannerIsUploading,
                                onRemove = { viewModel.pickBanner(null) },
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        AnimatedVisibility(visible = viewModel.bannerIsUploading) {
                            LinearProgressIndicator(
                                progress = { viewModel.bannerUploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        AnimatedVisibility(visible = viewModel.uploadError != null) {
                            Text(
                                viewModel.uploadError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        AnimatedVisibility(visible = viewModel.updateError != null) {
                            Text(
                                viewModel.updateError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            )
                        }

                        TextField(
                            label = { Text(stringResource(R.string.server_settings_name)) },
                            value = viewModel.serverName,
                            onValueChange = { viewModel.serverName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            singleLine = true
                        )

                        TextField(
                            label = { Text(stringResource(R.string.server_settings_description)) },
                            value = viewModel.serverDescription,
                            onValueChange = { viewModel.serverDescription = it },
                            modifier = Modifier
                                .animateContentSize()
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            singleLine = false,
                            minLines = 3
                        )
                    }

                    // Management section — navigate to sub-screens
                    ListHeader {
                        Text(stringResource(R.string.server_settings_management))
                    }

                    // Default permissions (owner or ManagePermissions)
                    if (canManage || permissions has PermissionBit.ManagePermissions) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_default_permissions)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_lock_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/permissions/default")
                            }
                        )
                    }

                    if (canManage || permissions has PermissionBit.ManageRole) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_roles)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_badge_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/roles")
                            }
                        )
                    }

                    if (canManage || permissions has PermissionBit.BanMembers) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_bans)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_gavel_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/bans")
                            }
                        )
                    }

                    // System messages — configure join/leave/kick/ban channels
                    if (canManage) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_system_messages)) },
                            supportingContent = { Text(stringResource(R.string.server_settings_system_messages_desc)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_notification_settings_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/system-messages")
                            }
                        )
                    }

                    if (canManage || permissions has PermissionBit.ManageChannel) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_create_channel)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_add_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/create-channel")
                            }
                        )

                        // Discord channel import wizard
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_import_discord)) },
                            supportingContent = { Text(stringResource(R.string.server_settings_import_discord_desc)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_download_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/import-discord")
                            }
                        )

                        // Discord↔Stoat message bridge settings
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.server_settings_bridge)) },
                            supportingContent = { Text(stringResource(R.string.server_settings_bridge_desc)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_link_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/bridge-settings")
                            }
                        )
                    }

                    // Emoji management — requires ManageCustomisation
                    if (canManage || permissions has PermissionBit.ManageCustomisation) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.emoji_management_title)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_emoji_objects_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/emojis")
                            }
                        )
                    }

                    // Invite management — requires ManageServer
                    if (canManage) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.invite_management_title)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.icn_link_24dp),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate("settings/server/$serverId/invites")
                            }
                        )
                    }
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
