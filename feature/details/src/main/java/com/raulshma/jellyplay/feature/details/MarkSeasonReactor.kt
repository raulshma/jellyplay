package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watched-state reactor for season/row/episode mark-played actions extracted
 * from [DetailViewModel]. Holds no UI state of its own: the optimistic
 * season-level flip routes back through [applyRewrite] (bound by the VM to the
 * provider's optimistic-rewrite seam, which re-emits a new-generation snapshot
 * the VM's reducer adopts); single-item (row/episode) toggles are pure
 * fire-and-forget whose reconciliation arrives via the provider's observe() /
 * local-row reactivity.
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
) {
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
        val itemId = itemIdProvider() ?: return
        val currentEpisodes = episodesProvider()[seasonId] ?: return
        // No-op if there is nothing to flip — avoids an unnecessary network
        // call, a spurious rewrite, and a redundant emission.
        val alreadyInTargetState = currentEpisodes.all { it.isPlayed == played }
        if (alreadyInTargetState) return

        scope.launch {
            val result = if (played) mediaRepository.markPlayed(seasonId)
            else mediaRepository.markUnplayed(seasonId)
            result
                .onSuccess {
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

    /**
     * Marks a single episode played/unplayed (offline-aware + outboxed via
     * PlayedStateSync, as today). Does NOT refetch — the provider/local row
     * reactivity updates the snapshot.
     */
    fun markEpisodePlayed(episodeId: String, played: Boolean) {
        scope.launch {
            if (played) mediaRepository.markPlayed(episodeId)
            else mediaRepository.markUnplayed(episodeId)
        }
    }
}
