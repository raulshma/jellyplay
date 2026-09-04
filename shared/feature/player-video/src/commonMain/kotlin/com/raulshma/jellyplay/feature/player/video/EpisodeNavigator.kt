package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long [EpisodeNavigator.next] keeps the single-flight latch held while the session settles (#146). */
internal const val NEXT_EPISODE_SETTLE_TIMEOUT_MS = 90_000L

/**
 * The episode-navigation module of the video player: season/episode browsing
 * state, adjacent-episode discovery, and the previous/next choreography —
 * extracted from [VideoPlayerViewModel] (the largest remaining behavioural
 * cluster) so the policies it owns are pinnable in jvmTest without the VM:
 *
 *  - the #146 single-flight latch: one in-flight next-episode advance at a
 *    time, held until the session *settles* (binds a different non-null item
 *    or surfaces an error), not merely until `initialize` returns;
 *  - mark-played-on-advance (the current episode was effectively watched when
 *    advancing near its end — also covers the SyncPlay branch, which bypasses
 *    [initialize][EpisodeNavigator.initializeItem] and its stopped-position
 *    report);
 *  - SyncPlay queue routing: when the group's queue holds the sibling, the
 *    advance goes through the group command instead of a local reload;
 *  - the offline branch of season/episode resolution (the injected
 *    [EpisodeCatalogue] is told offline per call, from the session state).
 *
 * The episode-browsing uiState slice stays a stored slice of
 * [VideoPlayerUiState]; this module is its single writer, through the
 * injected [updateEpisodes] seam. Autoplay/close policy on playback end stays
 * VM-side (per CONTEXT.md) — this module only owns the *verbs*.
 */
internal class EpisodeNavigator(
    private val scope: CoroutineScope,
    private val sessionState: StateFlow<PlayerSessionState>,
    private val sessionEvents: SharedFlow<SessionEvent>,
    private val getDetail: () -> MediaDetail?,
    private val getSeriesId: () -> String?,
    private val episodeCatalogue: EpisodeCatalogue,
    private val trySyncPlayNext: (nextItemId: String) -> Boolean,
    private val trySyncPlayPrevious: (previousItemId: String) -> Boolean,
    private val onAdvanceFrom: suspend (currentItemId: String) -> Unit,
    private val reportLoadError: suspend () -> Unit,
    private val initializeItem: (itemId: String, startPositionTicks: Long) -> Unit,
    private val updateEpisodes: (((EpisodeBrowserState) -> EpisodeBrowserState) -> Unit),
) {
    private val _nextEpisodeLoading = MutableStateFlow(false)

    /** True while a next-episode advance is in flight (latch held until settle). */
    val isNextEpisodeLoading: StateFlow<Boolean> = _nextEpisodeLoading.asStateFlow()

    /**
     * Loads the series' season list and the current season's episodes for
     * [detail] — the episode sheet's entry point on a fresh series load.
     */
    fun loadSeries(detail: MediaDetail) {
        val seriesId = detail.item.seriesId ?: return
        val currentSeasonId = detail.item.seasonId ?: return
        scope.launch {
            updateEpisodes { it.copy(isLoadingEpisodes = true) }
            val seasonList = resolveSeasons(seriesId)
            updateEpisodes {
                it.copy(seriesSeasons = seasonList, currentSeasonId = currentSeasonId)
            }
            loadSeason(currentSeasonId)
        }
    }

    /** Loads one season's episode list into the browsing slice (sheet season click). */
    fun loadSeason(seasonId: String) {
        val seriesId = getSeriesId() ?: return
        scope.launch {
            updateEpisodes { it.copy(isLoadingEpisodes = true) }
            val episodeList = resolveEpisodes(seriesId, seasonId)
            updateEpisodes { it.copy(
                seasonEpisodes = episodeList,
                currentSeasonId = seasonId,
                isLoadingEpisodes = false,
            ) }
        }
    }

    /** Resolves the season list for [seriesId] via the [EpisodeCatalogue]. */
    private suspend fun resolveSeasons(seriesId: String): List<JellyfinMediaItem> {
        val offline = sessionState.value.isOffline
        return episodeCatalogue.loadSeriesEpisodes(seriesId, offline)
            .getOrDefault(com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot.empty(seriesId))
            .seasons
    }

    /**
     * Adopts a freshly-bound item's season as the browsing selection — the
     * media-detail application write the VM routes here so this module stays
     * the slice's single writer.
     */
    fun adoptSeasonOf(detail: MediaDetail) {
        updateEpisodes { it.copy(currentSeasonId = detail.item.seasonId ?: it.currentSeasonId) }
    }

    /**
     * Resets the slice for an item switch: adjacency, season/episode lists,
     * season id and the loading flag are per-item; only the browser feature
     * toggle carries across.
     */
    fun resetForItemSwitch() {
        updateEpisodes { EpisodeBrowserState(videoEpisodeBrowserEnabled = it.videoEpisodeBrowserEnabled) }
    }

    /** Resolves the episode list for [seasonId] under [seriesId]; failures read as empty. */
    private suspend fun resolveEpisodes(seriesId: String, seasonId: String): List<JellyfinMediaItem> {
        val offline = sessionState.value.isOffline
        return episodeCatalogue.loadSeasonEpisodes(seriesId, seasonId, offline)
            .getOrDefault(emptyList())
    }

    /**
     * The episode [offset] spots from [currentItemId] in [episodes] (±1 for
     * the adjacent sibling), or null when the item isn't in the list or the
     * sibling falls outside it — the one shared pick behind
     * [refreshAdjacent]/[previous]/[next].
     */
    private fun siblingOf(
        episodes: List<JellyfinMediaItem>,
        currentItemId: String?,
        offset: Int,
    ): JellyfinMediaItem? {
        if (currentItemId == null) return null
        val index = episodes.indexOfFirst { it.id == currentItemId }
        if (index < 0) return null
        return episodes.getOrNull(index + offset)
    }

    /**
     * The navigation anchors the verbs share — the playing item's series,
     * season and current item id — or null when any required one is missing.
     */
    private data class EpisodeAnchor(val seriesId: String, val seasonId: String, val currentItemId: String?)

    private fun anchorOf(detail: MediaDetail?): EpisodeAnchor? {
        val seriesId = detail?.item?.seriesId ?: return null
        val seasonId = detail.item.seasonId ?: return null
        return EpisodeAnchor(seriesId, seasonId, sessionState.value.currentItemId)
    }

    /**
     * Writes the adjacent-episode snapshot (next/previous of the current item)
     * — feeds the up-next overlay and the autoplay decision.
     */
    fun refreshAdjacent(detail: MediaDetail) {
        val anchor = anchorOf(detail) ?: return
        scope.launch {
            val episodes = resolveEpisodes(anchor.seriesId, anchor.seasonId)
            val currentItemId = anchor.currentItemId
            updateEpisodes {
                it.copy(
                    nextEpisode = siblingOf(episodes, currentItemId, +1),
                    previousEpisode = siblingOf(episodes, currentItemId, -1),
                )
            }
        }
    }

    /**
     * Advances to the previous episode, resuming from its saved position.
     * Routes through the SyncPlay group when the queue holds the sibling.
     */
    fun previous() {
        val anchor = anchorOf(getDetail()) ?: return
        val currentItemId = anchor.currentItemId ?: return
        scope.launch {
            val episodes = resolveEpisodes(anchor.seriesId, anchor.seasonId)
            val previous = siblingOf(episodes, currentItemId, -1) ?: return@launch

            if (trySyncPlayPrevious(previous.id)) return@launch

            // Resume the previous episode from its saved position (mirrors the
            // episode picker), falling back to the start when none is recorded.
            initializeItem(previous.id, previous.playbackPositionTicks ?: 0L)
        }
    }

    /**
     * Advances to the next episode behind the #146 single-flight latch: the
     * latch is held from tap until the session settles on the new item (or an
     * error, or the settle timeout), so re-taps inside the window are ignored
     * rather than queued as staggered teardown+reload passes.
     */
    fun next() {
        val anchor = anchorOf(getDetail()) ?: return
        val currentItemId = anchor.currentItemId ?: return
        // Single-flight latch (#146): every tap used to launch an independent
        // resolve → mark-played → initialize chain. Offline, each stage blocked
        // on a full network timeout, so re-taps landed minutes later as
        // staggered teardown+reload passes — one visible restart per extra tap.
        if (!_nextEpisodeLoading.compareAndSet(false, true)) return
        scope.launch {
            try {
                val episodesResult = episodeCatalogue.loadSeasonEpisodes(
                    anchor.seriesId,
                    anchor.seasonId,
                    sessionState.value.isOffline,
                )
                val episodes = episodesResult.getOrElse {
                    // A failed resolution used to fall through to an empty list
                    // and silently do nothing; tell the user instead.
                    reportLoadError()
                    return@launch
                }
                val next = siblingOf(episodes, currentItemId, +1) ?: return@launch

                // Auto-advancing is only reachable near the episode's end, so the
                // current episode was effectively watched. Mark it played so it
                // drops out of Continue Watching. This also covers the SyncPlay
                // branch below, which bypasses [initializeItem] and its
                // stopped-position report.
                onAdvanceFrom(currentItemId)

                if (trySyncPlayNext(next.id)) return@launch

                initializeItem(next.id, 0L)

                // Keep holding the latch until the load actually settles — the
                // session binds a different, non-null item or an error surfaces
                // — so taps landing inside this window are ignored rather than
                // queued as a second teardown+reload of the same episode (#146).
                // The non-null guard matters: initialize()'s per-item teardown
                // resets PlayerSessionState (currentItemId = null) BEFORE the
                // pipeline rebinds the new item, and that transient must not
                // read as "settled" — without it the latch releases the instant
                // this line returns.
                withTimeoutOrNull(NEXT_EPISODE_SETTLE_TIMEOUT_MS) {
                    merge(
                        sessionState.map {
                            it.currentItemId != currentItemId && it.currentItemId != null
                        },
                        sessionEvents.map { it is SessionEvent.ShowError },
                    ).filter { settled -> settled }.first()
                }
            } finally {
                _nextEpisodeLoading.value = false
            }
        }
    }
}
