package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable

/**
 * One-shot delete feedback emitted by [DownloadsViewModel] and rendered by
 * [DownloadsScreen], which posts the resolved text to the shared UserMessageBus.
 * The screen resolves the resource text (compose-resources), so no R class or
 * UiText machinery leaks into shared code.
 */
@Immutable
sealed interface DownloadsUserMessage {
    /**
     * Download(s) were removed from the device — `downloads_deleted_message`.
     * Emitted by both the per-item delete and the bulk selection delete.
     */
    data object Deleted : DownloadsUserMessage

    /**
     * Ask-mode suggestions were confirmed (gh-181) — the episodes were queued
     * through the normal series/season intake. Fixed copy (the pluralized
     * count needs the @Composable resolver; see the SnackbarQueue note in
     * MediaDetailScreen for why collect-time plurals don't resolve here).
     */
    data object SuggestionsQueued : DownloadsUserMessage

    /** Raw failure text (exception message or fixed fallback). */
    data class Raw(val text: String) : DownloadsUserMessage
}
