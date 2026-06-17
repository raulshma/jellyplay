package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Persisted alongside a downloaded video in `<video-dir>/subtitles/manifest.json`.
 * Describes every external subtitle stream bundled for offline playback so the
 * player can reconstruct engine subtitle sources with correct metadata without
 * a server round-trip. See [com.raulshma.jellyplay.core.data.repository.DownloadRepository].
 */
@Immutable
@Serializable
data class OfflineSubtitleManifest(
    val subtitles: List<OfflineSubtitleEntry> = emptyList(),
)

@Immutable
@Serializable
data class OfflineSubtitleEntry(
    val index: Int,
    val fileName: String,
    val language: String? = null,
    val codec: String? = null,
    val title: String? = null,
    val displayTitle: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
)
