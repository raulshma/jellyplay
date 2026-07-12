package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * Subtitle styling + the SubtitleManager sheet's download/search state. The
 * download list ([remoteSubtitles]) and search list ([searchedSubtitles]) are
 * kept separate so tab switches never clobber each other.
 */
@Immutable
data class SubtitleState(
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val subtitleCultures: List<CultureInfo> = emptyList(),
    val searchedSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isSearchingSubtitles: Boolean = false,
    val hasSearchedSubtitles: Boolean = false,
    val subtitleSearchError: String? = null,
    val isUploadingSubtitle: Boolean = false,
    val isLoadingRemoteSubtitles: Boolean = false,
    val defaultSearchLanguage: String = "eng",
)
