package com.tribixbite.stoatally.screens.labs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tribixbite.stoatally.api.settings.FeatureFlags
import com.tribixbite.stoatally.screens.labs.ui.mockups.NewLoginExperienceMockup
import com.tribixbite.stoatally.screens.labs.ui.sandbox.FinalMarkdownSandbox
import com.tribixbite.stoatally.screens.labs.ui.sandbox.GradientEditorSandbox
import com.tribixbite.stoatally.screens.labs.ui.sandbox.JBMSandbox
import com.tribixbite.stoatally.screens.labs.ui.sandbox.NewCardSandboxScreen
import com.tribixbite.stoatally.screens.labs.ui.sandbox.SettingsDslSandbox
import com.tribixbite.stoatally.screens.labs.ui.sandbox.TelecomSandbox

annotation class LabsFeature

@Composable
fun LabsGuard(onTurnBack: () -> Unit = {}, content: @Composable () -> Unit) {
    if (!FeatureFlags.labsAccessControlGranted) {
        AlertDialog(
            onDismissRequest = { onTurnBack() },
            confirmButton = {
                TextButton(onClick = { onTurnBack() }) {
                    Text("Turn back")
                }
            },
            title = {
                Text("You don't have access to Labs.")
            },
            text = {
                Text("Labs is where we test new features. However, these features may be unstable and may not work as expected. Hence, access to Labs is restricted.")
            }
        )
    } else {
        content()
    }
}

@Composable
fun LabsRootScreen(topNav: NavController) {
    val labsNav = rememberNavController()

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LabsGuard(
            onTurnBack = {
                topNav.popBackStack()
            }
        ) {
            NavHost(
                navController = labsNav,
                startDestination = "home",
            ) {
                composable("home") {
                    LabsHomeScreen(labsNav, topNav)
                }

                composable("mockups/newlogin") {
                    NewLoginExperienceMockup(labsNav)
                }

                composable("sandboxes/settingsdsl") {
                    SettingsDslSandbox(labsNav)
                }
                composable("sandboxes/jbm") {
                    JBMSandbox(labsNav)
                }
                composable("sandboxes/finalmarkdown") {
                    FinalMarkdownSandbox(labsNav)
                }
                composable("sandboxes/gradienteditor") {
                    GradientEditorSandbox(labsNav)
                }
                composable("sandboxes/newcard") {
                    NewCardSandboxScreen(labsNav)
                }
                composable("sandboxes/telecom") {
                    TelecomSandbox(labsNav)
                }
            }
        }
    }
}