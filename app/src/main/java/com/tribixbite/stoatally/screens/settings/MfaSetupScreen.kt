package com.tribixbite.stoatally.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.routes.account.AccountInfo
import com.tribixbite.stoatally.api.routes.account.createMfaTicket
import com.tribixbite.stoatally.api.routes.account.disableTotp
import com.tribixbite.stoatally.api.routes.account.enableTotp
import com.tribixbite.stoatally.api.routes.account.fetchAccountInfo
import com.tribixbite.stoatally.api.routes.account.fetchRecoveryCodes
import com.tribixbite.stoatally.api.routes.account.generateRecoveryCodes
import com.tribixbite.stoatally.api.routes.account.generateTotpSecret
import com.tribixbite.stoatally.composables.generic.ListHeader
import kotlinx.coroutines.launch

/** MFA setup wizard states */
private enum class SetupStep {
    PASSWORD,    // Enter password to create MFA ticket
    SECRET,      // Show TOTP secret to add to authenticator app
    VERIFY,      // Enter code from authenticator to confirm setup
    DONE         // TOTP enabled successfully
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MfaSetupScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var accountInfo by remember { mutableStateOf<AccountInfo?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Setup flow state
    var showEnableDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var showRecoveryCodesDialog by remember { mutableStateOf(false) }
    var showRegenerateCodesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            accountInfo = fetchAccountInfo()
        } catch (e: Exception) {
            loadError = e.message
        }
        isLoading = false
    }

    // Enable TOTP dialog flow
    if (showEnableDialog) {
        EnableTotpDialog(
            onDismiss = { showEnableDialog = false },
            onSuccess = {
                showEnableDialog = false
                // Refresh account info
                scope.launch {
                    try { accountInfo = fetchAccountInfo() } catch (_: Exception) {}
                }
                Toast.makeText(context, context.getString(R.string.mfa_totp_enabled_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Disable TOTP dialog
    if (showDisableDialog) {
        DisableTotpDialog(
            onDismiss = { showDisableDialog = false },
            onSuccess = {
                showDisableDialog = false
                scope.launch {
                    try { accountInfo = fetchAccountInfo() } catch (_: Exception) {}
                }
                Toast.makeText(context, context.getString(R.string.mfa_totp_disabled_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // View recovery codes dialog
    if (showRecoveryCodesDialog) {
        RecoveryCodesDialog(
            onDismiss = { showRecoveryCodesDialog = false },
            regenerate = false
        )
    }

    // Regenerate recovery codes dialog
    if (showRegenerateCodesDialog) {
        RecoveryCodesDialog(
            onDismiss = { showRegenerateCodesDialog = false },
            regenerate = true
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.mfa_setup_title),
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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(32.dp)
                )
                return@Column
            }

            if (loadError != null) {
                Text(
                    text = loadError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }

            val totpEnabled = accountInfo?.mfa?.totpEnabled == true
            val recoveryActive = accountInfo?.mfa?.recoveryActive == true

            // Current status
            ListHeader { Text(stringResource(R.string.mfa_status_header)) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.mfa_totp_label)) },
                supportingContent = {
                    Text(
                        if (totpEnabled) stringResource(R.string.account_mfa_enabled)
                        else stringResource(R.string.account_mfa_disabled)
                    )
                },
                leadingContent = {
                    SettingsIcon {
                        Icon(painterResource(R.drawable.icn_key_24dp), contentDescription = null)
                    }
                }
            )

            if (recoveryActive) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.mfa_recovery_label)) },
                    supportingContent = { Text(stringResource(R.string.mfa_recovery_active)) },
                    leadingContent = {
                        SettingsIcon {
                            Icon(painterResource(R.drawable.icn_lock_24dp), contentDescription = null)
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Actions
            ListHeader { Text(stringResource(R.string.account_actions_header)) }

            if (!totpEnabled) {
                // Enable TOTP
                Button(
                    onClick = { showEnableDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(painterResource(R.drawable.icn_key_24dp), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mfa_enable_totp))
                }
            } else {
                // Disable TOTP
                Button(
                    onClick = { showDisableDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(painterResource(R.drawable.icn_close_24dp), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mfa_disable_totp))
                }

                // View recovery codes
                Button(
                    onClick = { showRecoveryCodesDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(painterResource(R.drawable.icn_lock_24dp), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mfa_view_recovery_codes))
                }

                // Regenerate recovery codes
                Button(
                    onClick = { showRegenerateCodesDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(painterResource(R.drawable.icn_edit_24dp), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mfa_regenerate_recovery_codes))
                }
            }
        }
    }
}

/** Multi-step dialog for enabling TOTP. */
@Composable
private fun EnableTotpDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var step by remember { mutableStateOf(SetupStep.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var mfaTicket by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var verifyCode by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                when (step) {
                    SetupStep.PASSWORD -> stringResource(R.string.mfa_step_password_title)
                    SetupStep.SECRET -> stringResource(R.string.mfa_step_secret_title)
                    SetupStep.VERIFY -> stringResource(R.string.mfa_step_verify_title)
                    SetupStep.DONE -> stringResource(R.string.mfa_totp_enabled_success)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    SetupStep.PASSWORD -> {
                        Text(stringResource(R.string.mfa_step_password_description))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.account_current_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SetupStep.SECRET -> {
                        Text(stringResource(R.string.mfa_step_secret_description))
                        Spacer(Modifier.height(8.dp))
                        // Show the TOTP secret in a selectable, copyable card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.mfa_secret_label),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(
                                        text = totpSecret,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(totpSecret))
                                Toast.makeText(context, context.getString(R.string.mfa_secret_copied), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(painterResource(R.drawable.icn_content_copy_24dp), contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.mfa_copy_secret))
                        }
                    }
                    SetupStep.VERIFY -> {
                        Text(stringResource(R.string.mfa_step_verify_description))
                        OutlinedTextField(
                            value = verifyCode,
                            onValueChange = { verifyCode = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.mfa_verify_code_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SetupStep.DONE -> {
                        Text(stringResource(R.string.mfa_setup_complete_description))
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                SetupStep.PASSWORD -> {
                    Button(
                        onClick = {
                            isProcessing = true
                            errorMsg = null
                            scope.launch {
                                try {
                                    val ticket = createMfaTicket(password)
                                    if (ticket != null) {
                                        mfaTicket = ticket
                                        val secret = generateTotpSecret(ticket)
                                        if (secret != null) {
                                            totpSecret = secret.secret
                                            step = SetupStep.SECRET
                                        } else {
                                            errorMsg = context.getString(R.string.mfa_error_generate_secret)
                                        }
                                    } else {
                                        errorMsg = context.getString(R.string.mfa_error_wrong_password)
                                    }
                                } catch (e: Exception) {
                                    errorMsg = e.message
                                }
                                isProcessing = false
                            }
                        },
                        enabled = password.isNotEmpty() && !isProcessing
                    ) { Text(stringResource(R.string.mfa_next)) }
                }
                SetupStep.SECRET -> {
                    Button(onClick = { step = SetupStep.VERIFY }) {
                        Text(stringResource(R.string.mfa_next))
                    }
                }
                SetupStep.VERIFY -> {
                    Button(
                        onClick = {
                            isProcessing = true
                            errorMsg = null
                            scope.launch {
                                try {
                                    val error = enableTotp(verifyCode)
                                    if (error == null) {
                                        step = SetupStep.DONE
                                    } else {
                                        errorMsg = error
                                    }
                                } catch (e: Exception) {
                                    errorMsg = e.message
                                }
                                isProcessing = false
                            }
                        },
                        enabled = verifyCode.length == 6 && !isProcessing
                    ) { Text(stringResource(R.string.mfa_enable_button)) }
                }
                SetupStep.DONE -> {
                    Button(onClick = onSuccess) {
                        Text(stringResource(R.string.mfa_done))
                    }
                }
            }
        },
        dismissButton = {
            if (step != SetupStep.DONE) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/** Dialog for disabling TOTP — requires password to create MFA ticket. */
@Composable
private fun DisableTotpDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text(stringResource(R.string.mfa_disable_totp_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mfa_disable_totp_description))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.account_current_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val ticket = createMfaTicket(password)
                            if (ticket != null) {
                                val error = disableTotp(ticket)
                                if (error == null) {
                                    onSuccess()
                                } else {
                                    errorMsg = error
                                }
                            } else {
                                errorMsg = context.getString(R.string.mfa_error_wrong_password)
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message
                        }
                        isProcessing = false
                    }
                },
                enabled = password.isNotEmpty() && !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text(stringResource(R.string.mfa_disable_totp)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Dialog for viewing or regenerating recovery codes. */
@Composable
private fun RecoveryCodesDialog(
    onDismiss: () -> Unit,
    regenerate: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var password by remember { mutableStateOf("") }
    var codes by remember { mutableStateOf<List<String>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                if (regenerate) stringResource(R.string.mfa_regenerate_recovery_codes)
                else stringResource(R.string.mfa_view_recovery_codes)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsPassword) {
                    Text(stringResource(R.string.mfa_step_password_description))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.account_current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (codes != null) {
                    if (regenerate) {
                        Text(
                            stringResource(R.string.mfa_recovery_codes_regenerated),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(stringResource(R.string.mfa_recovery_codes_description))
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Column(modifier = Modifier.padding(12.dp)) {
                                codes!!.forEach { code ->
                                    Text(
                                        text = code,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(codes!!.joinToString("\n")))
                            Toast.makeText(context, context.getString(R.string.mfa_recovery_codes_copied), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(painterResource(R.drawable.icn_content_copy_24dp), contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.mfa_copy_all))
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            if (needsPassword) {
                Button(
                    onClick = {
                        isProcessing = true
                        errorMsg = null
                        scope.launch {
                            try {
                                val ticket = createMfaTicket(password)
                                if (ticket != null) {
                                    codes = if (regenerate) {
                                        generateRecoveryCodes(ticket)
                                    } else {
                                        fetchRecoveryCodes(ticket)
                                    }
                                    needsPassword = false
                                } else {
                                    errorMsg = context.getString(R.string.mfa_error_wrong_password)
                                }
                            } catch (e: Exception) {
                                errorMsg = e.message
                            }
                            isProcessing = false
                        }
                    },
                    enabled = password.isNotEmpty() && !isProcessing
                ) { Text(stringResource(R.string.mfa_next)) }
            } else {
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.mfa_done))
                }
            }
        },
        dismissButton = {
            if (needsPassword) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
