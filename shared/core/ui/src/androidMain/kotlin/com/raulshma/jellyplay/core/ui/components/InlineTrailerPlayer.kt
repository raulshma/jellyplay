package com.raulshma.jellyplay.core.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InlineTrailerPlayer(
    videoKey: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    showControls: Boolean = true,
    autoplay: Boolean = true,
    focusable: Boolean = true,
    cropToFill: Boolean = false,
    onEmbedFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Build the HTML page with CSS to force the YouTube iframe to stretch to cover/fit the viewport
    val htmlContent = remember(videoKey, muted, showControls, autoplay, cropToFill) {
        val autoplayVal = if (autoplay) 1 else 0
        val muteVal = if (muted) 1 else 0
        val controlsVal = if (showControls) 1 else 0
        val pointerEvents = if (showControls) "auto" else "none"
        val playerStyle = if (cropToFill) {
            """
            #player {
                position: absolute;
                top: 50%;
                left: 50%;
                width: 100vw;
                height: 56.25vw; /* 16:9 ratio */
                min-height: 100vh;
                min-width: 177.77vh; /* 16:9 ratio */
                transform: translate(-50%, -50%)${if (!showControls) " scale(1.25)" else ""};
                border: none;
                pointer-events: $pointerEvents;
                opacity: 0;
                transition: opacity 1.0s ease-in-out;
            }
            #player.visible {
                opacity: 1;
            }
            """.trimIndent()
        } else {
            """
            #player {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                transform: ${if (!showControls) "scale(1.25)" else "none"};
                border: none;
                pointer-events: $pointerEvents;
                opacity: 0;
                transition: opacity 1.0s ease-in-out;
            }
            #player.visible {
                opacity: 1;
            }
            """.trimIndent()
        }
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
            *{margin:0;padding:0;overflow:hidden}
            html,body{width:100%;height:100%;background:transparent}
            .video-container {
                position: relative;
                width: 100%;
                height: 100%;
                overflow: hidden;
            }
            $playerStyle
        </style>
        </head>
        <body>
        <div class="video-container">
            <div id="player"></div>
        </div>
        <script>
            // Load the YouTube IFrame API asynchronously
            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            var player;
            function onYouTubeIframeAPIReady() {
                player = new YT.Player('player', {
                    height: '100%',
                    width: '100%',
                    videoId: '$videoKey',
                    playerVars: {
                        'autoplay': $autoplayVal,
                        'mute': $muteVal,
                        'controls': $controlsVal,
                        'loop': 1,
                        'playlist': '$videoKey',
                        'playsinline': 1,
                        'enablejsapi': 1,
                        'showinfo': 0,
                        'rel': 0,
                        'iv_load_policy': 3,
                        'modestbranding': 1,
                        'disablekb': 1,
                        'fs': 0
                    },
                    events: {
                        'onReady': onPlayerReady,
                        'onStateChange': onPlayerStateChange
                    }
                });
            }

            function onPlayerReady(event) {
                if ($muteVal === 1) {
                    event.target.mute();
                }
                if ($autoplayVal === 1) {
                    event.target.playVideo();
                }
            }

            function onPlayerStateChange(event) {
                if (event.data === 1) { // YT.PlayerState.PLAYING
                    var p = document.getElementById('player');
                    if (p) p.classList.add('visible');
                } else if (event.data === 0) { // YT.PlayerState.ENDED
                    event.target.playVideo();
                }
            }

            // Safety timeout: if player has not loaded in 5 seconds, force it to be visible
            setTimeout(function() {
                var p = document.getElementById('player');
                if (p && !p.classList.contains('visible')) {
                    p.classList.add('visible');
                }
            }, 5000);

            // Interface functions for Android WebView lifecycle management
            function pausePlayer() {
                if (player && typeof player.pauseVideo === 'function') {
                    player.pauseVideo();
                }
            }

            function playPlayer() {
                if (player && typeof player.playVideo === 'function') {
                    player.playVideo();
                }
            }
        </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Handle full-screen rendering
    customView?.let { view ->
        Dialog(
            onDismissRequest = {
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = {
                        // Remove from parent if already attached
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Nearly transparent so the backdrop image shows through until video renders
                setBackgroundColor(android.graphics.Color.argb(1, 0, 0, 0))
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // Emulate a standard Chrome browser to bypass WebView autoplay restrictions
                    userAgentString = userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        android.util.Log.e(
                            "InlineTrailerPlayer", 
                            "WebView error: ${error?.description} code: ${error?.errorCode} for url: ${request?.url}"
                        )
                        if (request?.isForMainFrame == true) {
                            onEmbedFailed()
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            android.util.Log.d(
                                "InlineTrailerPlayer", 
                                "CONSOLE [${it.messageLevel()}]: ${it.message()} at ${it.sourceId()}:${it.lineNumber()}"
                            )
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }

                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        super.onShowCustomView(view, callback)
                        if (view != null) {
                            customView = view
                            customViewCallback = callback
                        }
                    }

                    override fun onHideCustomView() {
                        super.onHideCustomView()
                        customView = null
                        customViewCallback = null
                    }
                }

                webViewRef = this
            }
        },
        modifier = modifier.focusProperties { canFocus = focusable },
        update = { webView ->
            val currentHtml = webView.tag as? String
            if (currentHtml != htmlContent) {
                webView.loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
                webView.tag = htmlContent
            }
        }
    )

    // Manage Lifecycle events (pause/resume video when app state changes)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val webView = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    webView.evaluateJavascript(
                        "if (typeof pausePlayer === 'function') { pausePlayer(); } else { var iframe = document.querySelector('iframe'); if(iframe) { iframe.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":\"\"}', '*'); } }", 
                        null
                    )
                }
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.evaluateJavascript(
                        "if (typeof playPlayer === 'function') { playPlayer(); } else { var iframe = document.querySelector('iframe'); if(iframe) { iframe.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"playVideo\",\"args\":\"\"}', '*'); } }", 
                        null
                    )
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                // Destroy the native renderer/V8/GPU textures. Detaching + loadUrl("about:blank")
                // alone leaves the Chromium renderer process alive; destroy() is required to
                // reclaim it.
                webView.destroy()
            }
        }
    }
}
