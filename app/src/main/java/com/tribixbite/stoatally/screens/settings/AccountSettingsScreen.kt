package com.tribixbite.stoatally.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.routes.account.AccountInfo
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.routes.account.changeEmail
import com.tribixbite.stoatally.api.routes.account.changePassword
import com.tribixbite.stoatally.api.routes.account.deleteAccount
import com.tribixbite.stoatally.api.routes.account.disableAccount
import com.tribixbite.stoatally.api.routes.account.fetchAccountInfo
import com.tribixbite.stoatally.api.routes.user.changeUsername
import com.tribixbite.stoatally.composables.generic.ListHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var accountInfo by remember { mutableStateOf<AccountInfo?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showChangeUsername by remember { mutableStateOf(false) }
    var showChangeEmail by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showDisableAccount by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            accountInfo = fetchAccountInfo()
        } catch (e: Exception) {
            loadError = e.message
        }
    }

    // Change username dialog
    if (showChangeUsername) {
        var newUsername by remember {
            mutableStateOf(StoatAPI.userCache[StoatAPI.selfId]?.username ?: "")
        }
        var currentPassword by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangeUsername = false },
            title = { Text(stringResource(R.string.account_change_username_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text(stringResource(R.string.account_new_username)) },
                        placeholder = { Text(stringResource(R.string.account_username_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.account_current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val error = changeUsername(newUsername.trim(), currentPassword)
                            if (error == null) {
                                showChangeUsername = false
                                Toast.makeText(context, context.getString(R.string.account_username_changed), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = newUsername.trim().length in 2..32
                            && currentPassword.isNotEmpty() && !isSaving
                ) { Text(stringResource(R.string.server_settings_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showChangeUsername = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Change email dialog
    if (showChangeEmail) {
        var newEmail by remember { mutableStateOf("") }
        var currentPassword by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangeEmail = false },
            title = { Text(stringResource(R.string.account_change_email_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text(stringResource(R.string.account_new_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.account_current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val error = changeEmail(newEmail, currentPassword)
                            if (error == null) {
                                showChangeEmail = false
                                Toast.makeText(context, context.getString(R.string.account_email_changed), Toast.LENGTH_SHORT).show()
                                // Refresh account info
                                try { accountInfo = fetchAccountInfo() } catch (_: Exception) {}
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = newEmail.contains("@") && currentPassword.isNotEmpty() && !isSaving
                ) { Text(stringResource(R.string.server_settings_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showChangeEmail = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Change password dialog
    if (showChangePassword) {
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangePassword = false },
            title = { Text(stringResource(R.string.account_change_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.account_current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.account_new_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.account_confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                        Text(
                            stringResource(R.string.account_passwords_mismatch),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val error = changePassword(newPassword, currentPassword)
                            if (error == null) {
                                showChangePassword = false
                                Toast.makeText(context, context.getString(R.string.account_password_changed), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isSaving = false
                        }
                    },
                    enabled = currentPassword.isNotEmpty() && newPassword.length >= 8
                            && newPassword == confirmPassword && !isSaving
                ) { Text(stringResource(R.string.server_settings_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showChangePassword = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Disable account confirmation
    if (showDisableAccount) {
        var isProcessing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showDisableAccount = false },
            title = { Text(stringResource(R.string.account_disable_title)) },
            text = { Text(stringResource(R.string.account_disable_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val error = disableAccount()
                            if (error == null) {
                                showDisableAccount = false
                                Toast.makeText(context, context.getString(R.string.account_disabled), Toast.LENGTH_SHORT).show()
                                navController.navigate("login/greeting") {
                                    popUpTo("chat") { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing
                ) { Text(stringResource(R.string.account_disable_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisableAccount = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete account confirmation
    if (showDeleteAccount) {
        var isProcessing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) showDeleteAccount = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val error = deleteAccount()
                            if (error == null) {
                                showDeleteAccount = false
                                navController.navigate("login/greeting") {
                                    popUpTo("chat") { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(
                        stringResource(R.string.account_delete_button),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccount = false }) {
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
                        text = stringResource(R.string.account_settings_title),
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
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            // Account info
            ListHeader { Text(stringResource(R.string.account_info_header)) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.account_email_label)) },
                supportingContent = {
                    Text(
                        accountInfo?.email ?: loadError ?: stringResource(R.string.search_messages_loading)
                    )
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_id_card_24dp), contentDescription = null)
                    }
                }
            )

            // MFA status — tap to manage
            ListItem(
                headlineContent = { Text(stringResource(R.string.account_mfa_status)) },
                supportingContent = {
                    val mfa = accountInfo?.mfa
                    Text(
                        if (mfa?.totpEnabled == true) stringResource(R.string.account_mfa_enabled)
                        else stringResource(R.string.account_mfa_disabled)
                    )
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_key_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { navController.navigate("settings/mfa") }
            )

            // Actions
            ListHeader { Text(stringResource(R.string.account_actions_header)) }

            // Current username display
            ListItem(
                headlineContent = { Text(stringResource(R.string.onboarding_username)) },
                supportingContent = {
                    val self = StoatAPI.userCache[StoatAPI.selfId]
                    Text(
                        "${self?.username ?: ""}#${self?.discriminator ?: ""}"
                    )
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_account_circle_24dp), contentDescription = null)
                    }
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.account_change_username_title)) },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_account_circle_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { showChangeUsername = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.account_change_email_title)) },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_id_card_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { showChangeEmail = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.account_change_password_title)) },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_lock_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { showChangePassword = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.session_management_title)) },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_devices_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { navController.navigate("settings/sessions") }
            )

            // Danger zone
            ListHeader { Text(stringResource(R.string.account_danger_zone)) }

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.account_disable_title),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.account_disable_description))
                },
                leadingContent = {
                    SettingsIcon(danger = true) {
                        Icon(painterResource(R.drawable.icn_block_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { showDisableAccount = true }
            )

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.account_delete_title),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.account_delete_description))
                },
                leadingContent = {
                    SettingsIcon(danger = true) {
                        Icon(painterResource(R.drawable.icn_delete_24dp), contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { showDeleteAccount = true }
            )
        }
    }
}
