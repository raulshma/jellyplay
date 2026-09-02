package com.raulshma.jellyplay.core.model.subtitle

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.isLanguageMatch

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
    /**
     * The server `MediaStream.index` the upload of this subtitle produced, when
     * known. Recorded after a successful upload so a server-side delete (the
     * metadata editor's delete-by-index) can purge exactly this local copy, and
     * playback load can skip entries whose stream no longer exists. Null for
     * legacy manifests and device-only downloads (upload failed) — those must
     * keep side-loading regardless of server state.
     */
    val serverStreamIndex: Int? = null,
)

/**
 * Whether an uploaded external-provider subtitle's server [stream]
 * corresponds to [saved]. Attribute-based (language/codec/role flags on an
 * EXTERNAL stream — embedded container subs never match), used both to
 * recover the new stream index right after an upload and as the legacy-entry
 * fallback when purging local copies by hand.
 *
 * Codecs compare by FAMILY because the two sides speak different dialects:
 * the store records the download FILE format (`srt`) while the server reports
 * the ffmpeg codec name (`subrip`). Unknown codecs on either side are lenient.
 */
fun MediaStream.matchesSavedSubtitle(saved: SavedSubtitle): Boolean {
    if (!isExternal) return false
    if (type != StreamType.SUBTITLE) return false
    val langMatch = language == null || saved.language == null ||
        isLanguageMatch(language, saved.language)
    val thisFamily = codecFamily(codec)
    val savedFamily = codecFamily(saved.codec)
    val codecMatch = thisFamily == null || savedFamily == null || thisFamily == savedFamily
    return langMatch && codecMatch &&
        isForced == saved.isForced &&
        isHearingImpaired == saved.isHearingImpaired
}

private fun codecFamily(codec: String?): String? = codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
    when (it) {
        "srt", "subrip" -> "subrip"
        "vtt", "webvtt" -> "webvtt"
        else -> it
    }
}

/**
 * Finds the server `MediaStream.index` [saved]'s upload produced within
 * [streams]: among matching streams prefer ones that appeared after
 * [preExistingIndices] was captured (the server appends, so the newest match
 * wins), falling back to the highest-index match when no fresh one is
 * distinguishable. Returns null when nothing matches (upload not reflected
 * yet / device-only download).
 */
fun findSavedSubtitleStreamIndex(
    streams: List<MediaStream>,
    saved: SavedSubtitle,
    preExistingIndices: Set<Int> = emptySet(),
): Int? =
    streams.asSequence()
        .filter { it.type == StreamType.SUBTITLE && it.matchesSavedSubtitle(saved) }
        .let { matched ->
            val fresh = matched.filter { it.index !in preExistingIndices }
            (fresh.ifEmpty { matched })
        }
        .maxByOrNull { it.index }
        ?.index

/**
 * External-subtitle stream indices captured BEFORE a subtitle upload, so
 * [findSavedSubtitleStreamIndex] can prefer streams that appeared after it.
 * Both upload paths (metadata editor, in-player provider download) snapshot
 * this immediately before calling the server.
 */
fun List<MediaStream>.externalSubtitleIndices(): Set<Int> =
    filter { it.type == StreamType.SUBTITLE && it.isExternal }
        .mapTo(mutableSetOf()) { it.index }

/**
 * Whether this saved copy corresponds to the server subtitle stream just
 * deleted at [deletedIndex]: exact when its uploaded index was recorded,
 * otherwise legacy entries (saved before linkage existed, or device-only
 * downloads that never uploaded) attribute-match the removed [deletedStream].
 * The single policy behind `StreamingSubtitleStore.purgeDeletedServerStreamCopies`.
 */
fun SavedSubtitle.matchesDeletedServerStream(deletedIndex: Int, deletedStream: MediaStream?): Boolean =
    serverStreamIndex?.let { it == deletedIndex }
        ?: (deletedStream != null && deletedStream.matchesSavedSubtitle(this))

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
