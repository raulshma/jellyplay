package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaType

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
 * - [OpenOffline] is a navigation request, not a snackbar: the online detail
 *   failed to load but the item is downloaded, so the screen redirects to the
 *   offline detail/series page instead of showing a load error.
 */
@Immutable
sealed interface DetailMessage {
    data class Text(val text: String) : DetailMessage
    data class SeriesDownload(val queuedCount: Int, val error: String?) : DetailMessage
    data class OpenOffline(val itemId: String, val mediaType: MediaType) : DetailMessage
}
