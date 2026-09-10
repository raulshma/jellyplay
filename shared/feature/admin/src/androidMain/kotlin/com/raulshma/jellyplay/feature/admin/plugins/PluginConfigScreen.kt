package com.raulshma.jellyplay.feature.admin.plugins

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_failed_load_config
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_plugin_settings_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_refresh
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.feature.admin.users.detail.asText
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
fun PluginConfigScreen(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
    viewModel: PluginConfigViewModel = koinViewModel(),
) {
    viewModel.initialize(pluginId, pluginName)
    val state = viewModel.state
    val backgroundColorState = rememberScreenBackgroundColorState()
    val isDark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val userMessageBus = LocalUserMessageBus.current
    val jsBridge = remember { PluginConfigJsBridge() }

    // The bridge publishes events on the WebView's JavaBridge thread via Compose
    // snapshot state (mutableStateOf). derivedStateOf re-snapshots here whenever
    // pendingEvent changes, and the LaunchedEffect forwards each one to the
    // user-message bus (rendered as a snackbar by the root host) and consumes it.
    val latestEvent by remember(jsBridge) {
        androidx.compose.runtime.derivedStateOf { jsBridge.pendingEvent }
    }
    LaunchedEffect(latestEvent) {
        val event = latestEvent ?: return@LaunchedEffect
        when (event) {
            is PluginConfigJsBridge.JsBridgeEvent.Saved ->
                userMessageBus.info(UiText.Raw("Settings saved"))
            is PluginConfigJsBridge.JsBridgeEvent.Error ->
                userMessageBus.error(UiText.Raw(event.message))
            is PluginConfigJsBridge.JsBridgeEvent.Alert ->
                userMessageBus.info(UiText.Raw(event.message))
            is PluginConfigJsBridge.JsBridgeEvent.Confirm,
            is PluginConfigJsBridge.JsBridgeEvent.Navigate -> Unit
        }
        jsBridge.consumeEvent()
    }

    // WebView content is not D-pad reachable (platform limitation), so on TV the
    // refresh action is the screen's sole focusable — anchor initial focus there
    // once it becomes enabled (it is disabled while the config page loads).
    val refreshFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = refreshFocusRequester,
        itemCount = if (state.isLoading) 0 else 1,
        tag = "plugin_config_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.admin_plugin_settings_title, pluginName),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            val refreshFocusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !state.isLoading,
                modifier = Modifier
                    .focusRequester(refreshFocusRequester)
                    .then(refreshFocusState.focusModifier)
                    .tvFocusIndicator(refreshFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(Res.string.admin_refresh))
            }
        },
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.error?.asText() ?: stringResource(Res.string.admin_failed_load_config),
                        color = colors.error,
                    )
                }
            }
            state.configPageHtml != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    PicoWebView(
                        htmlContent = state.configPageHtml,
                        bridgeScript = state.bridgeScript,
                        serverAddress = state.serverAddress,
                        accessToken = state.accessToken,
                        okHttpClient = viewModel.okHttpClient,
                        isDark = isDark,
                        colors = colors,
                        jsBridge = jsBridge,
                    )
                    // Native loading overlay driven by Dashboard.show/hideLoadingMsg().
                    AnimatedVisibility(visible = jsBridge.isLoadingOverlay) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PicoWebView(
    htmlContent: String,
    bridgeScript: String,
    serverAddress: String,
    accessToken: String,
    okHttpClient: okhttp3.OkHttpClient,
    isDark: Boolean,
    colors: androidx.compose.material3.ColorScheme,
    jsBridge: PluginConfigJsBridge,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val picoTheme = if (isDark) "dark" else "light"
    val themeOverrides = remember(isDark, colors) { buildPicoOverrides(isDark, colors) }

    val wrappedHtml = remember(htmlContent, picoTheme, themeOverrides, bridgeScript) {
        buildWrappedHtml(htmlContent, picoTheme, themeOverrides, bridgeScript)
    }

    // Reset bridge state when the page content changes.
    LaunchedEffect(htmlContent) { jsBridge.reset() }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    // Allow plugin pages to load authenticated same-origin resources.
                    addJavascriptInterface(jsBridge, "NativeInterface")
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            // Backstop pageshow dispatch in case the bridge's own
                            // readyState hook didn't catch the page.
                            view?.evaluateJavascript(
                                "if(window.__jellyplayFirePageShow){try{window.__jellyplayFirePageShow();}catch(e){}}",
                                null,
                            )
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            Log.e("PluginConfigWebView", "WebView error: ${error?.description}")
                            // Only treat main-frame errors as "done loading".
                            if (request?.isForMainFrame == true) isLoading = false
                        }

                        // Attach the X-Emby-Token header to every same-origin request so
                        // in-page fetches (config load/save), images, and controller JS
                        // authenticate against the Jellyfin server.
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            request ?: return null
                            return interceptAuthedRequest(
                                request = request,
                                okHttpClient = okHttpClient,
                                accessToken = accessToken,
                                serverAddress = serverAddress,
                            )
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // Plugin pages navigate via Dashboard.navigate(); let the bridge
                            // handle internal links rather than the WebView itself.
                            return false
                        }
                    }
                    webView = this
                    loadDataWithBaseURL(
                        serverAddress,
                        wrappedHtml,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center),
            )
        }
    }
}

// The pure halves this screen used to carry — colorToHex, buildPicoOverrides
// and buildWrappedHtml — live in commonMain beside PluginBridgeScript.kt
// (`PicoConfigHtml.kt`, pinned by PicoConfigHtmlTest); this file keeps only
// the Android WebView wiring.
