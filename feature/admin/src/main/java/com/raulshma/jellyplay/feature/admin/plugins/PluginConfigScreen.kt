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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
fun PluginConfigScreen(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
    viewModel: PluginConfigViewModel = hiltViewModel(),
) {
    viewModel.initialize(pluginId, pluginName)
    val state = viewModel.state
    val backgroundColor = rememberScreenBackgroundColor()
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
        title = stringResource(R.string.admin_plugin_settings_title, pluginName),
        onBack = onBack,
        backgroundColor = backgroundColor,
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
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(R.string.admin_refresh))
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
                        state.error ?: stringResource(R.string.admin_failed_load_config),
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

private fun colorToHex(color: androidx.compose.ui.graphics.Color): String {
    val argb = color.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02x%02x%02x".format(r, g, b)
}

private fun buildPicoOverrides(isDark: Boolean, colors: androidx.compose.material3.ColorScheme): String {
    val bg = colorToHex(colors.background)
    val onBg = colorToHex(colors.onBackground)
    val surface = colorToHex(colors.surface)
    val surfaceVariant = colorToHex(colors.surfaceVariant)
    val onSurfaceVariant = colorToHex(colors.onSurfaceVariant)
    val outline = colorToHex(colors.outline)
    val outlineVariant = colorToHex(colors.outlineVariant)
    val primary = colorToHex(colors.primary)
    val primaryContainer = colorToHex(colors.primaryContainer)
    val onPrimary = colorToHex(colors.onPrimary)
    val onPrimaryContainer = colorToHex(colors.onPrimaryContainer)
    val secondary = colorToHex(colors.secondary)
    val secondaryContainer = colorToHex(colors.secondaryContainer)
    val error = colorToHex(colors.error)

    return """
        :root {
          --pico-background-color: $bg;
          --pico-color: $onBg;
          --pico-primary: $primary;
          --pico-primary-background: $primary;
          --pico-primary-border: $primary;
          --pico-primary-hover: $onPrimaryContainer;
          --pico-primary-hover-background: $primaryContainer;
          --pico-primary-hover-border: $primaryContainer;
          --pico-primary-inverse: $onPrimary;
          --pico-primary-underline: ${primary}80;
          --pico-primary-focus: ${primary}60;
          --pico-muted-color: $onSurfaceVariant;
          --pico-muted-border-color: $outlineVariant;
          --pico-secondary: $secondary;
          --pico-secondary-background: $secondary;
          --pico-secondary-hover: $secondaryContainer;
          --pico-secondary-inverse: $onPrimary;
          --pico-contrast: $onBg;
          --pico-contrast-background: $surfaceVariant;
          --pico-contrast-inverse: $onBg;
          --pico-h1-color: $onBg;
          --pico-h2-color: $onBg;
          --pico-h3-color: $onBg;
          --pico-h4-color: $onBg;
          --pico-h5-color: $onBg;
          --pico-h6-color: $onBg;
          --pico-table-border-color: $outlineVariant;
          --pico-table-row-stripped-background-color: ${surfaceVariant}18;
          --pico-card-background-color: $surface;
          --pico-card-border-color: $outlineVariant;
          --pico-card-sectioning-background-color: $surfaceVariant;
          --pico-form-element-background-color: $surface;
          --pico-form-element-border-color: $outline;
          --pico-form-element-color: $onBg;
          --pico-form-element-placeholder-color: $onSurfaceVariant;
          --pico-form-element-active-background-color: $surfaceVariant;
          --pico-form-element-active-border-color: $primary;
          --pico-form-element-focus-color: $primary;
          --pico-code-background-color: $surfaceVariant;
          --pico-code-color: $onSurfaceVariant;
          --pico-blockquote-border-color: $outlineVariant;
          --pico-blockquote-footer-color: $onSurfaceVariant;
          --pico-mark-background-color: $primaryContainer;
          --pico-mark-color: $onPrimaryContainer;
          --pico-ins-color: ${if (isDark) "#62af9a" else "#1c6954"};
          --pico-del-color: $error;
          --pico-progress-background-color: $surfaceVariant;
          --pico-progress-color: $primary;
          --pico-accordion-border-color: $outlineVariant;
          --pico-accordion-active-summary-color: $primary;
          --pico-switch-background-color: $outline;
          --pico-switch-checked-background-color: $primary;
          --pico-switch-color: $onPrimary;
          --pico-dropdown-background-color: $surface;
          --pico-dropdown-border-color: $outlineVariant;
          --pico-dropdown-color: $onBg;
          --pico-dropdown-hover-background-color: $surfaceVariant;
          --pico-modal-overlay-background-color: ${bg}BF;
          --pico-tooltip-background-color: $onBg;
          --pico-tooltip-color: $bg;
          --pico-border-radius: 0.75rem;
        }
    """.trimIndent()
}

/**
 * Wraps the plugin's HTML so that the bridge script and Pico CSS theme load
 * before the page's own scripts. The [bridgeScript] is injected at the very top
 * of `<head>` so `window.ApiClient`/`window.Dashboard` are defined before any
 * inline `<script>` runs (Pattern-A pages read these globals at load time).
 */
private fun buildWrappedHtml(
    originalHtml: String,
    theme: String,
    overrides: String,
    bridgeScript: String,
): String {
    val picoStylesheet = """
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css">
        <style>$overrides</style>
    """.trimIndent()

    val hasHead = originalHtml.contains("<head", ignoreCase = true)
    val hasBody = originalHtml.contains("<body", ignoreCase = true)
    val hasHtml = originalHtml.contains("<html", ignoreCase = true)

    if (hasHtml) {
        return originalHtml
            .replace(
                Regex("(<html[^>]*?)>", RegexOption.IGNORE_CASE),
                "$1 data-theme=\"$theme\">",
            )
            .replace(
                Regex("(<head[^>]*?>)", RegexOption.IGNORE_CASE),
                "$1<script>$bridgeScript</script>",
            )
            .replace(
                Regex("(</head>)", RegexOption.IGNORE_CASE),
                "$picoStylesheet\n</head>",
            )
    }

    val picoHead = """
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="light dark">
        <script>$bridgeScript</script>
        $picoStylesheet
    """.trimIndent()

    if (hasHead && hasBody) {
        return originalHtml.replace(
            Regex("(<head[^>]*?>)", RegexOption.IGNORE_CASE),
            "$1$picoHead\n",
        )
    }

    if (hasBody) {
        return originalHtml.replace(
            Regex("(<body[^>]*>)", RegexOption.IGNORE_CASE),
            "$1\n$picoHead",
        )
    }

    return """<!DOCTYPE html>
        |<html lang="en" data-theme="$theme">
        |<head>$picoHead</head>
        |<body><main class="container">$originalHtml</main></body>
        |</html>""".trimMargin()
}
