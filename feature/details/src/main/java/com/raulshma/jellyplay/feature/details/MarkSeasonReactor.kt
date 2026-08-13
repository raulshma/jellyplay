package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Watched-state reactor for season/row/episode mark-played actions extracted
 * from [DetailViewModel]. Holds no UI state of its own: the optimistic
 * season-level flip routes back through [applyRewrite] (bound by the VM to the
 * provider's optimistic-rewrite seam, which re-emits a new-generation snapshot
 * the VM's reducer adopts); single-item (row/episode) toggles route through the
 * VM callback so both the active projections and the provider's re-entry state
 * are updated.
 */
internal class MarkSeasonReactor(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val context: Context,
    private val itemIdProvider: () -> String?,
    private val episodesProvider: () -> Map<String, List<MediaItem>>,
    private val applyRewrite: suspend (
        itemId: String,
        seasonId: String,
        transform: (List<MediaItem>) -> List<MediaItem>,
    ) -> Unit,
    private val messageSink: (DetailMessage) -> Unit,
    private val seriesIdProvider: () -> String? = { null },
    private val onItemPlayed: suspend (itemId: String, played: Boolean, seriesId: String?) -> Unit = { _, _, _ -> },
    private val mutationMutex: Mutex = Mutex(),
) {
    /** Last successful target by screen item/season, used while UI emissions catch up. */
    private val lastSuccessfulSeasonStates = mutableMapOf<Pair<String, String>, Boolean>()
    private var lastItemId: String? = null

    /**
     * Marks every episode in [seasonId] as played. Jellyfin's `markPlayedItem`
     * endpoint recurses into a season's children, so this is a single network
     * call — but the UI needs the optimistic in-place flip so every `EpisodeCard`
     * shows the WATCHED badge and the Play button target recomputes without
     * waiting on a re-fetch.
     *
     * The optimistic rewrite is routed through [applyRewrite], which the VM
     * binds to the provider's optimistic-rewrite seam (re-emits a new-generation
     * snapshot; the reducer adopts the rewritten episodes and recomputes
     * smart-play). No post-mutation server refetch — the optimistic flip holds
     * the correct post-mutation state for this screen.
     */
    fun markSeasonPlayed(seasonId: String) {
        markSeason(seasonId, played = true)
    }

    fun markSeasonUnplayed(seasonId: String) {
        markSeason(seasonId, played = false)
    }

    private fun markSeason(seasonId: String, played: Boolean) {
        scope.launch {
            mutationMutex.withLock {
                val itemId = itemIdProvider() ?: return@withLock
                if (lastItemId != itemId) {
                    lastSuccessfulSeasonStates.clear()
                    lastItemId = itemId
                }
                val currentEpisodes = episodesProvider()[seasonId] ?: return@withLock
                val stateKey = itemId to seasonId
                // Prefer the last successful target while the provider emission
                // is still being reduced by the VM. This keeps rapid inverse
                // actions from using the pre-action UI snapshot and returning
                // early incorrectly.
                val alreadyInTargetState = lastSuccessfulSeasonStates[stateKey]?.let { it == played }
                    ?: currentEpisodes.all { it.isPlayed == played }
                if (alreadyInTargetState) return@withLock

                val result = if (played) mediaRepository.markPlayed(seasonId)
                else mediaRepository.markUnplayed(seasonId)
                result
                    .onSuccess {
                        lastSuccessfulSeasonStates[stateKey] = played
                        // markPlayed(seasonId) can't derive the parent series (seasons
                        // are never cached), so drop detailCache[seriesId] explicitly —
                        // also clears the series catalogue.
                        seriesIdProvider()?.let { mediaRepository.invalidateUserDataCaches(it) }
                        applyRewrite(itemId, seasonId) { episodes ->
                            episodes.map { episode ->
                                // The mark-played/unplayed endpoints clear the resume
                                // position server-side; mirror that locally for BOTH
                                // directions.
                                episode.copy(
                                    isPlayed = played,
                                    playbackPositionTicks = 0L,
                                )
                            }
                        }
                    }
                    .onFailure {
                        messageSink(
                            DetailMessage.Text(
                                context.getString(
                                    if (played) R.string.detail_msg_couldnt_mark_played
                                    else R.string.detail_msg_couldnt_mark_unplayed
                                )
                            )
                        )
                    }
            }
        }
    }

    /**
     * Marks a single episode played/unplayed (offline-aware + outboxed via
     * PlayedStateSync). The callback applies the optimistic active-session
     * rewrite and drops the parent series catalogue for re-entry.
     */
    fun markEpisodePlayed(episodeId: String, played: Boolean) {
        val seriesId = seriesIdProvider()
        scope.launch {
            mutationMutex.withLock {
                val result = if (played) mediaRepository.markPlayed(episodeId)
                else mediaRepository.markUnplayed(episodeId)
                result.onSuccess {
                    onItemPlayed(episodeId, played, seriesId)
                }
            }
        }
    }
}
