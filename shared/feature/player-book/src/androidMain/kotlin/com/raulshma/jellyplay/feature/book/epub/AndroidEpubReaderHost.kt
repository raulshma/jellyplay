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
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

/**
 * Android EPUB host: a stock `android.webkit.WebView` rendering the inlined
 * reader page over `https://jellyplay.local` (an HTTPS base URL avoids
 * file:// access entirely). JS → native rides `addJavascriptInterface`
 * (parsed and re-posted onto the main thread before the event seam runs —
 * the bridge thread must never touch the WebView or Compose state);
 * appearance pushes and commands ride `evaluateJavascript`.
 *
 * The boot-transfer ladder lives in [BookTransferEffects] (shared with the
 * desktop host); this host's divergent halves are the WebView eval seam and
 * the LATCHED page-load fact. Android has no viewer status to watch (no
 * KcefStatus) — its boot failures are the page build and the encode, both
 * folded into [failBoot] here (the same error veil + retry semantics
 * desktop's viewer failures get): an IOException in either used to be an
 * uncaught composition-effect crash, and a failed page build used to pin the
 * boot veil forever (no WebView, no events).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    onEvent: EpubEventListener,
    // The stock WebView is lightweight — Compose sheets/dialogs composite
    // above it, so the desktop windowed-CEF occlusion toggle is a no-op here.
    overlayActive: Boolean,
    modifier: Modifier,
): EpubReaderHandle {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var pageHtml by remember { mutableStateOf<String?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    // The boot-transfer ladder's gate state (generation gating + payload) —
    // see BookDeliveryTracker for the full story.
    val delivery = remember { BookDeliveryTracker() }

    // The RAW seam, bypassing the delivery receipt: boot failures must fail
    // WITHOUT latching a delivery for a page generation that never received
    // the book (a receipt here would bar the retry's encode via shouldEncode).
    val rawEventsRef = rememberUpdatedState(onEvent)
    fun failBoot() = rawEventsRef.value.failBoot()

    // The whole ladder — receipt wiring, encode (with the failure fold into
    // failBoot), chunked send, payload release, appearance push. The eval
    // reads the view live so a rebuilt WebView (AndroidView recycle) gets the
    // re-sent book, not a stale capture.
    val receivingEvents = BookTransferEffects(
        bookFile = bookFile,
        resumePercent = resumePercent,
        appearance = appearance,
        onEvent = onEvent,
        delivery = delivery,
        pageLoaded = pageLoaded,
        eval = { script -> webViewRef.value?.evaluateJavascript(script, null) },
        encodeBook = { file -> withContext(Dispatchers.IO) { EpubBookCodec.encodeBase64(file) } },
        onFailBoot = ::failBoot,
    )
    val eventsRef = rememberUpdatedState(receivingEvents)
    // Events re-posted onto the main thread: `addJavascriptInterface` methods
    // run on the WebView's private JavaBridge thread, but the tap events
    // turn around and drive the host handle (evaluateJavascript — UI-thread
    // only) and Compose screen state. Dispatching here keeps every event
    // on the thread the screen was written for.
    val bridge = remember {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        EpubAndroidBridge { raw ->
            mainHandler.post { EpubEventParser.parse(raw).forEach(eventsRef.value::onEvent) }
        }
    }

    // A failed page build leaves pageHtml null — no WebView load, no events,
    // the stuck boot veil without this failure fold.
    LaunchedEffect(Unit) {
        runCatchingRethrowingCancellation { EpubReaderHtml.build() }
            .onSuccess { pageHtml = it }
            .onFailure { failBoot() }
    }
    // Load the page as soon as the built HTML and the WebView exist; its
    // onPageFinished hook flips `pageLoaded`, which gates every
    // evaluateJavascript below (a script before page-finished is a no-op).
    LaunchedEffect(pageHtml, webViewRef.value) {
        val html = pageHtml ?: return@LaunchedEffect
        val view = webViewRef.value ?: return@LaunchedEffect
        view.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
    }

    AndroidView(
        modifier = modifier,
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
                        delivery.onPageLoadFinished()
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

    return remember {
        EvaluatingEpubReaderHandle(
            viewerDownloadProgress = neverDownloads,
            eval = { script -> webViewRef.value?.evaluateJavascript(script, null) },
        )
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
