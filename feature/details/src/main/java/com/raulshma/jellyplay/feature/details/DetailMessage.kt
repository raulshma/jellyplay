package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable

/**
 * One-shot user-facing messages emitted from [DetailViewModel]. Consumed by the
 * screen via a single [kotlinx.coroutines.flow.SharedFlow] collector and shown
 * as a snackbar.
 *
 * Replaces the former trio of nullable [DetailUiState] fields (`userMessage`,
 * `downloadError`, `seriesDownloadResult`) + their three near-identical
 * `LaunchedEffect` pumps at the screen entry.
 *
 * - [Text] is a fully-resolved string (caller already did resource lookup).
 * - [SeriesDownload] keeps the raw count/error so the screen can resolve the
 *   plural `detail_episodes_queued` resource, which only the UI layer can do.
 * - [WatchPartyStarted] carries the item id of a freshly-bootstrapped SyncPlay
 *   group so the screen can navigate to the player; the existing [SyncPlayBridge]
 *   auto-detects the active session on attach. It is a dedicated variant rather
 *   than a [Text] toast because it expresses navigation intent.
 *
 * The former `OpenOffline` navigation request has been removed: the
 * [com.raulshma.jellyplay.core.data.repository.MediaDetailProvider] now performs
 * the remote/local fallback in place (origin `LOCAL_REMOTE_FAILURE`) instead of
 * redirecting to a separate offline screen, so a load failure no longer carries
 * a navigation side effect.
 */
@Immutable
sealed interface DetailMessage {
    data class Text(val text: String) : DetailMessage
    data class SeriesDownload(val queuedCount: Int, val error: String?) : DetailMessage
    data class WatchPartyStarted(val itemId: String) : DetailMessage
}
