package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.datlag.kcef.KCEF
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.koin.compose.koinInject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop-side directories the EPUB host needs, all derived from the app data
 * dir (see desktopBookPlayerModule): the KCEF bundle install dir, the CEF
 * cache dir, and the scratch dir the built reader page is written to.
 */
internal class EpubDesktopEnv(
    val kcefDir: File,
    val cacheDir: File,
    val readerHtmlDir: File,
)

/**
 * Where the process-wide Chromium viewer stands. [progress] (below) still
 * carries the download fraction; this status names the phase so the host can
 * tell "still downloading" apart from "failed" — a failed init used to look
 * exactly like a slow one (null progress, blank WebView) and pinned the
 * reader on its boot veil forever.
 */
internal enum class KcefStatus {
    /** Init never attempted in this process. */
    IDLE,

    /** Init running (bundle download and/or CEF startup). */
    STARTING,

    /** CEF up — the host may create its browser. */
    READY,

    /** Init failed ([KcefRuntime.viewerError] names the cause). */
    FAILED,

    /** Bundle installed but CEF needs an app restart to load it. */
    RESTART_REQUIRED,
}

/**
 * Whether a viewer init attempt may start from [status]: anything but an
 * in-flight or already-ready runtime — a failure must be retryable (reopening
 * the reader retries), not latched for the process lifetime.
 */
internal fun shouldStartViewerInit(status: KcefStatus): Boolean =
    status != KcefStatus.STARTING && status != KcefStatus.READY

/**
 * Hides/restores the windowed browser for Compose overlays: [overlayActive]
 * means "a sheet or dialog holds the screen" (the sheet stack's `open` fold),
 * and windowed CEF composites above those overlay windows — the browser must
 * be unmapped for a raised sheet to show over the content region instead of
 * the book painting through it.
 *
 * Hides the canvas itself AND its first Swing ancestor (the SwingPanel
 * wrapper): hiding only the canvas leaves the wrapper mapped, and the wrapper
 * paints its look-and-feel background over the region — the white band a sheet
 * used to rise over — while hiding only the direct parent is fragile when the
 * interop container inserts an intermediate (non-Swing) parent between the
 * canvas and the wrapper. With both gone the region falls back to the Compose
 * surface (the layout's black column, plus the sheet's scrim). The walk stops
 * at the first JComponent and never reaches a Window, so the guard can never
 * reach the skiko root (an AWT Canvas) or the window itself. Null-safe
 * (a not-yet-added browser has no wrapper yet) and idempotent (synced both
 * from onCreated and post-composition — see the host). Runs on the EDT —
 * SideEffects may land off it. Pinned by DesktopViewerBootTest.
 *
 * This is the second belt: the desktop layout additionally collapses the
 * browser's Compose size to 0 px while a sheet is open (see ReaderContent),
 * so even a peer that ignores visibility still occupies no pixels.
 */
internal fun syncBrowserSurfaceVisibility(uiComponent: java.awt.Component?, overlayActive: Boolean) {
    val component = uiComponent ?: return
    val apply = Runnable {
        val visible = !overlayActive
        component.isVisible = visible
        var parent = component.parent
        while (parent != null && parent !is java.awt.Window) {
            if (parent is javax.swing.JComponent) {
                parent.isVisible = visible
                break
            }
            parent = parent.parent
        }
        component.parent?.let {
            it.revalidate()
            it.repaint()
        }
    }
    if (javax.swing.SwingUtilities.isEventDispatchThread()) {
        apply.run()
    } else {
        javax.swing.SwingUtilities.invokeLater(apply)
    }
}

/**
 * The process-wide KCEF owner. CEF does not reliably survive a
 * dispose-then-reinit cycle inside one JVM, so the client starts once and
 * lives for the whole app session — screens observe [progress]/[viewerStatus]
 * and NEVER dispose (app exit tears the process down with it).
 *
 * The init runs on a runtime-owned scope, NOT a screen's: the first run
 * downloads the CEF bundle for tens of seconds, and a reader screen left
 * mid-download would cancel [KCEF.init] with the KCEF state machine parked
 * on `Initializing` — every later `newClientOrNullBlocking` (a blocking
 * call inside the library's composition) would then wait forever.
 */
internal class KcefRuntime(private val env: EpubDesktopEnv) {

    /** `null` = nothing downloading (ready or not started); else 0f..1f. */
    private val _progress = mutableStateOf<Float?>(null)
    val progress: State<Float?> = _progress

    private val _viewerStatus = mutableStateOf(KcefStatus.IDLE)
    val viewerStatus: State<KcefStatus> = _viewerStatus

    private val _viewerError = mutableStateOf<String?>(null)
    val viewerError: State<String?> = _viewerError

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureStarted() {
        if (!shouldStartViewerInit(_viewerStatus.value)) return
        _viewerStatus.value = KcefStatus.STARTING
        _viewerError.value = null
        initScope.launch {
            KCEF.init(
                builder = {
                    installDir(env.kcefDir)
                    progress {
                        onDownloading { value ->
                            // KCEF reports percent on some versions, fraction
                            // on others — normalize to 0f..1f.
                            _progress.value = (if (value > 1f) value / 100f else value).coerceIn(0f, 1f)
                        }
                        onInitialized {
                            _progress.value = null
                            _viewerStatus.value = KcefStatus.READY
                        }
                    }
                    settings { cachePath = env.cacheDir.absolutePath }
                },
                onError = { error ->
                    // Surface, don't strand: with no browser the host forwards
                    // an ERROR status so the reader shows its error veil
                    // instead of the boot veil forever (the pre-status failure
                    // this left only on stderr).
                    _progress.value = null
                    _viewerError.value = error?.toString()
                    _viewerStatus.value = KcefStatus.FAILED
                    System.err.println("[JellyPlay] KCEF init failed: $error")
                },
                onRestartRequired = {
                    _progress.value = null
                    _viewerStatus.value = KcefStatus.RESTART_REQUIRED
                    System.err.println("[JellyPlay] KCEF init needs an app restart to finish installing")
                },
            )
        }
    }
}

/**
 * Desktop EPUB host: compose-webview-multiplatform (KCEF/Chromium). The page
 * loads from a hash-named `reader-*.html` under [EpubDesktopEnv.readerHtmlDir]
 * (rewritten only when the built document changes).
 *
 * JS→native: this library exposes no URI-interception hook, so the host pulls
 * `window.jellyPlayReader.consumeEvents()` via `evaluateJavaScript` every
 * 500 ms — reader.js queues events until drained, so none are lost.
 *
 * The boot-transfer ladder lives in [BookTransferEffects] (shared with the
 * Android host); this host's divergent halves are the CEF event poll, the
 * LIVE page-load fact (the phantom-Finished identity probe below) and the
 * viewer-failure channel ([KcefStatus] → failBoot — the failure source
 * Android has no counterpart of).
 */
@Composable
internal actual fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    onEvent: EpubEventListener,
    overlayActive: Boolean,
    modifier: Modifier,
): EpubReaderHandle {
    val env = koinInject<EpubDesktopEnv>()
    val kcef = koinInject<KcefRuntime>()
    var pageLoaded by remember { mutableStateOf(false) }
    // The boot-transfer ladder (generation gating + payload) — see
    // BookDeliveryTracker: a reload here wipes reader.js and the book with it.
    val delivery = remember { BookDeliveryTracker() }
    // The live browser's canvas — the overlay-occlusion toggle's input (the
    // toggle hides its SwingPanel wrapper, see syncBrowserSurfaceVisibility):
    // captured in onCreated, cleared on dispose, synced with overlayActive
    // through the SideEffect below.
    val browserSurfaceRef = remember { mutableStateOf<java.awt.Component?>(null) }
    val overlayActiveState = rememberUpdatedState(overlayActive)

    val downloadProgress = kcef.progress
    val viewerStatus by kcef.viewerStatus
    var readerFileUrl by remember { mutableStateOf<String?>(null) }
    val navigatorRef = remember { mutableStateOf<WebViewNavigator?>(null) }
    // The RAW seam, bypassing the delivery receipt: viewer failures must
    // fail the boot WITHOUT latching a delivery for a page generation that
    // never received the book (a receipt here would bar the retry's encode
    // via shouldEncode).
    val rawEventsRef = rememberUpdatedState(onEvent)
    fun failBoot() = rawEventsRef.value.failBoot()
    // The whole boot-transfer ladder (receipt wiring + encode, with the
    // encode-failure fold into failBoot, + chunked send, payload release,
    // appearance push) — see BookTransferEffects. The eval reads the
    // navigator LIVE so the ladder reaches the CURRENT browser (a reader-file
    // rewrite creates a new one), never a stale capture; the send/push gates
    // can only open once a page actually loaded through it.
    val receivingEvents = BookTransferEffects(
        bookFile = bookFile,
        resumePercent = resumePercent,
        appearance = appearance,
        onEvent = onEvent,
        delivery = delivery,
        pageLoaded = pageLoaded,
        eval = { script -> navigatorRef.value?.evaluateJavaScript(script, null) },
        encodeBook = { file -> withContext(Dispatchers.IO) { EpubBookCodec.encodeBase64(file) } },
        onFailBoot = ::failBoot,
    )
    val eventsRef = rememberUpdatedState(receivingEvents)

    // Idempotent per outcome — a FAILED/RESTART_REQUIRED runtime retries on
    // the next reader open (see KcefRuntime). The init outlives this screen
    // (runtime-owned scope — see KcefRuntime).
    LaunchedEffect(Unit) { kcef.ensureStarted() }

    // A dead viewer fails the boot outright: with no browser no status event
    // will ever arrive, which used to pin the "preparing locations" veil
    // forever. Guarded on no delivery yet so a (theoretical) late failure
    // can never veil a book that is already reading.
    LaunchedEffect(viewerStatus, delivery.deliveredGeneration) {
        val failed = viewerStatus == KcefStatus.FAILED ||
            viewerStatus == KcefStatus.RESTART_REQUIRED
        if (failed && delivery.deliveredGeneration < 0) failBoot()
    }

    LaunchedEffect(env) {
        // A build/write failure leaves readerFileUrl null — no WebView, no
        // events, same stuck veil without this ERROR arm.
        runCatchingRethrowingCancellation {
            val html = EpubReaderHtml.build()
            withContext(Dispatchers.IO) {
                env.readerHtmlDir.mkdirs()
                val current = File(env.readerHtmlDir, EpubReaderHtml.fileName(html)).apply {
                    if (!exists()) writeText(html)
                }
                // One ~1.5MB page per resource change adds up — prune ancestors.
                env.readerHtmlDir.listFiles { f -> f.name.startsWith("reader-") && f != current }
                    ?.forEach { runCatching { it.delete() } }
                current
            }
        }.onSuccess { file ->
            readerFileUrl = file.toURI().toString()
        }.onFailure {
            failBoot()
        }
    }

    // The browser factory blocks the composition thread until KCEF is up
    // (newClientOrNullBlocking) and renders nothing when init failed — so the
    // WebView only composes into a READY runtime. Besides the fail-fast error
    // veil, this keeps a first-run download from freezing the UI behind a
    // blocked composition.
    val viewerReady = viewerStatus == KcefStatus.READY
    val url = readerFileUrl
    if (url != null && viewerReady) {
        val state = rememberWebViewState(url)
        val navigator = rememberWebViewNavigator()
        SideEffect { navigatorRef.value = navigator }

        WebView(
            state = state,
            navigator = navigator,
            modifier = modifier,
            // JS is enabled by default (WebViewState.webSettings.isJavaScriptEnabled).
            // One-param lambdas pick the desktop overload of WebView.
            onCreated = { browser ->
                browserSurfaceRef.value = browser.uiComponent
                syncBrowserSurfaceVisibility(browser.uiComponent, overlayActiveState.value)
            },
            onDispose = { _ -> browserSurfaceRef.value = null },
        )

        /*
         * Windowed CEF composites above every Compose surface — the M3
         * sheet/dialog windows included, so a raised sheet showed the book
         * through its own middle band (the sheet windows do NOT float above
         * the browser on desktop). While an overlay holds the screen the
         * browser's SwingPanel wrapper unmaps (the sheet then renders over
         * the black content region) and remaps on dismiss. Post-composition
         * sync so every overlayActive flip lands even when onCreated ran.
         */
        SideEffect {
            syncBrowserSurfaceVisibility(browserSurfaceRef.value, overlayActiveState.value)
        }

        /*
         * CEF fires a PHANTOM Loading→Finished cycle seconds after the real
         * page load — verified in a harness: the page's JS state survives it,
         * so it is not a reload (some internal loadContent re-issue; the
         * library exposes no distinction). Counting every Finished as a new
         * page generation re-encoded and RE-SENT the whole book into the live
         * page — reader.js's loadBookBegin reset assembly and re-opened the
         * book mid-boot, and on real-sized books the reader sat on the
         * "preparing locations" veil for a second full boot (or forever on a
         * big one). Identify the page instead: the host stamps the generation
         * into the page right after each bump; a Finished whose stamp already
         * matches the current generation is the same live page, not a new
         * one. An unknown/mismatched stamp means a genuinely fresh page
         * (first load or a real reload) — bump, then stamp it.
         */
        LaunchedEffect(state.loadingState) {
            if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            // The desktop evaluate path drops null results WITHOUT invoking
            // the callback, so a single probe can vanish silently — and no
            // further Finished event will retry it, stranding pageLoaded and
            // the whole boot. Re-probe until the page answers or the budget
            // runs out; the probed-generation guard keeps a late duplicate
            // from double-bumping when a retry lost its race.
            val answered = AtomicBoolean(false)
            repeat(PAGE_IDENTITY_PROBE_ATTEMPTS) {
                val probed = delivery.pageGeneration
                navigator.evaluateJavaScript(pageIdentityProbe(probed)) { result ->
                    when (parsePageIdentityResult(result)) {
                        true -> answered.set(true)
                        false -> {
                            if (delivery.pageGeneration == probed) {
                                pageLoaded = true
                                delivery.onPageLoadFinished()
                                navigator.evaluateJavaScript(
                                    pageIdentityStamp(delivery.pageGeneration),
                                    null,
                                )
                            }
                            answered.set(true)
                        }
                        // Unanswered (the desktop null-swallow): the loop below retries.
                        null -> Unit
                    }
                }
                delay(PAGE_IDENTITY_PROBE_RETRY_MS)
                if (answered.get()) return@LaunchedEffect
            }
            // The page never answered — unreachable JS context, not a slow
            // one. Fail the boot instead of pinning the veil forever.
            if (delivery.deliveredGeneration < 0) failBoot()
        }

        // The event poll — see the class doc for why this is a pull. Gated on
        // pageLoaded so it never evaluates against an unfinished document.
        LaunchedEffect(navigator, pageLoaded) {
            if (!pageLoaded) return@LaunchedEffect
            while (isActive) {
                delay(EVENT_POLL_MS)
                navigator.evaluateJavaScript(buildConsumeEventsScript()) { raw ->
                    EpubEventParser.parse(raw).forEach(eventsRef.value::onEvent)
                }
            }
        }
    }

    return remember {
        EvaluatingEpubReaderHandle(
            viewerDownloadProgress = downloadProgress,
            eval = { script -> navigatorRef.value?.evaluateJavaScript(script, null) },
        )
    }
}

private const val EVENT_POLL_MS = 500L

/** Cadence + budget for the page-identity probe retry loop (see the host). */
internal const val PAGE_IDENTITY_PROBE_RETRY_MS = 250L
internal const val PAGE_IDENTITY_PROBE_ATTEMPTS = 12

/**
 * Page-identity probe/stamp for the phantom-Finished guard (see the host):
 * the stamp rides the page, so `undefined` means a page the host has not
 * seen yet — first load or a genuine reload — while a match with the
 * current generation means the live page was re-signalled, not replaced.
 */
internal fun pageIdentityProbe(generation: Int): String =
    "window.__jellyPlayPageGeneration === $generation"

internal fun pageIdentityStamp(generation: Int): String =
    "window.__jellyPlayPageGeneration = $generation"

/**
 * Reads one probe answer: `true` = the live page already carries this
 * generation (phantom re-signal, nothing to do), `false` = a fresh page that
 * needs the book, `null` = no usable answer (garbage payload — and, on the
 * desktop evaluate path, a null result never reaches the callback at all, so
 * the host treats a missing answer the same way: retry).
 */
internal fun parsePageIdentityResult(raw: String?): Boolean? =
    when (raw?.trim()?.removeSurrounding("\"")) {
        "true" -> true
        "false" -> false
        else -> null
    }
