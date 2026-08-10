package com.raulshma.jellyplay.core.model.subtitle

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The set of subtitle search/download providers JellyPlay can talk to.
 *
 * [JELLYFIN] is always available (the server's own OpenSubtitles proxy); the
 * external providers ([WYZIE], [OPENSUBTITLES]) are only active when the user
 * has entered credentials under *Integrations → Subtitle Providers*.
 *
 * Adding a new provider is intentionally a three-point change: a new enum value
 * here, a matching [SubtitleProviderCredentials] subclass, and one
 * `core/network` provider implementation registered in DI. The fan-out
 * repository, settings UI, and search sheet pick it up generically from the
 * enum + DI map — no per-provider wiring in those layers.
 */
@Immutable
@Serializable
enum class SubtitleProviderKind {
    JELLYFIN,
    WYZIE,
    OPENSUBTITLES,
}

/**
 * A single subtitle returned by a [SubtitleProviderKind] search. The [id] is
 * stable within a provider and is the handle passed back to the provider to
 * download the file. [provider] tags every row so the UI can badge provenance
 * and the repository can dispatch the download to the owning provider.
 *
 * [downloadUrl] carries the provider-issued direct file URL when the provider
 * exposes one inline (Wyzie's search response `url` field). Providers that need
 * a separate download handshake (OpenSubtitles `POST /download`) leave it null
 * and resolve the URL during download.
 */
@Immutable
@Serializable
data class SubtitleSearchResult(
    val provider: SubtitleProviderKind,
    val id: String,
    val language: String?,
    val displayName: String,
    val releaseName: String? = null,
    val format: String? = null,
    val isHearingImpaired: Boolean = false,
    val isForced: Boolean = false,
    val isAiTranslated: Boolean? = null,
    val downloadCount: Int? = null,
    val rating: Double? = null,
    val author: String? = null,
    val fileName: String? = null,
    val downloadUrl: String? = null,
    /**
     * TV episode metadata echoed by the provider (when available), used by the
     * provider's `search` to filter cross-episode rows client-side. `null` for
     * movies, Jellyfin rows, or providers that do not return per-row episode
     * metadata (e.g. Wyzie). Not user-facing.
     */
    val season: Int? = null,
    val episode: Int? = null,
    /**
     * Only present for [SubtitleProviderKind.JELLYFIN] rows — carries the
     * server-native [com.raulshma.jellyplay.core.model.RemoteSubtitleInfo] so
     * the player/editor can route Jellyfin downloads through the existing
     * server-side `downloadRemoteSubtitle` + media-detail poll (Jellyfin's
     * download is not a byte stream, unlike the external providers). External
     * providers leave this null and resolve bytes via their own download path.
     */
    val jellyfinInfo: com.raulshma.jellyplay.core.model.RemoteSubtitleInfo? = null,
)

/**
 * Provider-agnostic search request built from a [com.raulshma.jellyplay.core.model.MediaDetail].
 *
 * [tmdbId] / [imdbId] are preferred (highest precision) and are what Wyzie and
 * OpenSubtitles key on. [query] is the title-based fallback used when the item
 * carries no provider ids (e.g. a library item the server never matched to a
 * metadata provider). [languages] are ISO 639-3 codes — the canonical internal
 * form — and may be empty to mean "any language". [season] / [episode] are
 * provided together for TV episodes.
 */
@Immutable
@Serializable
data class SubtitleQuery(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val query: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val languages: List<String> = emptyList(),
    val hearingImpaired: Boolean? = null,
)

/**
 * The downloaded subtitle file. [format] is the codec-ish extension
 * (srt / ass / vtt …) used to map a MIME type when side-loading into the player
 * engine; [language] is the ISO 639-3 code for labelling/selection.
 */
@Immutable
data class SubtitleFile(
    val bytes: ByteArray,
    val fileName: String,
    val format: String?,
    val language: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubtitleFile) return false
        return fileName == other.fileName &&
            format == other.format &&
            language == other.language &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (format?.hashCode() ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        return result
    }
}
