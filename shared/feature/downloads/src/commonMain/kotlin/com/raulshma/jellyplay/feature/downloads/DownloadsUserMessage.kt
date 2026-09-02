package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable

/**
 * One-shot delete feedback emitted by [DownloadsViewModel] and rendered by
 * [DownloadsScreen] through the DownloadsMessenger seam — the commonMain-safe
 * replacement for the legacy `UiText.Resource(R.string.downloads_deleted_message)`
 * values the ViewModel used to post through the Android-only UserMessageBus.
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
    /** Raw failure text (exception message or fixed fallback). */
    data class Raw(val text: String) : DownloadsUserMessage
}
