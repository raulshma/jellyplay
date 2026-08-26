package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadStatus

/**
 * The SubtitleManager sheet's download/search state — the slice owned by
 * [com.raulshma.jellyplay.feature.player.video.SubtitleManager]. The download
 * list ([remoteSubtitles]) and search list ([searchedSubtitles]) are kept
 * separate so tab switches never clobber each other.
 *
 * `subtitleStyle` deliberately does NOT live here: it has three writers
 * (SettingsProjector + the ViewModel's subtitle-delay/style setters), so it
 * stays a session/prefs-mirror field on [com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState].
 *
 * [providerSearchResults] holds the merged cross-provider search (Jellyfin +
 * Wyzie + OpenSubtitles), tagged with provenance so the Search tab can render
 * provider filter chips and per-row badges. [providerSearchErrors] carries a
 * per-provider failure message so one bad key surfaces as a chip rather than
 * blanking the whole list. [configuredSubtitleProviders] drives whether the
 * provider chips are shown at all (hidden when only Jellyfin is configured).
 */
@Immutable
data class SubtitleState(
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val subtitleCultures: List<CultureInfo> = emptyList(),
    val searchedSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isSearchingSubtitles: Boolean = false,
    val hasSearchedSubtitles: Boolean = false,
    val subtitleSearchError: String? = null,
    val isUploadingSubtitle: Boolean = false,
    val isLoadingRemoteSubtitles: Boolean = false,
    val defaultSearchLanguage: String = "eng",
    /** Per-subtitle-id download status for the "Get Subtitles" sheet. */
    val downloadingSubtitles: Map<String, SubtitleDownloadStatus> = emptyMap(),
    /** Merged cross-provider search results (Jellyfin + external providers). */
    val providerSearchResults: List<SubtitleSearchResult> = emptyList(),
    /** Per-provider failure message from the last multi-provider search. */
    val providerSearchErrors: Map<SubtitleProviderKind, String> = emptyMap(),
    /** Providers the user has configured (drives chip visibility). */
    val configuredSubtitleProviders: Set<SubtitleProviderKind> = emptySet(),
)
