package com.tribixbite.stoatally.screens.settings

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.InstanceConfig
import com.tribixbite.stoatally.api.ResolvedConfig
import com.tribixbite.stoatally.composables.generic.ListHeader
import com.tribixbite.stoatally.persistence.KVStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstanceSettingsViewModel @Inject constructor(
    val kvStorage: KVStorage
) : ViewModel()

/** Connection test state */
private sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Success(val config: ResolvedConfig) : TestState()
    data class Error(val message: String) : TestState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceSettingsScreen(
    navController: NavController,
    viewModel: InstanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var customUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<Pair<String, ResolvedConfig>?>(null) }
    var pendingName by remember { mutableStateOf("") }

    // Current instance indicator
    val currentApiUrl = InstanceConfig.apiUrl
    val currentName = InstanceConfig.instanceName

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = "Server Instance",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = "Back"
                        )
                    }
                },
            )
        },
    ) { pv ->
        Box(Modifier.padding(pv)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp)
            ) {
                // Current instance status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Current Instance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentApiUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Known instances
                ListHeader {
                    Text("Known Instances")
                }

                for (instance in InstanceConfig.knownInstances) {
                    val isSelected = currentApiUrl == instance.apiUrl
                    ListItem(
                        headlineContent = { Text(instance.name) },
                        supportingContent = { Text(instance.description) },
                        leadingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null // handled by row click
                            )
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            if (!isSelected) {
                                scope.launch {
                                    testState = TestState.Testing
                                    try {
                                        val config = InstanceConfig.testConnection(instance.apiUrl)
                                        testState = TestState.Success(config)
                                        pendingConfig = instance.apiUrl to config
                                        pendingName = instance.name
                                        showRestartDialog = true
                                    } catch (e: Exception) {
                                        testState = TestState.Error(e.message ?: "Connection failed")
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Custom instance
                ListHeader {
                    Text("Custom Instance")
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Enter the API base URL of a self-hosted Stoat/Revolt instance. " +
                                "All other URLs (WebSocket, CDN, proxy) are resolved automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            testState = TestState.Idle
                        },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://myinstance.com/api") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Instance Name (optional)") },
                        placeholder = { Text("My Server") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val url = customUrl.trim()
                                if (url.isBlank()) return@OutlinedButton
                                scope.launch {
                                    testState = TestState.Testing
                                    try {
                                        val config = InstanceConfig.testConnection(url)
                                        testState = TestState.Success(config)
                                    } catch (e: Exception) {
                                        Log.e("InstanceSettings", "Connection test failed", e)
                                        testState = TestState.Error(e.message ?: "Connection failed")
                                    }
                                }
                            },
                            enabled = customUrl.isNotBlank() && testState !is TestState.Testing
                        ) {
                            Text("Test Connection")
                        }

                        Button(
                            onClick = {
                                val url = customUrl.trim()
                                val name = customName.ifBlank { "Custom" }
                                val successConfig = (testState as? TestState.Success)?.config
                                if (successConfig != null) {
                                    pendingConfig = url to successConfig
                                    pendingName = name
                                    showRestartDialog = true
                                }
                            },
                            enabled = testState is TestState.Success
                        ) {
                            Text("Save & Apply")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Test result display
                    when (val state = testState) {
                        is TestState.Idle -> {}
                        is TestState.Testing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Text(
                                    "Testing connection...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        is TestState.Success -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Connection successful",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    ServerInfoRow("Server version", state.config.revolt)
                                    ServerInfoRow("WebSocket", state.config.ws)
                                    ServerInfoRow("Files (Autumn)", state.config.features.autumn.url)
                                    ServerInfoRow("Proxy (January)", state.config.features.january.url)
                                    ServerInfoRow("Web app", state.config.app)
                                    ServerInfoRow("Email", if (state.config.features.email) "Enabled" else "Disabled")
                                    ServerInfoRow("CAPTCHA", if (state.config.features.captcha.enabled) "Enabled" else "Disabled")
                                    ServerInfoRow("Invite only", if (state.config.features.inviteOnly) "Yes" else "No")
                                }
                            }
                        }
                        is TestState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Connection failed",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Reset to default option (only show when not on default)
                if (InstanceConfig.isCustomInstance) {
                    Spacer(Modifier.height(16.dp))
                    ListHeader {
                        Text("Reset")
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                "Reset to Stoat (Official)",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = {
                            Text("Clear custom instance and revert to stoat.chat")
                        },
                        modifier = Modifier.clickable {
                            pendingConfig = "https://api.stoat.chat/0.8" to ResolvedConfig(
                                revolt = "",
                                features = com.tribixbite.stoatally.api.ResolvedFeatures(
                                    captcha = com.tribixbite.stoatally.api.ResolvedCaptcha(true, ""),
                                    email = true,
                                    inviteOnly = false,
                                    autumn = com.tribixbite.stoatally.api.ResolvedService(true, "https://cdn.stoatusercontent.com"),
                                    january = com.tribixbite.stoatally.api.ResolvedService(true, "https://proxy.stoatusercontent.com"),
                                ),
                                ws = "wss://events.stoat.chat",
                                app = "https://stoat.chat",
                                vapid = ""
                            )
                            pendingName = "reset"
                            showRestartDialog = true
                        }
                    )
                }
            }
        }
    }

    // Restart dialog
    if (showRestartDialog && pendingConfig != null) {
        val (apiUrl, config) = pendingConfig!!
        val isReset = pendingName == "reset"
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(if (isReset) "Reset Instance?" else "Switch Instance?") },
            text = {
                Text(
                    if (isReset) {
                        "Reverting to official Stoat instance. " +
                                "The app will restart to connect to the new server. " +
                                "Your session will be preserved if valid."
                    } else {
                        "Switching to ${pendingName}. " +
                                "The app will restart to connect to the new server. " +
                                "Your session will be preserved if valid on this instance."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    scope.launch {
                        // Save current session token keyed to current instance
                        // so we can restore it when switching back later
                        InstanceConfig.saveSessionForCurrentInstance(viewModel.kvStorage)

                        if (isReset) {
                            InstanceConfig.resetToDefault()
                            InstanceConfig.clearFromStorage(viewModel.kvStorage)
                        } else {
                            InstanceConfig.apply(pendingName, apiUrl, config)
                            InstanceConfig.saveToStorage(viewModel.kvStorage)
                        }

                        // Restore a previously saved session for the target instance,
                        // or fall back to the current token (login check will validate)
                        val restoredToken = InstanceConfig.restoreSessionForCurrentInstance(viewModel.kvStorage)
                        if (restoredToken != null) {
                            viewModel.kvStorage.set("sessionToken", restoredToken)
                        }
                        // If no saved session exists, the existing token stays —
                        // checkLoggedInState() will validate it and redirect to
                        // login if it's invalid on the new instance.

                        // Restart the app to reinitialize API connections
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(context.packageName)
                            ?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                }) {
                    Text(if (isReset) "Reset & Restart" else "Switch & Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ServerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
