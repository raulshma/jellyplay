package com.raulshma.jellyplay.feature.admin.plugins

import android.webkit.JavascriptInterface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Native counterpart of the JavaScript objects injected by `pluginBridge.js`.
 *
 * Plugin config pages call back into the native side via `window.NativeInterface`
 * for UI feedback (save confirmation, loading overlay, alerts). Data calls
 * (load/save configuration, images) stay on the JS side as standard same-origin
 * fetches; the host attaches the `X-Emby-Token` header to those via
 * [android.webkit.WebViewClient.shouldInterceptRequest].
 *
 * Every method is called on the WebView's JavaBridge thread, so state mutations
 * go through Compose snapshot state (thread-safe).
 */
class PluginConfigJsBridge {
    /** Mirrors the plugin's `Dashboard.showLoadingMsg()/hideLoadingMsg()` calls. */
    var isLoadingOverlay by mutableStateOf(false)
        private set

    /** Latest one-shot event emitted by the page, surfaced as a snackbar by the screen. */
    var pendingEvent by mutableStateOf<JsBridgeEvent?>(null)
        private set

    fun reset() {
        isLoadingOverlay = false
        pendingEvent = null
    }

    @JavascriptInterface
    fun onLoading(show: Boolean) {
        isLoadingOverlay = show
    }

    @JavascriptInterface
    fun onConfigSaved() {
        isLoadingOverlay = false
        pendingEvent = JsBridgeEvent.Saved
    }

    @JavascriptInterface
    fun onConfigError(message: String) {
        isLoadingOverlay = false
        pendingEvent = JsBridgeEvent.Error(message)
    }

    @JavascriptInterface
    fun onAlert(message: String) {
        pendingEvent = JsBridgeEvent.Alert(message)
    }

    @JavascriptInterface
    fun onConfirm(message: String, title: String) {
        pendingEvent = JsBridgeEvent.Confirm(message, title)
    }

    @JavascriptInterface
    fun onNavigate(url: String) {
        pendingEvent = JsBridgeEvent.Navigate(url)
    }

    /** Consumes and returns the current event, clearing it. Call from a LaunchedEffect. */
    fun consumeEvent(): JsBridgeEvent? {
        val event = pendingEvent
        pendingEvent = null
        return event
    }

    sealed interface JsBridgeEvent {
        data object Saved : JsBridgeEvent
        data class Error(val message: String) : JsBridgeEvent
        data class Alert(val message: String) : JsBridgeEvent
        data class Confirm(val message: String, val title: String) : JsBridgeEvent
        data class Navigate(val url: String) : JsBridgeEvent
    }
}
