package chat.stoat.screens.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.push.subscribePush
import chat.stoat.api.settings.SyncedSettings
import chat.stoat.composables.generic.ListHeader
import chat.stoat.persistence.KVStorage
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val kvStorage: KVStorage
) : ViewModel() {
    var fcmRegistered by mutableStateOf(false)
        private set
    var isRetrying by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    /** True when google-services.json is placeholder (Firebase unusable) */
    var firebaseUnconfigured by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // Detect placeholder google-services.json
            try {
                val options = com.google.firebase.FirebaseApp.getInstance().options
                firebaseUnconfigured = options.projectId == "stoat-local-dev"
                        || options.gcmSenderId == "000000000000"
            } catch (_: Exception) {
                firebaseUnconfigured = true
            }

            if (firebaseUnconfigured) {
                lastError = "Replace app/google-services.json with real Firebase config"
                return@launch
            }

            val failed = kvStorage.getBoolean("pushRegistrationFailed") == true
            val hasToken = kvStorage.get("fcmToken") != null
            fcmRegistered = hasToken && !failed
        }
    }

    fun retryFcmRegistration() {
        if (firebaseUnconfigured) {
            lastError = "Replace app/google-services.json with real Firebase config"
            return
        }

        isRetrying = true
        lastError = null
        try {
            FirebaseMessaging.getInstance().token
        } catch (e: Exception) {
            lastError = "Firebase not configured: ${e.message}"
            isRetrying = false
            return
        }.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("NotificationSettings", "FCM token fetch failed", task.exception)
                task.exception?.let { Sentry.captureException(it) }
                val cause = task.exception?.message ?: "unknown"
                lastError = "FCM token failed: $cause"
                isRetrying = false
                return@addOnCompleteListener
            }

            val token = task.result
            viewModelScope.launch {
                kvStorage.set("fcmToken", token)
                val error = subscribePush(auth = token)
                kvStorage.set("pushRegistrationFailed", error != null)
                fcmRegistered = error == null
                lastError = error
                isRetrying = false
                if (error == null) {
                    Log.d("NotificationSettings", "Push registration succeeded")
                }
            }
        }
    }

    fun resetNotificationSettings() {
        viewModelScope.launch {
            SyncedSettings.resetNotifications()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permission status when returning from system settings
    var notificationsEnabled by mutableStateOf(
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    )

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mutedServers = SyncedSettings.notifications.server.filter { it.value == "muted" }
    val mutedChannels = SyncedSettings.notifications.channel.filter { it.value == "muted" }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.settings_notifications),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            // Permission status
            ListHeader {
                Text(stringResource(R.string.settings_notifications_permission_status))
            }

            ListItem(
                headlineContent = {
                    Text(
                        if (notificationsEnabled) {
                            stringResource(R.string.settings_notifications_permission_granted)
                        } else {
                            stringResource(R.string.settings_notifications_permission_denied)
                        }
                    )
                },
                supportingContent = {
                    if (!notificationsEnabled) {
                        Text(stringResource(R.string.settings_notifications_open_system_settings))
                    }
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(
                            painter = painterResource(R.drawable.icn_notification_settings_24dp),
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )

            // FCM registration status
            ListHeader {
                Text(stringResource(R.string.settings_notifications_fcm_status))
            }

            ListItem(
                headlineContent = {
                    Text(
                        if (viewModel.firebaseUnconfigured) {
                            stringResource(R.string.settings_notifications_fcm_unconfigured)
                        } else if (!notificationsEnabled) {
                            stringResource(R.string.settings_notifications_permission_denied)
                        } else if (viewModel.fcmRegistered) {
                            stringResource(R.string.settings_notifications_fcm_registered)
                        } else {
                            stringResource(R.string.settings_notifications_fcm_not_registered)
                        }
                    )
                },
                supportingContent = {
                    if (viewModel.firebaseUnconfigured) {
                        Text(
                            text = viewModel.lastError ?: stringResource(R.string.settings_notifications_fcm_unconfigured_hint),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!notificationsEnabled) {
                        Text(stringResource(R.string.settings_notifications_grant_first))
                    } else if (viewModel.lastError != null) {
                        Text(
                            text = viewModel.lastError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                trailingContent = {
                    if (!viewModel.firebaseUnconfigured) {
                        TextButton(
                            onClick = { viewModel.retryFcmRegistration() },
                            enabled = !viewModel.isRetrying && notificationsEnabled
                        ) {
                            Text(
                                if (viewModel.isRetrying) {
                                    stringResource(R.string.search_messages_loading)
                                } else {
                                    stringResource(R.string.settings_notifications_fcm_retry)
                                }
                            )
                        }
                    }
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(
                            painter = painterResource(R.drawable.icn_chat_24dp),
                            contentDescription = null,
                        )
                    }
                }
            )

            // Muted servers summary
            ListHeader {
                Text(stringResource(R.string.settings_notifications_muted_servers))
            }

            if (mutedServers.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_notifications_no_muted),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                mutedServers.keys.forEach { serverId ->
                    val serverName = StoatAPI.serverCache[serverId]?.name ?: serverId
                    ListItem(
                        headlineContent = { Text(serverName) },
                        supportingContent = if (serverName != serverId) {
                            { Text(serverId, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else null,
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    val updated = SyncedSettings.notifications.copy(
                                        server = SyncedSettings.notifications.server - serverId
                                    )
                                    SyncedSettings.updateNotifications(updated)
                                }
                            }) {
                                Text(stringResource(R.string.channel_context_sheet_actions_unmute))
                            }
                        }
                    )
                }
            }

            // Muted channels summary
            ListHeader {
                Text(stringResource(R.string.settings_notifications_muted_channels))
            }

            if (mutedChannels.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_notifications_no_muted),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                mutedChannels.keys.forEach { channelId ->
                    val channel = StoatAPI.channelCache[channelId]
                    val channelName = channel?.name ?: channelId
                    // Show parent server name if available
                    val parentServer = channel?.server?.let { StoatAPI.serverCache[it] }
                    ListItem(
                        headlineContent = { Text(channelName) },
                        supportingContent = {
                            val subtitle = buildString {
                                parentServer?.name?.let { append(it) }
                                if (channelName != channelId) {
                                    if (isNotEmpty()) append(" · ")
                                    append(channelId)
                                }
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    val updated = SyncedSettings.notifications.copy(
                                        channel = SyncedSettings.notifications.channel - channelId
                                    )
                                    SyncedSettings.updateNotifications(updated)
                                }
                            }) {
                                Text(stringResource(R.string.channel_context_sheet_actions_unmute))
                            }
                        }
                    )
                }
            }

            // Debug / testing
            ListHeader {
                Text("Debug")
            }

            // Test local notification — bypasses FCM to verify display path
            ListItem(
                headlineContent = { Text("Send Test Notification") },
                supportingContent = { Text("Creates a local notification to verify display works (bypasses FCM)") },
                leadingContent = {
                    SettingsIcon {
                        Icon(
                            painter = painterResource(R.drawable.icn_notification_settings_24dp),
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.clickable {
                    val testAuthor = Person.Builder()
                        .setName("Notification Test")
                        .setKey("test-user")
                        .build()
                    val builder = NotificationCompat.Builder(context, CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES)
                        .setSmallIcon(R.drawable.icn_chat_24dp)
                        .setContentTitle("Test Notification")
                        .setContentText("If you see this, local notifications work. FCM path may be the issue.")
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setStyle(
                            NotificationCompat.MessagingStyle(testAuthor)
                                .setConversationTitle("#test-channel")
                                .addMessage(
                                    "If you see this, local notifications work. FCM path may be the issue.",
                                    System.currentTimeMillis(),
                                    testAuthor
                                )
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)

                    if (ActivityCompat.checkSelfPermission(
                            context, android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        NotificationManagerCompat.from(context)
                            .notify("test-notification", 999, builder.build())
                        Log.d("NotificationTest", "Test notification sent successfully")
                    } else {
                        Log.w("NotificationTest", "POST_NOTIFICATIONS permission not granted")
                    }
                }
            )

            // Show FCM token (truncated) for debugging
            ListItem(
                headlineContent = { Text("FCM Token") },
                supportingContent = {
                    var token by mutableStateOf("(tap to load)")
                    Text(token)
                    // Load on composition
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            val options = com.google.firebase.FirebaseApp.getInstance().options
                            token = "Project: ${options.projectId}\nSender: ${options.gcmSenderId}"
                        } catch (e: Exception) {
                            token = "Firebase not configured: ${e.message}"
                        }
                    }
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(
                            painter = painterResource(R.drawable.icn_key_24dp),
                            contentDescription = null,
                        )
                    }
                }
            )

            // Reset
            ListHeader {
                Text(stringResource(R.string.settings_category_miscellaneous))
            }

            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_notifications_reset),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    SettingsIcon(danger = true) {
                        Icon(
                            painter = painterResource(R.drawable.icn_delete_24dp),
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.clickable {
                    viewModel.resetNotificationSettings()
                }
            )
        }
    }
}
