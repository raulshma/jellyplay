package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val session: StateFlow<DetailSession?>,
    private val userDataMutator: UserDataMutator,
    private val messages: MutableSharedFlow<DetailMessage>,
    private val strings: DetailStrings,
) {
    /**
     * Last successful target by screen item/season, used ONLY while the UI
     * emission catches up — the provider's re-emission is reduced by the VM
     * asynchronously, so immediately-after taps cannot trust the episode
     * snapshot alone. Entries are recorded on success only, matching the
     * mutator's "flip only on success" contract (plan 03): a failed write
     * records nothing, so a retry of the same direction is never swallowed.
     * Once the session snapshot adopts the recorded target the entry is
     * retired, so a later same-direction tap (the user re-watched episodes
     * since) is judged against live state, not a stale record.
     */
    private val lastSuccessfulSeasonStates = mutableMapOf<Pair<String, String>, Boolean>()
    private var lastItemId: String? = null

    init {
        // Retire records the moment the session snapshot adopts their target:
        // the record's only job is bridging the async gap between mutation
        // success and the VM reducer adopting the provider's re-emission.
        scope.launch {
            session.collect { current ->
                val itemId = current?.itemId ?: return@collect
                lastSuccessfulSeasonStates.entries.retainAll { (key, target) ->
                    val (screenItem, seasonId) = key
                    screenItem != itemId ||
                        current.episodes[seasonId]?.any { !it.matchesWatchedTarget(target) } != false
                }
            }
        }
    }

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
            val current = session.value ?: return@launch
            val itemId = current.itemId
            if (lastItemId != itemId) {
                lastSuccessfulSeasonStates.clear()
                lastItemId = itemId
            }
            val currentEpisodes = current.episodes[seasonId] ?: return@launch
            val stateKey = itemId to seasonId
            // Guard against redundant flips, in three steps:
            //  1. A recorded success in the OPPOSITE direction means "all in
            //     target" is the pre-mutation view the snapshot has not
            //     adopted yet — the tap must run (rapid inverse toggles).
            //  2. Every episode already matches the target (unwatched means
            //     no resume position either) — genuinely nothing to do.
            //  3. A same-direction record that the snapshot has not adopted
            //     yet, with the snapshot still fully pre-mutation — dedupe a
            //     rapid double-tap. Any MIXED snapshot (some watched /
            //     half-played / unwatched) is state newer than the record —
            //     episode-level flips or playback since — so the flip runs.
            val recorded = lastSuccessfulSeasonStates[stateKey]
            val alreadyInTargetState = when {
                recorded == !played -> false
                currentEpisodes.all { it.matchesWatchedTarget(played) } -> true
                recorded == played && currentEpisodes.none { it.matchesWatchedTarget(played) } -> true
                else -> false
            }
            if (alreadyInTargetState) return@launch

            // The series screen knows both ids, so the season-aware mutation
            // owns the parent-series scope (a bare mutation cannot resolve the
            // parent — seasons are never detail-cached). Null seriesId falls
            // back to the screen item: seasons only render on a series screen,
            // where the two are the same id, so this only fires mid-navigation
            // (defensive) and at worst drops one extra catalogue entry.
            val seriesId = current.seriesId ?: itemId
            userDataMutator.setSeasonPlayed(seriesId, seasonId, played)
                .onSuccess { lastSuccessfulSeasonStates[stateKey] = played }
                .onFailure {
                    messages.tryEmit(
                        DetailMessage.Text(
                            strings.get(
                                if (played) R.string.detail_msg_couldnt_mark_played
                                else R.string.detail_msg_couldnt_mark_unplayed
                            )
                        )
                    )
                }
        }
    }

    /**
     * Whether an episode is already in the state a season flip would put it
     * in. Watched is [MediaItem.isPlayed] alone; unwatched also requires a
     * clean resume point — a half-played episode is NOT unwatched (the flip
     * must clear its progress), matching [AppliedMutation]'s patch, which
     * zeroes the position on every played flip.
     */
    private fun MediaItem.matchesWatchedTarget(played: Boolean): Boolean =
        if (played) isPlayed else !isPlayed && (playbackPositionTicks ?: 0L) == 0L
}
