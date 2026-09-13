package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.BookProgressPolicy
import okio.Path

/**
 * The reader screen's UI state. Sealed Loading/Ready/Error mirroring the
 * video player's shape; only the ViewModel writes it. A [Ready] book is one
 * of two content kinds — the paged renderer (CBZ/PDF/CBR) or the reflowable
 * EPUB host (see [ReadyContent]).
 */
sealed interface BookReaderUiState {

    /** Before [BookReaderViewModel.load] ran for the first time. */
    data object Idle : BookReaderUiState

    /**
     * Resolving/opening the document. [progress] is the streaming-fetch byte
     * fraction (0f..1f) when known; null = indeterminate (offline hit, cached
     * file, or a server that sends no content length).
     */
    data class Loading(val progress: Float? = null) : BookReaderUiState

    data class Ready(
        val title: String,
        val content: ReadyContent,
        val showControls: Boolean = true,
        val showSettings: Boolean = false,
    ) : BookReaderUiState

    /**
     * @param detail for [ErrorReason.UnsupportedFormat]: either the file
     *   extension the item's path actually carried (".mobi" — known but not
     *   readable in-app) or null when the server reported no path at all. The
     *   veil renders a different line per case so a broken book setup is
     *   diagnosable from the screen instead of a bare "unsupported".
     */
    data class Error(val reason: ErrorReason, val detail: String? = null) : BookReaderUiState

    enum class ErrorReason {
        /** Corrupt archive, password-protected PDF, missing local file, network failure. */
        CannotOpen,

        /** The item's path carries no known book extension ([Error.detail] says which). */
        UnsupportedFormat,
    }
}

/** What a [BookReaderUiState.Ready] actually renders. */
sealed interface ReadyContent {

    /** Paged renderer (CBZ/PDF/CBR): pager over pre-rendered pages. */
    data class Paged(
        val pageCount: Int,
        val currentPage: Int,
    ) : ReadyContent

    /**
     * Reflowable EPUB: the platform WebView host renders the resolved local
     * file; position is a percent (see [BookProgressPolicy] percent
     * convention), not a page index.
     */
    data class Reflowable(
        val bookFile: Path,
        val resumePercent: Double,
    ) : ReadyContent
}
