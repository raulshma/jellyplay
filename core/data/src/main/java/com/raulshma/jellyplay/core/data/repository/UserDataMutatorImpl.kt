package com.raulshma.jellyplay.core.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// C4p2 note: the [UserDataMutator] interface + [UserDataContainer] +
// [AppliedMutation] moved to :shared:core:data commonMain; this impl stays in
// the legacy module because Koin cannot construct it yet — its
// `dagger.Lazy<MediaRepository>` / `dagger.Lazy<MediaDetailProvider>` params
// need Koin definitions for those interfaces, and their impls
// (MediaRepositoryImpl, UnifiedMediaDetailProviderImpl) are still Hilt-owned
// legacy (their ctors take Android-coupled NetworkMonitor / OfflineModeManager
// / PlaybackSourceResolver). It migrates when those repository impls do.

/**
 * Production adapter over [MediaRepository] (the write — including
 * PlayedStateSync fan-out and repository self-invalidation) and
 * [MediaDetailProvider] (the active-session rewrite). Both are
 * [dagger.Lazy] for the same reason [PlayedStateSyncImpl] takes them: the
 * provider and repository reference each other's graphs, and deferring
 * construction keeps this module out of any DI cycle.
 */
@Singleton
class UserDataMutatorImpl @Inject constructor(
    private val mediaRepository: dagger.Lazy<MediaRepository>,
    private val mediaDetailProvider: dagger.Lazy<MediaDetailProvider>,
) : UserDataMutator {

    /**
     * Serializes every user-data mutation app-wide (rapid taps resolve in
     * input order). Replaces the per-ViewModel mutexes only Detail used to
     * hold; the grid screens gain serialization they never had. Non-reentrant
     * — see the [UserDataContainer] contract.
     */
    private val userDataMutationMutex = Mutex()

    override suspend fun setPlayed(
        itemId: String,
        played: Boolean,
        mode: UserDataMutator.FlipMode,
        containers: List<UserDataContainer>,
        seriesId: String?,
    ): Result<AppliedMutation> = userDataMutationMutex.withLock {
        writePlayed(itemId, played)
            .map { AppliedMutation(itemId = itemId, played = played) }
            .onSuccess { applied -> applyOptimistically(itemId, applied, mode, containers, seriesId) }
    }

    override suspend fun setFavorite(
        itemId: String,
        mode: UserDataMutator.FlipMode,
        containers: List<UserDataContainer>,
        seriesId: String?,
    ): Result<AppliedMutation> = userDataMutationMutex.withLock {
        mediaRepository.get().toggleFavorite(itemId)
            .map { target -> AppliedMutation(itemId = itemId, favorite = target) }
            .onSuccess { applied -> applyOptimistically(itemId, applied, mode, containers, seriesId) }
    }

    override suspend fun setSeasonPlayed(
        seriesId: String,
        seasonId: String,
        played: Boolean,
    ): Result<AppliedMutation> = userDataMutationMutex.withLock {
        val applied = AppliedMutation(itemId = seasonId, played = played)
        writeSeasonPlayed(seriesId, seasonId, played)
            .map { applied }
            .onSuccess {
                // The whole-season flip is one catalogue-level fact, not a
                // per-item one: the provider's season rewrite re-emits every
                // episode of the season for the active series session (the
                // reducer adopts the new generation and recomputes smart-play)
                // and drops the series catalogue for re-entry itself.
                mediaDetailProvider.get().applyOptimisticSeasonRewrite(seriesId, seasonId) { episodes ->
                    episodes.map(applied::patch)
                }
            }
    }

    /** Repository call for the played direction, shared by [setPlayed] and [setSeasonPlayed]. */
    private suspend fun writePlayed(itemId: String, played: Boolean): Result<Unit> =
        if (played) mediaRepository.get().markPlayed(itemId)
        else mediaRepository.get().markUnplayed(itemId)

    /** Season variant of [writePlayed] — the server recurses into the season's children. */
    private suspend fun writeSeasonPlayed(seriesId: String, seasonId: String, played: Boolean): Result<Unit> =
        if (played) mediaRepository.get().markSeasonPlayed(seasonId, seriesId)
        else mediaRepository.get().markSeasonUnplayed(seasonId, seriesId)

    /**
     * Post-success optimistic pass, in the order callers used to hand-assemble:
     * caller containers → provider session rewrite → residual series-catalogue
     * drop. Skipped entirely in [UserDataMutator.FlipMode.Silent] and on write
     * failure (never reached) — there is no flip without a successful write,
     * so there is nothing to roll back.
     */
    private suspend fun applyOptimistically(
        itemId: String,
        applied: AppliedMutation,
        mode: UserDataMutator.FlipMode,
        containers: List<UserDataContainer>,
        seriesId: String?,
    ) {
        if (mode == UserDataMutator.FlipMode.Silent) return
        containers.forEach { it.rewrite(itemId, applied::patch) }
        // Keep the provider's replayed snapshot aligned with the caller's
        // optimistic state so leaving and immediately re-entering detail does
        // not flash the pre-mutation state (no-op without an active session).
        mediaDetailProvider.get().applyOptimisticItemState(
            itemId = itemId,
            isFavorite = applied.favorite,
            isPlayed = applied.played,
        )
        // Residual invalidation: episode mutations must not leave the series'
        // seasons/episodes catalogue serving stale watched state on the next
        // screen entry. The repository dropped its own caches around the write
        // already; this is the provider-owned catalogue only.
        if (seriesId != null) mediaDetailProvider.get().invalidate(seriesId)
    }
}
