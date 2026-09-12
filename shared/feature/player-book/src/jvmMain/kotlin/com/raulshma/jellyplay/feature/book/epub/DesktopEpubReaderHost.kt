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
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import dev.datlag.kcef.KCEF
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.koin.compose.koinInject
import java.io.File

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
 * The process-wide KCEF owner. CEF does not reliably survive a
 * dispose-then-reinit cycle inside one JVM, so the client starts once and
 * lives for the whole app session — screens observe [progress] and NEVER
 * dispose (app exit tears the process down with it).
 */
internal class KcefRuntime(private val env: EpubDesktopEnv) {

    /** `null` = nothing downloading (ready or not started); else 0f..1f. */
    private val _progress = mutableStateOf<Float?>(null)
    val progress: State<Float?> = _progress

    @Volatile
    private var started = false

    fun ensureStarted(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            KCEF.init(
                builder = {
                    installDir(env.kcefDir)
                    progress {
                        onDownloading { value ->
                            // KCEF reports percent on some versions, fraction
                            // on others — normalize to 0f..1f.
                            _progress.value = (if (value > 1f) value / 100f else value).coerceIn(0f, 1f)
                        }
                        onInitialized { _progress.value = null }
                    }
                    settings { cachePath = env.cacheDir.absolutePath }
                },
                onError = { _ ->
                    // Drop the veil even on failure — a blank WebView with the
                    // chrome still beats a stuck "downloading" screen.
                    _progress.value = null
                },
                onRestartRequired = { },
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
 */
@Composable
internal actual fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    theme: ReaderTheme,
    fontSizePx: Int,
    callbacks: EpubReaderCallbacks,
): EpubReaderHandle {
    val env = koinInject<EpubDesktopEnv>()
    val kcef = koinInject<KcefRuntime>()
    val callbacksRef = rememberUpdatedState(callbacks)
    val downloadProgress = kcef.progress
    var readerFileUrl by remember { mutableStateOf<String?>(null) }
    var bookBase64 by remember { mutableStateOf<String?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var bookSent by remember { mutableStateOf(false) }
    val navigatorRef = remember { mutableStateOf<WebViewNavigator?>(null) }
    val scope = rememberCoroutineScope()

    // Idempotent — only the first caller actually initializes CEF.
    LaunchedEffect(Unit) { kcef.ensureStarted(scope) }

    LaunchedEffect(env) {
        val html = EpubReaderHtml.build()
        val file = withContext(Dispatchers.IO) {
            env.readerHtmlDir.mkdirs()
            val current = File(env.readerHtmlDir, EpubReaderHtml.fileName(html)).apply {
                if (!exists()) writeText(html)
            }
            // One ~1.5MB page per resource change adds up — prune ancestors.
            env.readerHtmlDir.listFiles { f -> f.name.startsWith("reader-") && f != current }
                ?.forEach { runCatching { it.delete() } }
            current
        }
        readerFileUrl = file.toURI().toString()
    }
    LaunchedEffect(bookFile) {
        bookBase64 = withContext(Dispatchers.IO) { EpubBookCodec.encodeBase64(bookFile) }
    }

    val url = readerFileUrl
    if (url != null) {
        val state = rememberWebViewState(url)
        val navigator = rememberWebViewNavigator()
        SideEffect { navigatorRef.value = navigator }

        WebView(
            state = state,
            navigator = navigator,
            modifier = Modifier.fillMaxSize(),
            // JS is enabled by default (WebViewState.webSettings.isJavaScriptEnabled).
            // One-param lambdas pick the desktop overload of WebView.
            onCreated = { _ -> },
            onDispose = { _ -> },
        )

        LaunchedEffect(state.loadingState) {
            if (state.loadingState is LoadingState.Finished) pageLoaded = true
        }

        LaunchedEffect(state.loadingState, bookBase64) {
            val base64 = bookBase64 ?: return@LaunchedEffect
            if (bookSent || state.loadingState !is LoadingState.Finished) return@LaunchedEffect
            bookSent = true
            sendBookChunks(base64, resumePercent, theme, fontSizePx) { navigator.evaluateJavaScript(it, null) }
        }

        // Theme/font push for CHANGES after load — see pushAppearanceScripts.
        LaunchedEffect(pageLoaded, bookSent, theme, fontSizePx) {
            if (!pageLoaded || !bookSent) return@LaunchedEffect
            pushAppearanceScripts(theme, fontSizePx) { navigator.evaluateJavaScript(it, null) }
        }

        // The event poll — see the class doc for why this is a pull. Gated on
        // pageLoaded so it never evaluates against an unfinished document.
        LaunchedEffect(navigator, pageLoaded) {
            if (!pageLoaded) return@LaunchedEffect
            while (isActive) {
                delay(EVENT_POLL_MS)
                navigator.evaluateJavaScript(buildConsumeEventsScript()) { raw ->
                    dispatchEpubEvents(raw, callbacksRef.value)
                }
            }
        }
    }

    return remember {
        object : EpubReaderHandle {
            override val viewerDownloadProgress = downloadProgress

            override fun next() {
                navigatorRef.value?.evaluateJavaScript(buildNextScript(), null)
            }

            override fun prev() {
                navigatorRef.value?.evaluateJavaScript(buildPrevScript(), null)
            }

            override fun goTo(href: String) {
                navigatorRef.value?.evaluateJavaScript(buildGoToScript(href), null)
            }
        }
    }
}

private const val EVENT_POLL_MS = 500L
