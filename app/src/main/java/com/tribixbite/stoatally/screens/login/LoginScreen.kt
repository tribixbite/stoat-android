package com.tribixbite.stoatally.screens.login

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.StoatApplication
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.routes.account.EmailPasswordAssessment
import com.tribixbite.stoatally.api.routes.account.negotiateAuthentication
import com.tribixbite.stoatally.api.routes.account.resendVerification
import com.tribixbite.stoatally.api.routes.account.sendPasswordReset
import com.tribixbite.stoatally.api.routes.onboard.needsOnboarding
import com.tribixbite.stoatally.composables.generic.FormTextField
import com.tribixbite.stoatally.persistence.KVStorage
import com.tribixbite.stoatally.ui.theme.FragmentMono
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val kvStorage: KVStorage
) : ViewModel() {
    private var _email by mutableStateOf("")
    val email: String
        get() = _email

    private var _password by mutableStateOf("")
    val password: String
        get() = _password

    private var _error by mutableStateOf<String?>(null)
    val error: String?
        get() = _error

    private var _navigateTo by mutableStateOf<String?>(null)
    val navigateTo: String?
        get() = _navigateTo

    private var _mfaResponse by mutableStateOf<EmailPasswordAssessment?>(null)
    val mfaResponse: EmailPasswordAssessment?
        get() = _mfaResponse

    private var _isLoggingIn by mutableStateOf(false)
    val isLoggingIn: Boolean
        get() = _isLoggingIn

    fun doLogin() {
        if (_isLoggingIn) return // Guard against double-tap (#30)
        _isLoggingIn = true
        _error = null

        viewModelScope.launch {
            try {
                val response = try {
                    negotiateAuthentication(_email, _password)
                } catch (e: Exception) {
                    _error = if (e.message?.startsWith("Unexpected JSON token") == true) {
                        StoatApplication.instance.getString(R.string.service_health_alert_body_default)
                    } else e.message ?: "Unknown error"
                    _isLoggingIn = false
                    return@launch
                }
                if (response.error != null) {
                    _error = response.error.type
                } else {
                    Log.d("Login", "Checking for MFA")
                    if (response.proceedMfa) {
                        Log.d("Login", "MFA required. Navigating to MFA screen")
                        _mfaResponse = response
                        _navigateTo = "mfa"
                    } else {
                        Log.d(
                            "Login",
                            "No MFA required. Login is complete! We should have a session token"
                        )

                        val hints = response.firstUserHints
                        if (hints == null) {
                            _error = "Login succeeded but no session data received"
                            _isLoggingIn = false
                            return@launch
                        }
                        val token = hints.token
                        val id = hints.id

                        kvStorage.set("sessionToken", token)
                        kvStorage.set("sessionId", id)

                        val onboard = needsOnboarding(token)
                        if (onboard) {
                            _navigateTo = "onboarding"
                            _isLoggingIn = false
                            return@launch
                        }

                        StoatAPI.loginAs(token)
                        StoatAPI.setSessionId(id) // Was incorrectly using token instead of id

                        _navigateTo = "home"
                    }
                }
            } catch (e: Exception) {
                _error = e.message ?: "Unknown error"
            }
            _isLoggingIn = false
        }
    }

    fun navigationComplete() {
        _navigateTo = null
    }

    fun setEmail(email: String) {
        _email = email
    }

    fun setPassword(password: String) {
        _password = password
    }
}

@Composable
fun LoginScreen(navController: NavController, viewModel: LoginViewModel = hiltViewModel()) {
    val passwordTextFieldState = rememberTextFieldState()
    LaunchedEffect(passwordTextFieldState.text) {
        viewModel.setPassword(passwordTextFieldState.text.toString())
    }
    val showPassword = remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showResendVerifyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.navigateTo) {
        when (viewModel.navigateTo) {
            "mfa" -> {
                val spec = viewModel.mfaResponse?.mfaSpec
                if (spec != null) {
                    navController.navigate(
                        "login/mfa/${spec.ticket}/${spec.allowedMethods.joinToString(",")}"
                    )
                }
            }

            "home" -> {
                navController.navigate("chat") {
                    popUpTo("login/greeting") { inclusive = true }
                }
            }

            "onboarding" -> {
                navController.navigate("register/onboarding") {
                    popUpTo("login/greeting") { inclusive = true }
                }
            }
        }
        if (viewModel.navigateTo != null) {
            viewModel.navigationComplete()
        }
    }

    // Forgot password dialog — native API call
    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(viewModel.email) }
        var isSending by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSending) showForgotPasswordDialog = false },
            title = { Text(stringResource(R.string.password_forgot_heading)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.password_forgot_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(stringResource(R.string.email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSending
                    )
                    if (dialogError != null) {
                        Text(
                            dialogError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSending = true
                        dialogError = null
                        scope.launch {
                            val err = withContext(Dispatchers.IO) { sendPasswordReset(resetEmail) }
                            if (err != null) {
                                dialogError = err
                            } else {
                                Toast.makeText(context, R.string.password_forgot_success, Toast.LENGTH_LONG).show()
                                showForgotPasswordDialog = false
                            }
                            isSending = false
                        }
                    },
                    enabled = resetEmail.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.password_forgot_send))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    enabled = !isSending
                ) {
                    Text(stringResource(R.string.back))
                }
            }
        )
    }

    // Resend verification dialog — native API call
    if (showResendVerifyDialog) {
        var verifyEmail by remember { mutableStateOf(viewModel.email) }
        var isSending by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSending) showResendVerifyDialog = false },
            title = { Text(stringResource(R.string.resend_verification_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.resend_verification_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = verifyEmail,
                        onValueChange = { verifyEmail = it },
                        label = { Text(stringResource(R.string.email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSending
                    )
                    if (dialogError != null) {
                        Text(
                            dialogError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSending = true
                        dialogError = null
                        scope.launch {
                            val err = withContext(Dispatchers.IO) { resendVerification(verifyEmail) }
                            if (err != null) {
                                dialogError = err
                            } else {
                                Toast.makeText(context, R.string.resend_verification_success, Toast.LENGTH_LONG).show()
                                showResendVerifyDialog = false
                            }
                            isSending = false
                        }
                    },
                    enabled = verifyEmail.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.resend_verification_send))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResendVerifyDialog = false },
                    enabled = !isSending
                ) {
                    Text(stringResource(R.string.back))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .imePadding()
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.login_heading),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .width(270.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FormTextField(
                    value = viewModel.email,
                    label = stringResource(R.string.email),
                    type = KeyboardType.Email,
                    action = ImeAction.Next,
                    onChange = viewModel::setEmail,
                    modifier = Modifier
                        .padding(vertical = 25.dp)
                        .semantics {
                            contentType = ContentType.EmailAddress
                        }
                )
                SecureTextField(
                    passwordTextFieldState,
                    label = { Text(stringResource(R.string.password)) },
                    textObfuscationMode = if (showPassword.value) {
                        TextObfuscationMode.Visible
                    } else {
                        TextObfuscationMode.RevealLastTyped
                    },
                    textStyle = if (showPassword.value) LocalTextStyle.current else LocalTextStyle.current.copy(
                        fontFamily = FragmentMono
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            showPassword.value = !showPassword.value
                        }) {
                            when {
                                showPassword.value -> {
                                    Icon(
                                        painter = painterResource(R.drawable.icn_visibility_off_24dp),
                                        contentDescription = stringResource(R.string.hide_password)
                                    )
                                }

                                else -> {
                                    Icon(
                                        painter = painterResource(R.drawable.icn_visibility_24dp),
                                        contentDescription = stringResource(R.string.show_password)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.semantics {
                        contentType = ContentType.Password
                    }
                )

                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.padding(vertical = 7.dp)
                ) {
                    Text(
                        text = stringResource(R.string.password_forgot),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                viewModel.error?.let { errorText ->
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(
                onClick = { showResendVerifyDialog = true },
                modifier = Modifier
                    .padding(vertical = 7.dp)
                    .testTag("resend_verification_link")
            ) {
                Text(
                    text = stringResource(R.string.resend_verification),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row {
                TextButton(onClick = {
                    navController.popBackStack()
                }) {
                    Text(text = stringResource(R.string.back))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = { viewModel.doLogin() },
                    enabled = !viewModel.isLoggingIn
                ) {
                    if (viewModel.isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(text = stringResource(R.string.login))
                    }
                }
            }
        }
    }
}
