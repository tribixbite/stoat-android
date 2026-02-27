package com.tribixbite.stoatally.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutExpo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tribixbite.stoatally.BuildConfig
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.StoatApplication
import com.tribixbite.stoatally.callbacks.Action
import com.tribixbite.stoatally.callbacks.ActionChannel
import com.tribixbite.stoatally.api.HitRateLimitException
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.api.routes.microservices.geo.queryGeo
import com.tribixbite.stoatally.api.routes.microservices.health.healthCheck
import com.tribixbite.stoatally.api.routes.onboard.needsOnboarding
import com.tribixbite.stoatally.core.model.schemas.HealthNotice
import com.tribixbite.stoatally.api.settings.Experiments
import com.tribixbite.stoatally.api.settings.GeoStateProvider
import com.tribixbite.stoatally.api.settings.LoadedSettings
import com.tribixbite.stoatally.api.settings.SyncedSettings
import com.tribixbite.stoatally.composables.generic.HealthAlert
import com.tribixbite.stoatally.composables.voice.VoicePermissionSwitch
import com.tribixbite.stoatally.composables.voice.VoiceSheet
import com.tribixbite.stoatally.material.EasingTokens
import com.tribixbite.stoatally.ndk.NativeLibraries
import com.tribixbite.stoatally.persistence.KVStorage
import com.tribixbite.stoatally.screens.DefaultDestinationScreen
import com.tribixbite.stoatally.screens.about.AboutScreen
import com.tribixbite.stoatally.screens.about.AttributionScreen
import com.tribixbite.stoatally.screens.chat.ChatRouterScreen
import com.tribixbite.stoatally.screens.chat.standalone.CatchUpScreen
import com.tribixbite.stoatally.screens.chat.views.channel.ChannelScreen
import com.tribixbite.stoatally.screens.create.CreateGroupScreen
import com.tribixbite.stoatally.screens.labs.LabsRootScreen
import com.tribixbite.stoatally.screens.login.LoginGreetingScreen
import com.tribixbite.stoatally.screens.login.LoginScreen
import com.tribixbite.stoatally.screens.login.MfaScreen
import com.tribixbite.stoatally.screens.login2.InitScreen
import com.tribixbite.stoatally.screens.main.MainScreen
import com.tribixbite.stoatally.screens.register.OnboardingScreen
import com.tribixbite.stoatally.screens.register.RegisterDetailsScreen
import com.tribixbite.stoatally.screens.register.RegisterGreetingScreen
import com.tribixbite.stoatally.screens.register.RegisterVerifyScreen
import com.tribixbite.stoatally.screens.services.DiscoverScreen
import com.tribixbite.stoatally.screens.settings.AppearanceSettingsScreen
import com.tribixbite.stoatally.screens.settings.ChangelogsSettingsScreen
import com.tribixbite.stoatally.screens.settings.ChatSettingsScreen
import com.tribixbite.stoatally.screens.settings.DebugSettingsScreen
import com.tribixbite.stoatally.screens.settings.ExperimentsSettingsScreen
import com.tribixbite.stoatally.screens.settings.LanguagePickerSettingsScreen
import com.tribixbite.stoatally.screens.settings.ProfileSettingsScreen
import com.tribixbite.stoatally.screens.settings.AccountSettingsScreen
import com.tribixbite.stoatally.screens.settings.MfaSetupScreen
import com.tribixbite.stoatally.screens.settings.SessionManagementScreen
import com.tribixbite.stoatally.screens.settings.NotificationSettingsScreen
import com.tribixbite.stoatally.screens.settings.SettingsScreen
import com.tribixbite.stoatally.screens.settings.channel.ChannelSettingsHome
import com.tribixbite.stoatally.screens.settings.channel.ChannelSettingsOverview
import com.tribixbite.stoatally.screens.settings.channel.ChannelSettingsPermissions
import com.tribixbite.stoatally.screens.search.MessageSearchScreen
import com.tribixbite.stoatally.screens.settings.server.BanManagementScreen
import com.tribixbite.stoatally.screens.settings.server.CreateChannelScreen
import com.tribixbite.stoatally.screens.settings.server.EmojiManagementScreen
import com.tribixbite.stoatally.screens.settings.server.InviteManagementScreen
import com.tribixbite.stoatally.screens.settings.server.DefaultPermissionsEditorScreen
import com.tribixbite.stoatally.screens.settings.server.RoleManagementScreen
import com.tribixbite.stoatally.screens.settings.server.RolePermissionsEditorScreen
import com.tribixbite.stoatally.screens.settings.server.BridgeSettingsScreen
import com.tribixbite.stoatally.screens.settings.server.DiscordImportScreen
import com.tribixbite.stoatally.screens.settings.server.ServerSettingsScreen
import com.tribixbite.stoatally.screens.settings.server.SystemMessagesScreen
import com.tribixbite.stoatally.screens.settings.server.WebhookManagementScreen
import com.tribixbite.stoatally.screens.settings.BotManagementScreen
import com.tribixbite.stoatally.ui.theme.StoatTheme
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.get
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class MainActivityViewModel @Inject constructor(
    private val kvStorage: KVStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val nextDestination = MutableStateFlow<String?>(null)
    var isConnected = MutableStateFlow(false)
    val isReady = MutableStateFlow(false)
    val couldNotLogIn = MutableStateFlow(false)

    // Channel ID from notification tap — consumed after login to navigate directly
    var pendingNotificationChannelId: String? = null

    /**
     * Extract channelId from a notification tap intent.
     * Called from onCreate before the login state check coroutine progresses.
     */
    fun handleNotificationIntent(intent: Intent?) {
        val channelId = intent?.getStringExtra("channelId")
        if (channelId != null) {
            Log.d("MainActivity", "Notification tap: pending navigation to channel $channelId")
            pendingNotificationChannelId = channelId
        }
    }

    /**
     * Handle new intent when activity is already running (e.g. notification tap
     * while app is in foreground). Sends directly via ActionChannel since
     * ChatRouterScreen's listener is already active.
     */
    fun handleNewIntent(intent: Intent?) {
        val channelId = intent?.getStringExtra("channelId") ?: return
        Log.d("MainActivity", "New intent: switching to channel $channelId")
        viewModelScope.launch {
            ActionChannel.send(Action.SwitchChannel(channelId))
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> true
            else -> false
        }
    }

    private suspend fun canReachStoat(): Boolean {
        try {
            val res = StoatHttp.get("/".api())
            return res.status.value == 200
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun startWithDestination(destination: String) {
        nextDestination.emit(destination)
        isReady.emit(true)
    }

    private suspend fun startWithoutDestination() {
        isReady.emit(true)
    }

    private fun doPreStartupTasks() {
        Log.d("MainActivity", "Performing pre-startup tasks")
        viewModelScope.launch {
            Log.d("MainActivity", "Hydrating Experiments from KV")
            Experiments.hydrateWithKv()
            // Health check and geo update are independent — run in parallel
            coroutineScope {
                launch {
                    Log.d("MainActivity", "Performing health check")
                    doHealthCheck()
                }
                launch {
                    Log.d("MainActivity", "Performing update geo state")
                    updateGeoState()
                }
            }
        }
    }

    private suspend fun updateGeoState() {
        try {
            Log.d("MainActivity", "Querying geo state")
            GeoStateProvider.updateGeoState(queryGeo())
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to query geo state", e)
        }
    }

    fun checkLoggedInState() {
        viewModelScope.launch {
            Log.d("MainActivity", "Checking logged in state")

            // Load saved instance config before any API calls
            com.tribixbite.stoatally.api.InstanceConfig.loadFromStorage(kvStorage)

            isConnected.emit(hasInternetConnection())

            Log.d("MainActivity", "Checking if we can reach instance (${com.tribixbite.stoatally.api.InstanceConfig.instanceName})")

            if (!isConnected.value) return@launch startWithoutDestination()

            Log.d("MainActivity", "We can reach Stoat, checking if we're logged in")

            val token = kvStorage.get("sessionToken")
                ?: return@launch startWithDestination("login/greeting")
            val id = kvStorage.get("sessionId") ?: ""

            Log.d(
                "MainActivity",
                "We have a session token, checking if it's valid and if we can still reach Stoat"
            )

            // Run reachability and token validation in parallel — both are independent reads
            val (canReachStoat, valid) = coroutineScope {
                val reachDeferred = async { canReachStoat() }
                val validDeferred = async {
                    try { StoatAPI.checkSessionToken(token) } catch (_: Throwable) { false }
                }
                reachDeferred.await() to validDeferred.await()
            }

            if (canReachStoat && !valid) {
                Log.d("MainActivity", "Session token is invalid, could not log in")
                couldNotLogIn.emit(true)
            } else {
                try {
                    Log.d("MainActivity", "Session token is valid, checking onboarding state")
                    val onboard = needsOnboarding(token)
                    if (onboard) {
                        Log.d("MainActivity", "Onboarding state is incomplete, starting onboarding")
                        startWithDestination("register/onboarding")
                        return@launch
                    }
                } catch (e: HitRateLimitException) {
                    Log.e("MainActivity", "Rate limited while checking onboarding state", e)
                    Toast.makeText(
                        context,
                        context.getString(R.string.rate_limit_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch startWithoutDestination()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to check onboarding state, could not log in", e)
                    couldNotLogIn.emit(true)
                }

                try {
                    Log.d("MainActivity", "Onboarding state is complete, logging in")
                    StoatAPI.loginAs(token)
                    StoatAPI.setSessionId(id)

                    // Save session token keyed to current instance for hot-swap restore
                    com.tribixbite.stoatally.api.InstanceConfig.saveSessionForCurrentInstance(kvStorage)

                    // If launched from a notification tap, set the target channel
                    // so ChatRouterViewModel picks it up on initialization
                    val notifChannel = pendingNotificationChannelId
                    if (notifChannel != null) {
                        Log.d("MainActivity", "Setting notification channel destination: $notifChannel")
                        kvStorage.set("currentDestination", "channel/$notifChannel")
                        pendingNotificationChannelId = null
                    }

                    if (Experiments.usePolar.isEnabled) {
                        startWithDestination("main")
                    } else {
                        startWithDestination("chat")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to login, could not log in", e)
                    couldNotLogIn.emit(true)
                }
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            kvStorage.remove("sessionToken")
            kvStorage.remove("sessionId")
            // Clear all in-memory caches and persistent DB so stale
            // suspended/deleted user state doesn't survive logout (#27)
            StoatAPI.logout()
            startWithDestination("login/greeting")
        }
    }

    fun updateNextDestination(destination: String) {
        viewModelScope.launch {
            nextDestination.emit(null)
            nextDestination.emit(destination)
        }
    }

    val activeAlert = MutableStateFlow<HealthNotice?>(null)
    val isAlertActive = MutableStateFlow(false)

    private suspend fun doHealthCheck() {
        try {
            val health = healthCheck()
            if (health.alert != null) {
                activeAlert.emit(health)
                isAlertActive.emit(true)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to perform health check", e)
        }
    }

    fun onDismissHealthAlert() {
        viewModelScope.launch {
            activeAlert.emit(null)
            isAlertActive.emit(false)
        }
    }

    fun onDismissLoginError() {
        viewModelScope.launch {
            couldNotLogIn.emit(false)
        }
    }

    init {
        Log.d("MainActivity", "Starting up")
        doPreStartupTasks()
        checkLoggedInState()
    }
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainActivityViewModel>()

    // Fix for SDK >=31, where core-splashscreen accidentally removes dynamic colours
    // See the other one in DefaultDestinationScreen.kt
    override fun onResume() {
        super.onResume()
        DynamicColors.applyToActivityIfAvailable(this)
        DynamicColors.applyToActivitiesIfAvailable(StoatApplication.instance)
        @Suppress("DEPRECATION") // We are fixing a bug in the splash screen
        window.statusBarColor = Color.Transparent.toArgb()
    }

    // Same as above for configuration changes (rotation, dark mode, etc.)
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        DynamicColors.applyToActivityIfAvailable(this)
        DynamicColors.applyToActivitiesIfAvailable(StoatApplication.instance)
        @Suppress("DEPRECATION") // We are fixing a bug in the splash screen
        window.statusBarColor = Color.Transparent.toArgb()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract notification channel ID before the ViewModel's login coroutine progresses
        viewModel.handleNotificationIntent(intent)

        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = BuildConfig.VERSION_NAME
        }

        @Suppress("DEPRECATION") // We are fixing a bug in the splash screen
        window.statusBarColor = Color.Transparent.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        StoatAPI.hydrateFromPersistentCache()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            AppEntrypoint(
                windowSizeClass,
                viewModel.nextDestination.collectAsState().value,
                viewModel.isConnected.collectAsState().value,
                viewModel.activeAlert.collectAsState().value,
                viewModel.isAlertActive.collectAsState().value,
                viewModel.couldNotLogIn.collectAsState().value,
                viewModel::logOut,
                viewModel::onDismissHealthAlert,
                viewModel::onDismissLoginError,
                viewModel::checkLoggedInState,
                viewModel::updateNextDestination
            )
        }

        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    // Check whether the initial data is ready.
                    return if (viewModel.isReady.value) {
                        // The content is ready. Start drawing.
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        // The content isn't ready. Suspend.
                        false
                    }
                }
            }
        )
    }

    // Handle notification taps when activity is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleNewIntent(intent)
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int
    ) {
        val messaging = KeyboardShortcutGroup(
            getString(R.string.keyboard_shortcut_messaging),
            listOf(
                KeyboardShortcutInfo(
                    getString(R.string.keyboard_shortcut_messaging_new_line),
                    KeyEvent.KEYCODE_ENTER,
                    0
                ),
                KeyboardShortcutInfo(
                    getString(R.string.keyboard_shortcut_messaging_send_message),
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.META_CTRL_ON
                )
            )
        )

        data?.add(messaging)
    }

    companion object {
        init {
            NativeLibraries.init()
        }
    }
}

val StoatTweenInt: FiniteAnimationSpec<IntOffset> = tween(400, easing = EaseInOutExpo)
val StoatTweenFloat: FiniteAnimationSpec<Float> = tween(400, easing = EaseInOutExpo)
val StoatTweenDp: FiniteAnimationSpec<Dp> = tween(400, easing = EaseInOutExpo)
val StoatTweenColour: FiniteAnimationSpec<Color> = tween(400, easing = EaseInOutExpo)

val NavTweenInt: FiniteAnimationSpec<IntOffset> = tween(350, easing = EaseInOutExpo)
val NavTweenFloat: FiniteAnimationSpec<Float> = tween(350, easing = EaseInOutExpo)

// This composable handles the main compose entrypoint of the app, provides the main navigation
// graph, and handles the animation and layout for the voice chat UI.
@Composable
fun AppEntrypoint(
    windowSizeClass: WindowSizeClass,
    nextDestination: String?,
    isConnected: Boolean,
    healthNotice: HealthNotice?,
    isHealthAlertActive: Boolean,
    couldNotLogIn: Boolean,
    onLogout: () -> Unit = {},
    onDismissHealthAlert: () -> Unit = {},
    onDismissLoginError: () -> Unit = {},
    onRetryConnection: () -> Unit,
    onUpdateNextDestination: (String) -> Unit = {}
) {
    var showVoiceUI by rememberSaveable { mutableStateOf(false) }
    var voiceChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    val chatUIScale by animateFloatAsState(
        if (showVoiceUI) 0.8f else 1.0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EasingTokens.EmphasizedDecelerate
        )
    )
    val chatUIOpacity by animateFloatAsState(
        if (showVoiceUI) 0.8f else 1.0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EasingTokens.EmphasizedDecelerate
        )
    )

    BackHandler(showVoiceUI) {
        showVoiceUI = false
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showVoiceUI) {
        if (showVoiceUI) keyboardController?.hide()
    }

    val navController = rememberNavController()

    StoatTheme(
        requestedTheme = LoadedSettings.theme,
        requestedUserInterfaceFont = LoadedSettings.font,
        colourOverrides = SyncedSettings.android.colourOverrides
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(chatUIScale)
                    .alpha(chatUIOpacity),
                color = MaterialTheme.colorScheme.background
            ) {
                if (isHealthAlertActive) {
                    healthNotice?.let {
                        HealthAlert(notice = healthNotice, onDismiss = onDismissHealthAlert)
                    }
                }

                if (couldNotLogIn) {
                    AlertDialog(
                        onDismissRequest = {
                            // no-op
                        },
                        title = {
                            Text(stringResource(R.string.could_not_log_in_heading))
                        },
                        text = {
                            Text(stringResource(R.string.could_not_log_in_body))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onDismissLoginError()
                                    onRetryConnection()
                                }
                            ) {
                                Text(stringResource(R.string.could_not_log_in_cta_try_again))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    onDismissLoginError()
                                    onLogout()
                                }
                            ) {
                                Text(stringResource(R.string.could_not_log_in_cta_logout))
                            }
                        }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "default",
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = NavTweenInt,
                            initialOffset = { it / 3 }
                        ) + fadeIn(animationSpec = NavTweenFloat)
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = NavTweenInt,
                            targetOffset = { it / 3 }
                        ) + fadeOut(animationSpec = NavTweenFloat)
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = NavTweenInt,
                            initialOffset = { it / 3 }
                        ) + fadeIn(animationSpec = NavTweenFloat)
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = NavTweenInt,
                            targetOffset = { it / 2 }
                        ) + fadeOut(animationSpec = NavTweenFloat)
                    }
                ) {
                    composable("default") {
                        DefaultDestinationScreen(
                            navController,
                            nextDestination,
                            isConnected,
                            onRetryConnection
                        )
                    }

                    composable("login/greeting") { LoginGreetingScreen(navController) }
                    composable("login/login") { LoginScreen(navController) }
                    composable("login/mfa/{mfaTicket}/{allowedAuthTypes}") { backStackEntry ->
                        val mfaTicket = backStackEntry.arguments?.getString("mfaTicket") ?: ""
                        val allowedAuthTypes =
                            backStackEntry.arguments?.getString("allowedAuthTypes") ?: ""

                        MfaScreen(navController, allowedAuthTypes, mfaTicket)
                    }

                    composable("register/greeting") { RegisterGreetingScreen(navController) }
                    composable("register/details") { RegisterDetailsScreen(navController) }
                    composable("register/verify/{email}") { backStackEntry ->
                        val email = backStackEntry.arguments?.getString("email") ?: ""

                        RegisterVerifyScreen(navController, email)
                    }
                    composable("register/onboarding") {
                        OnboardingScreen(
                            navController,
                            onOnboardingComplete = {
                                onUpdateNextDestination("chat")
                                navController.popBackStack(
                                    navController.graph.startDestinationRoute!!,
                                    inclusive = true
                                )
                                navController.navigate("default")
                            }
                        )
                    }

                    composable("login2/init") { InitScreen(navController, windowSizeClass) }

                    // This is only used outside of Polar mode
                    // Otherwise you may be looking for "main" right below
                    composable(
                        "chat",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(
                                    400,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialOffset = { it / 3 }
                            ) + fadeIn(animationSpec = StoatTweenFloat)
                        }
                    ) {
                        ChatRouterScreen(
                            navController,
                            windowSizeClass,
                            disableBackHandler = showVoiceUI,
                            onNullifiedUser = {
                                onRetryConnection()
                                navController.popBackStack(
                                    navController.graph.startDestinationRoute!!,
                                    inclusive = true
                                )
                                navController.navigate("default")
                            },
                            onEnterVoiceUI = { channelId ->
                                showVoiceUI = true
                                voiceChannelId = channelId
                            },
                        )
                    }

                    // This is only the main screen in Polar mode
                    // Otherwise you may be looking for "chat" right above
                    composable(
                        "main",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(
                                    400,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialOffset = { it / 3 }
                            ) + fadeIn(animationSpec = StoatTweenFloat) + scaleIn(
                                animationSpec = tween(
                                    400,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialScale = 0.8f,
                                transformOrigin = TransformOrigin.Center
                            )
                        }
                    ) {
                        MainScreen(navController)
                    }
                    composable(
                        "main/conversation/{channelId}",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(
                                    600,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                initialOffset = { it }
                            ) + fadeIn(animationSpec = StoatTweenFloat)
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(
                                    600,
                                    easing = EasingTokens.EmphasizedDecelerate
                                ),
                                targetOffset = { it }
                            ) + fadeOut(animationSpec = StoatTweenFloat)
                        }
                    ) { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelScreen(
                            channelId = channelId,
                            onToggleDrawer = {},
                            useDrawer = false,
                            useBackButton = true,
                            backButtonAction = {
                                navController.popBackStack()
                            },
                            useChatUI = true
                        )
                    }

                    composable("catchup") { CatchUpScreen(navController) }

                    composable("create/group") { CreateGroupScreen(navController) }

                    composable("discover") { DiscoverScreen(navController) }

                    composable("settings") { SettingsScreen(navController) }
                    composable("settings/profile") { ProfileSettingsScreen(navController) }
                    composable("settings/sessions") { SessionManagementScreen(navController) }
                    composable("settings/appearance") { AppearanceSettingsScreen(navController) }
                    composable("settings/chat") { ChatSettingsScreen(navController) }
                    composable("settings/debug") { DebugSettingsScreen(navController) }
                    composable("settings/experiments") { ExperimentsSettingsScreen(navController) }
                    composable("settings/changelogs") { ChangelogsSettingsScreen(navController) }
                    composable("settings/language") { LanguagePickerSettingsScreen(navController) }
                    composable("settings/notifications") { NotificationSettingsScreen(navController) }
                    composable("settings/account") { AccountSettingsScreen(navController) }
                    composable("settings/mfa") { MfaSetupScreen(navController) }
                    composable("settings/bots") { BotManagementScreen(navController) }
                    composable("settings/instance") { com.tribixbite.stoatally.screens.settings.InstanceSettingsScreen(navController) }

                    composable(
                        "search/{channelId}?pinned={pinned}",
                        arguments = listOf(
                            navArgument("channelId") { type = NavType.StringType },
                            navArgument("pinned") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        val initialPinnedOnly = backStackEntry.arguments?.getBoolean("pinned") ?: false
                        MessageSearchScreen(
                            channelId = channelId,
                            navController = navController,
                            initialPinnedOnly = initialPinnedOnly
                        )
                    }

                    composable("search/server/{serverId}") { backStackEntry ->
                        val sId = backStackEntry.arguments?.getString("serverId") ?: ""
                        MessageSearchScreen(channelId = "", serverId = sId, navController = navController)
                    }

                    composable("settings/channel/{channelId}") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsHome(navController, channelId)
                    }
                    composable("settings/channel/{channelId}/overview") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsOverview(navController, channelId)
                    }
                    composable("settings/channel/{channelId}/permissions") { backStackEntry ->
                        val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                        ChannelSettingsPermissions(navController, channelId)
                    }

                    // Server settings screens
                    composable("settings/server/{serverId}") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        ServerSettingsScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/roles") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        RoleManagementScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/bans") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        BanManagementScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/create-channel") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        CreateChannelScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/import-discord") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        DiscordImportScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/bridge-settings") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        BridgeSettingsScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/invites") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        InviteManagementScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/emojis") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        EmojiManagementScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/permissions/default") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        DefaultPermissionsEditorScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/roles/{roleId}/permissions") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        val roleId = backStackEntry.arguments?.getString("roleId") ?: ""
                        RolePermissionsEditorScreen(navController, serverId, roleId)
                    }
                    composable("settings/server/{serverId}/system-messages") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        SystemMessagesScreen(navController, serverId)
                    }
                    composable("settings/server/{serverId}/webhooks") { backStackEntry ->
                        val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                        WebhookManagementScreen(navController, serverId)
                    }

                    composable("about") { AboutScreen(navController) }
                    composable("about/oss") { AttributionScreen(navController) }

                    composable("labs") { LabsRootScreen(navController) }
                }
            }

            if (showVoiceUI) { // if tapped outside the voice UI, close it
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showVoiceUI = false
                        }
                )
            }

            AnimatedVisibility(
                visible = showVoiceUI,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it -> it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = EasingTokens.EmphasizedDecelerate
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it -> it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = EasingTokens.EmphasizedDecelerate
                    )
                )
            ) {
                // We need a box as applying the padding elsewhere leads to either
                // janky animation or layout
                Box(Modifier.safeDrawingPadding()) {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(8.dp)
                    ) {
                        VoicePermissionSwitch(
                            onCancel = {
                                showVoiceUI = false
                            }
                        ) {
                            voiceChannelId?.let {
                                VoiceSheet(
                                    it,
                                    onDisconnect = {
                                        showVoiceUI = false
                                        voiceChannelId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
