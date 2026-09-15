package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.runtime.State

/**
 * The ONE [EpubReaderHandle] dispatch table, written once for both platform
 * hosts: every command maps to its builder script in [EpubReaderHost.kt] and
 * evaluates through the injected [eval]. The Android WebView and the desktop
 * CEF navigator previously carried line-for-line copies of this table —
 * `requestSpeechContext` and `setAutoScroll` were each added twice — so the
 * next protocol command is a one-file change (plus reader.js) again.
 *
 * The platform actuals keep everything that genuinely diverges: the page-load
 * → book-send-once → appearance-push ladders, the event channel (Android's
 * push bridge vs desktop's 500 ms poll), and the viewer-download progress
 * (KCEF only — Android passes a permanently-null State).
 */
internal class EvaluatingEpubReaderHandle(
    override val viewerDownloadProgress: State<Float?>,
    private val eval: (String) -> Unit,
) : EpubReaderHandle {

    override fun next() = eval(buildNextScript())

    override fun prev() = eval(buildPrevScript())

    override fun goTo(href: String) = eval(buildGoToScript(href))

    override fun goToCfi(cfi: String) = eval(buildGoToCfiScript(cfi))

    override fun setFlow(scrolled: Boolean) = eval(buildSetFlowScript(scrolled))

    override fun applyAnnotations(entries: List<EpubAnnotationSpec>) =
        eval(buildApplyAnnotationsScript(entries))

    override fun addAnnotation(entry: EpubAnnotationSpec) =
        eval(buildAddAnnotationScript(entry))

    override fun removeAnnotation(cfi: String) = eval(buildRemoveAnnotationScript(cfi))

    override fun clearSelection() = eval(buildClearSelectionScript())

    override fun search(query: String, token: Int) = eval(buildSearchScript(query, token))

    override fun requestSpeechContext(cfi: String?) = eval(buildSpeechContextScript(cfi))

    override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) =
        eval(buildSetAutoScrollScript(enabled, pxPerSec))
}
