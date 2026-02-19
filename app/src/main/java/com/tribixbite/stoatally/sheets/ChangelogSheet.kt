package com.tribixbite.stoatally.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tribixbite.stoatally.api.internals.getFragmentActivity
import com.tribixbite.stoatally.fragments.ChangelogBottomSheetFragment

@Composable
fun ChangelogSheet(
    versionName: String,
    versionIsHistorical: Boolean,
    renderedContents: String,
    onDismiss: () -> Unit
) {
    val activity = LocalContext.current.getFragmentActivity()

    var lastRenderedContents by remember { mutableStateOf("") }

    DisposableEffect(versionName, renderedContents) {
        if (lastRenderedContents == renderedContents) return@DisposableEffect onDispose {}
        if (renderedContents.isEmpty()) return@DisposableEffect onDispose {}
        
        lastRenderedContents = renderedContents

        val sheet = ChangelogBottomSheetFragment(onDismiss)
        sheet.arguments =
            ChangelogBottomSheetFragment.createArguments(
                versionName,
                versionIsHistorical,
                renderedContents,
            )

        // Use commitAllowingStateLoss to avoid crash when activity is in background
        activity?.supportFragmentManager?.let { fm ->
            fm.beginTransaction()
                .add(sheet, ChangelogBottomSheetFragment.TAG)
                .commitAllowingStateLoss()
        }

        onDispose {
            try {
                sheet.dismissAllowingStateLoss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}