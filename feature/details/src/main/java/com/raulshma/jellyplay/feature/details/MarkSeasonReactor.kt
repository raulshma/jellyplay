package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watched-state reactor for season mark-played actions extracted from
 * [DetailViewModel]. Holds no UI state of its own and owns none of the
 * mutation protocol: the write, serialization, the optimistic provider-season
 * rewrite, and the series-catalogue drop all live in [UserDataMutator]
 * ([UserDataMutator.setSeasonPlayed]). What stays here is the screen-stateful
 * part — the idempotence guard ([lastSuccessfulSeasonStates]) and the
 * "episodes for this season are on screen" early-exit — plus the localized
 * failure message, which belongs to feature code.
 */
internal class MarkSeasonReactor(
    private val scope: CoroutineScope,
    private val userDataMutator: UserDataMutator,
    private val context: Context,
    private val itemIdProvider: () -> String?,
    private val episodesProvider: () -> Map<String, List<MediaItem>>,
    private val messageSink: (DetailMessage) -> Unit,
    private val seriesIdProvider: () -> String? = { null },
) {
    /**
     * Last successful target by screen item/season, used while UI emissions
     * catch up — the provider's re-emission is reduced by the VM
     * asynchronously, so the guard cannot trust the episode snapshot alone.
     * Entries are recorded on success only, matching the mutator's
     * "flip only on success" contract (plan 03): a failed write records
     * nothing, so a retry of the same direction is never swallowed.
     */
    private val lastSuccessfulSeasonStates = mutableMapOf<Pair<String, String>, Boolean>()
    private var lastItemId: String? = null

    /**
     * Marks every episode in [seasonId] as played. Jellyfin's `markPlayedItem`
     * endpoint recurses into a season's children, so this is a single network
     * call — but the UI needs the optimistic in-place flip so every `EpisodeCard`
     * shows the WATCHED badge and the Play button target recomputes without
     * waiting on a re-fetch. The flip itself is the mutator's provider-season
     * rewrite (re-emits a new-generation snapshot; the VM's reducer adopts the
     * rewritten episodes and recomputes smart-play). No post-mutation server
     * refetch — the optimistic flip holds the correct post-mutation state for
     * this screen.
     */
    fun markSeasonPlayed(seasonId: String) {
        markSeason(seasonId, played = true)
    }

    fun markSeasonUnplayed(seasonId: String) {
        markSeason(seasonId, played = false)
    }

    private fun markSeason(seasonId: String, played: Boolean) {
        scope.launch {
            val itemId = itemIdProvider() ?: return@launch
            if (lastItemId != itemId) {
                lastSuccessfulSeasonStates.clear()
                lastItemId = itemId
            }
            val currentEpisodes = episodesProvider()[seasonId] ?: return@launch
            val stateKey = itemId to seasonId
            // Prefer the last successful target while the provider emission
            // is still being reduced by the VM. This keeps rapid inverse
            // actions from using the pre-action UI snapshot and returning
            // early incorrectly.
            val alreadyInTargetState = lastSuccessfulSeasonStates[stateKey]?.let { it == played }
                ?: currentEpisodes.all { it.isPlayed == played }
            if (alreadyInTargetState) return@launch

            // The series screen knows both ids, so the season-aware mutation
            // owns the parent-series scope (a bare mutation cannot resolve the
            // parent — seasons are never detail-cached). Null seriesId falls
            // back to the screen item: seasons only render on a series screen,
            // where the two are the same id, so this only fires mid-navigation
            // (defensive) and at worst drops one extra catalogue entry.
            val seriesId = seriesIdProvider() ?: itemId
            userDataMutator.setSeasonPlayed(seriesId, seasonId, played)
                .onSuccess { lastSuccessfulSeasonStates[stateKey] = played }
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
