package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem

/**
 * Series/season/episode browser state for the episode-picker sheet + up-next.
 */
@Immutable
data class EpisodeBrowserState(
    val nextEpisode: JellyfinMediaItem? = null,
    /** Adjacent-episode snapshot's previous entry; written alongside [nextEpisode] by fetchAdjacentEpisodes. */
    val previousEpisode: JellyfinMediaItem? = null,
    val seriesSeasons: List<JellyfinMediaItem> = emptyList(),
    val seasonEpisodes: List<JellyfinMediaItem> = emptyList(),
    val currentSeasonId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val videoEpisodeBrowserEnabled: Boolean = true,
)
