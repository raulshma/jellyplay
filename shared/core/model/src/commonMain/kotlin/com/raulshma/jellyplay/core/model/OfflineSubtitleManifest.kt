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
    /**
     * Bitmap subtitle (PGS/VOBSUB/DVB) whose bytes were delivered verbatim via
     * a server [MediaStream.deliveryUrl]. Only mpv can render such a sidecar —
     * the player gates offline side-loading of these entries on engine
     * support. Defaults to `false` so manifests written before this field
     * existed decode unchanged (the Json instance ignores unknown keys, and
     * playback additionally sniffs [codec] for those legacy entries).
     */
    val isImage: Boolean = false,
) {
    /**
     * Single gating predicate for offline side-loading: `true` when this entry
     * must only be handed to engines advertising the `supportsImageSubtitles`
     * engine capability. The [isImageSubtitleCodec] fallback covers legacy
     * manifests written before the [isImage] field existed.
     */
    val isBitmapSidecar: Boolean
        get() = isImage || isImageSubtitleCodec(codec)
}
