package com.tribixbite.stoatally.composables.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tribixbite.stoatally.api.settings.Experiments
import com.tribixbite.stoatally.markdown.jbm.JBM
import com.tribixbite.stoatally.markdown.jbm.JBMRenderer
import com.tribixbite.stoatally.ndk.NativeLibraries
import com.tribixbite.stoatally.ndk.Stendal

@OptIn(JBM::class)
@Composable
fun RichMarkdown(input: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (!NativeLibraries.stendalAvailable || Experiments.useKotlinBasedMarkdownRenderer.isEnabled) {
            // Fall back to Kotlin-based renderer when stendal native lib is unavailable
            JBMRenderer(input)
        } else {
            MarkdownTree(node = Stendal.render(input))
        }
    }
}