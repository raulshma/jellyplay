package com.raulshma.jellyplay.core.model.subtitle

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A subtitle downloaded from an external provider ([SubtitleProviderKind]) and
 * persisted durably to disk by `StreamingSubtitleStore` so it survives replay
 * even when the Jellyfin server is unreachable.
 *
 * This mirrors the offline-subtitle convention (`OfflineSubtitleManifest`) but
 * is keyed by Jellyfin `itemId` rather than a downloaded media-file path,
 * because streaming items have no on-disk video dir of their own. See
 * `StreamingSubtitleStore`.
 *
 * [fileRelativePath] is relative to the per-item dir
 * (`<filesDir>/streaming-subtitles/<itemId>/`) so the store can relocate the
 * root without rewriting the manifest.
 */
@Immutable
@Serializable
data class SavedSubtitle(
    val provider: SubtitleProviderKind,
    val providerSubtitleId: String,
    val fileName: String,
    val language: String?,
    val codec: String?,
    val isForced: Boolean,
    val isHearingImpaired: Boolean,
    val fileRelativePath: String,
)

/**
 * Manifest persisted at `<filesDir>/streaming-subtitles/<itemId>/manifest.json`.
 * Lists every external-provider subtitle saved for that streaming item so the
 * player can re-side-load them on replay without a server round-trip.
 */
@Immutable
@Serializable
data class StreamingSubtitleManifest(
    val subtitles: List<SavedSubtitle> = emptyList(),
)
