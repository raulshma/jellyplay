package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.AppliedMutation
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator

/**
 * Behavior fake for [UserDataMutator], shared by the details tests. Records
 * every call and mimics the real module's success path — container rewrite,
 * provider session rewrite, series-catalogue drop, provider season rewrite —
 * so ViewModel-level flip/re-entry assertions keep driving the same observable
 * sequence without re-implementing the protocol per test. Results are
 * stubbable per test; a stubbed failure skips every rewrite, like the module
 * (the protocol itself is pinned by UserDataMutatorTest in :core:data).
 */
internal class FakeUserDataMutator(
    private val provider: MediaDetailProvider? = null,
) : UserDataMutator {

    val playedCalls = mutableListOf<Triple<String, Boolean, String?>>()
    val favoriteCalls = mutableListOf<Pair<String, String?>>()
    val seasonCalls = mutableListOf<Triple<String, String, Boolean>>()

    /** Stub hooks; defaults mirror a successful resolved mutation. */
    var playedResult: (itemId: String, played: Boolean) -> Result<AppliedMutation> =
        { itemId, played -> Result.success(AppliedMutation(itemId, played = played)) }
    var favoriteResult: (itemId: String) -> Result<AppliedMutation> =
        { itemId -> Result.success(AppliedMutation(itemId, favorite = true)) }
    var seasonResult: (seriesId: String, seasonId: String, played: Boolean) -> Result<AppliedMutation> =
        { _, seasonId, played -> Result.success(AppliedMutation(seasonId, played = played)) }

    override suspend fun setPlayed(
        itemId: String,
        played: Boolean,
        mode: UserDataMutator.FlipMode,
        containers: List<UserDataContainer>,
        seriesId: String?,
    ): Result<AppliedMutation> {
        playedCalls += Triple(itemId, played, seriesId)
        return playedResult(itemId, played)
            .onSuccess { applied -> applyOptimistically(itemId, applied, mode, containers, seriesId) }
    }

    override suspend fun setFavorite(
        itemId: String,
        mode: UserDataMutator.FlipMode,
        containers: List<UserDataContainer>,
        seriesId: String?,
    ): Result<AppliedMutation> {
        favoriteCalls += itemId to seriesId
        return favoriteResult(itemId)
            .onSuccess { applied -> applyOptimistically(itemId, applied, mode, containers, seriesId) }
    }

    override suspend fun setSeasonPlayed(
        seriesId: String,
        seasonId: String,
        played: Boolean,
    ): Result<AppliedMutation> {
        seasonCalls += Triple(seriesId, seasonId, played)
        return seasonResult(seriesId, seasonId, played)
            .onSuccess { applied ->
                provider?.applyOptimisticSeasonRewrite(seriesId, seasonId) { episodes ->
                    episodes.map(applied::patch)
                }
            }
    }

    private suspend fun applyOptimistically(
        itemId: String,
        applied: AppliedMutation,
        mode: UserDataMutator.FlipMode,
        containers: List<UserDataContainer>,
        seriesId: String?,
    ) {
        if (mode == UserDataMutator.FlipMode.Silent) return
        containers.forEach { it.rewrite(itemId, applied::patch) }
        provider?.applyOptimisticItemState(itemId, applied.favorite, applied.played)
        if (seriesId != null) provider?.invalidate(seriesId)
    }
}
