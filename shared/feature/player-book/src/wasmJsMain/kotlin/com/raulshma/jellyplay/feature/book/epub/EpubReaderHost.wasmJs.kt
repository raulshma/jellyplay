package com.raulshma.jellyplay.feature.book.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import okio.Path

/**
 * The wasmJs actual of the EPUB host seam: no WebView host exists on the
 * browser for now (the Android actual embeds a WebView, desktop embeds
 * KCEF/Chromium), so the handle degrades honestly — it reports
 * [EpubReaderStatus.ERROR] through the callbacks immediately (the reader
 * screen hides its loading veil on ERROR and shows its own error affordances)
 * and every command ([EpubReaderHandle.next]/[prev]/[goTo] and the newer
 * Wave 2 surface: goToCfi/setFlow/annotations/search/speech/auto-scroll) is a
 * never-called no-op. The CEF-download progress state stays permanently null.
 */
internal class WasmEpubReaderHandle : EpubReaderHandle {
    private val noProgress = mutableStateOf<Float?>(null)
    override val viewerDownloadProgress: State<Float?> = noProgress
    override fun next() {}
    override fun prev() {}
    override fun goTo(href: String) {}
    override fun goToCfi(cfi: String) {}
    override fun setFlow(scrolled: Boolean) {}
    override fun applyAnnotations(entries: List<EpubAnnotationSpec>) {}
    override fun removeAnnotation(cfi: String) {}
    override fun search(query: String, token: Int) {}
    override fun requestSpeechContext(cfi: String?) {}
    override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) {}
}

@Composable
internal actual fun rememberEpubReaderHost(
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    callbacks: EpubReaderCallbacks,
): EpubReaderHandle {
    val handle = remember { WasmEpubReaderHandle() }
    LaunchedEffect(handle) {
        callbacks.onStatusChanged(EpubReaderStatus.ERROR)
    }
    return handle
}
