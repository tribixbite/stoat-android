package com.tribixbite.stoatally.composables.screens.services

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tribixbite.stoatally.activities.InviteActivity
import com.tribixbite.stoatally.api.STOAT_INVITES
import com.tribixbite.stoatally.api.STOAT_WEB_APP
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.buildUserAgent
import com.tribixbite.stoatally.api.internals.ThemeCompat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ColumnScope.DiscoverView() {
    var showPlaceholder by remember { mutableStateOf(true) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (showPlaceholder) 1f else 0f,
        label = "discoverViewPlaceholderAlpha"
    )
    val themeMap = ThemeCompat.materialThemeAsDiscoverTheme(MaterialTheme)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = buildUserAgent("DiscoverView")
                    settings.setSupportZoom(false)
                    settings.setSupportMultipleWindows(false)
                    loadUrl("$STOAT_INVITES/discover/servers?embedded=true")

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val themeMessage = JsonObject(
                                mapOf(
                                    "source" to JsonPrimitive("revolt"),
                                    "type" to JsonPrimitive("theme"),
                                    "theme" to themeMap
                                )
                            )
                            val themeMessageString =
                                StoatJson.encodeToString(JsonObject.serializer(), themeMessage)

                            evaluateJavascript(
                                """
                                    window.postMessage($themeMessageString, "*")
                                    window.addEventListener("message", event => {
                                        try {
                                            const data = JSON.parse(event.data)
                                            if (data.source === "discover") {
                                                switch (data.type) {
                                                    case "navigate":
                                                        // Cheap debounce
                                                        if (Date.now() - window.lastNavigateEvent < 500) {
                                                            return
                                                        }
                                                        window.lastNavigateEvent = Date.now()
                                                        window.location.href = data.url
                                                        break
                                                }
                                            }
                                        } catch(e) {}
                                    })
                                """.trimIndent(),
                                null
                            )

                            postDelayed({
                                showPlaceholder = false
                            }, 1000) // to prevent flickering
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return true
                            val host = url.host ?: return true
                            val path = url.path ?: ""
                            // All known Revolt/Stoat invite and app domains
                            val inviteHosts = setOf(
                                Uri.parse(STOAT_WEB_APP).host,  // stoat.chat
                                Uri.parse(STOAT_INVITES).host,  // stt.gg
                                "rvlt.gg",
                                "app.revolt.chat"               // discover page links here
                            )

                            if (host in inviteHosts) {
                                // Let discover page tab navigation stay in WebView
                                // (servers/bots/themes tabs)
                                if (path.startsWith("/discover")) {
                                    return false
                                }

                                // Route invite/bot/server URLs to InviteActivity
                                val intent = Intent(
                                    context,
                                    InviteActivity::class.java
                                ).setAction(Intent.ACTION_VIEW)
                                intent.data = url
                                context.startActivity(intent)
                                return true
                            }

                            // Block all other external URLs
                            return true
                        }
                    }
                }
            },
            update = {
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f - animatedAlpha)
        )

        CircularProgressIndicator(
            modifier = Modifier
                .size(48.dp)
                .alpha(animatedAlpha)
        )
    }
}