package com.raulshma.jellyplay.feature.book.epub

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

/**
 * Android EPUB host: a stock `android.webkit.WebView` rendering the inlined
 * reader page over `https://jellyplay.local` (an HTTPS base URL avoids
 * file:// access entirely). JS → native rides `addJavascriptInterface`
 * (re-posted onto the main thread before [dispatchEpubEvents] runs — the
 * bridge thread must never touch the WebView or Compose state); appearance
 * pushes and commands ride `evaluateJavascript`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    callbacks: EpubReaderCallbacks,
): EpubReaderHandle {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val callbacksRef = rememberUpdatedState(callbacks)
    // Events re-posted onto the main thread: `addJavascriptInterface` methods
    // run on the WebView's private JavaBridge thread, but the tap callbacks
    // turn around and drive the host handle (evaluateJavascript — UI-thread
    // only) and Compose screen state. Dispatching here keeps every callback
    // on the thread the screen was written for.
    val bridge = remember {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        EpubAndroidBridge { raw -> mainHandler.post { dispatchEpubEvents(raw, callbacksRef.value) } }
    }

    var bookBase64 by remember { mutableStateOf<String?>(null) }
    var pageHtml by remember { mutableStateOf<String?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var bookSent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pageHtml = EpubReaderHtml.build()
    }
    LaunchedEffect(bookFile) {
        bookBase64 = withContext(Dispatchers.IO) { EpubBookCodec.encodeBase64(bookFile) }
    }
    // Load the page as soon as the built HTML and the WebView exist; its
    // onPageFinished hook flips `pageLoaded`, which gates every
    // evaluateJavascript below (a script before page-finished is a no-op).
    LaunchedEffect(pageHtml, webViewRef.value) {
        val html = pageHtml ?: return@LaunchedEffect
        val view = webViewRef.value ?: return@LaunchedEffect
        view.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
    }
    // The book rides the chunked loadBookBegin/loadBookChunk/loadBookEnd
    // protocol once the page's JS is reachable (see BOOK_CHUNK_CHARS).
    LaunchedEffect(pageLoaded, bookBase64) {
        val base64 = bookBase64 ?: return@LaunchedEffect
        val view = webViewRef.value ?: return@LaunchedEffect
        if (!pageLoaded || bookSent) return@LaunchedEffect
        // Flipped only once the view is in hand — marking it sent on a path
        // that drops the send would strand the reader on the boot veil.
        bookSent = true
        sendBookChunks(base64, resumePercent, appearance) { view.evaluateJavascript(it, null) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                // The page is self-contained; no local file/content access.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(bridge, HOST_BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        pageLoaded = true
                    }
                }
                webViewRef.value = this
            }
        },
        onRelease = { view ->
            if (webViewRef.value === view) webViewRef.value = null
            view.destroy()
        },
    )

    // Appearance push for CHANGES after load — see pushAppearanceScripts.
    LaunchedEffect(pageLoaded, bookSent, appearance) {
        if (!pageLoaded || !bookSent) return@LaunchedEffect
        webViewRef.value?.let { view ->
            pushAppearanceScripts(appearance) { view.evaluateJavascript(it, null) }
        }
    }

    return remember {
        object : EpubReaderHandle {
            // The system WebView ships with the app — there is never a
            // runtime viewer download to report (desktop's KCEF only).
            override val viewerDownloadProgress: State<Float?>
                get() = neverDownloads

            override fun next() {
                webViewRef.value?.evaluateJavascript(buildNextScript(), null)
            }

            override fun prev() {
                webViewRef.value?.evaluateJavascript(buildPrevScript(), null)
            }

            override fun goTo(href: String) {
                webViewRef.value?.evaluateJavascript(buildGoToScript(href), null)
            }

            override fun goToCfi(cfi: String) {
                webViewRef.value?.evaluateJavascript(buildGoToCfiScript(cfi), null)
            }

            override fun setFlow(scrolled: Boolean) {
                webViewRef.value?.evaluateJavascript(buildSetFlowScript(scrolled), null)
            }

            override fun applyAnnotations(entries: List<EpubAnnotationSpec>) {
                webViewRef.value?.evaluateJavascript(buildApplyAnnotationsScript(entries), null)
            }

            override fun addAnnotation(entry: EpubAnnotationSpec) {
                webViewRef.value?.evaluateJavascript(buildAddAnnotationScript(entry), null)
            }

            override fun removeAnnotation(cfi: String) {
                webViewRef.value?.evaluateJavascript(buildRemoveAnnotationScript(cfi), null)
            }

            override fun clearSelection() {
                webViewRef.value?.evaluateJavascript(buildClearSelectionScript(), null)
            }

            override fun search(query: String, token: Int) {
                webViewRef.value?.evaluateJavascript(buildSearchScript(query, token), null)
            }

            override fun requestSpeechContext(cfi: String?) {
                webViewRef.value?.evaluateJavascript(buildSpeechContextScript(cfi), null)
            }

            override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) {
                webViewRef.value?.evaluateJavascript(buildSetAutoScrollScript(enabled, pxPerSec), null)
            }
        }
    }
}

/**
 * Trust boundary: the native bridge is reachable from book-supplied scripts
 * (EPUBs may embed JS). It can only report reader events (percent/toc/status)
 * — no file, cookie, or credential surface — so a hostile book can at worst
 * lie about reading progress on its own item.
 */
/** JS bridge object the page calls as `window.hostBridge.post(json)`. */
private class EpubAndroidBridge(private val onEvent: (String) -> Unit) {
    @JavascriptInterface
    fun post(json: String?) {
        if (!json.isNullOrBlank()) onEvent(json)
    }
}

/** Android never downloads a viewer — the progress State is permanently null. */
private val neverDownloads = mutableStateOf<Float?>(null)

private const val BASE_URL = "https://jellyplay.local/"
private const val HOST_BRIDGE_NAME = "hostBridge"
