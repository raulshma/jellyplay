package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam for user-data mutations (watched / favorite). Owns the
 * protocol: serialize → repository call (PlayedStateSync fan-out via
 * [MediaRepository]: server, offline mirror, outbox) → on success: rewrite the
 * caller's exposed containers, rewrite the active provider session, drop the
 * residual series catalogue. Flips only on success; there is no pre-call guess
 * and no revert path (PlayedStateSync stages offline mutations in the outbox
 * and reports success, so failure reaching a caller is exceptional and is
 * surfaced through the [Result] — the caller decides message vs silent).
 *
 * Previously "mark this item watched / favorite this item" was re-implemented
 * per ViewModel with slightly divergent semantics (flip scope, resume
 * handling, serialization, invalidation). Flip-vs-silent is now a parameter of
 * one interface: the paged grid screens' "no in-place flip" ([FlipMode.Silent],
 * the default — `PagingData` cannot be edited in place) is a mode, not a fork
 * of the logic, and a new caller must explicitly opt into optimism.
 *
 * Plan-08 note: the repository's public invalidation knobs
 * (`invalidateDetailCache` / `invalidateCollectionItemsCache` /
 * `invalidateUserDataCaches`) are gone — writes self-invalidate inside
 * [MediaRepositoryImpl]. This module's residual invalidation surface is
 * therefore exactly the provider's own reactivity seams: the active-session
 * rewrite ([MediaDetailProvider.applyOptimisticItemState] /
 * [applyOptimisticSeasonRewrite]) and the series-catalogue drop
 * ([MediaDetailProvider.invalidate]) for episode mutations, which the write
 * path cannot know is wanted. The plan's `ParentScope` parameter was shrunk
 * away entirely for this reason — it had nothing left to trigger.
 */
interface UserDataMutator {

    /**
     * Whether a successful write rewrites the caller's containers and the
     * active provider session. [Silent] (the default) is the grid-screen
     * contract: the write lands, the visible list is untouched, the badge
     * updates on the next natural data refresh. [Optimistic] is the
     * detail/home/collection/person contract: post-success in-place flip
     * without a refetch (NOT a pre-call guess — see the interface doc).
     */
    enum class FlipMode { Optimistic, Silent }

    /**
     * Sets the watched state of a single item.
     *
     * @param seriesId the parent series of [itemId] when the item belongs to
     * one (episode cards on a series screen). Drives the series-catalogue drop
     * so re-entry refetches the post-cascade state; null (or a non-series
     * item) skips it. The repository-side caches are invalidated by the write
     * itself regardless.
     */
    suspend fun setPlayed(
        itemId: String,
        played: Boolean,
        mode: FlipMode = FlipMode.Silent,
        containers: List<UserDataContainer> = emptyList(),
        seriesId: String? = null,
    ): Result<AppliedMutation>

    /**
     * Toggles the favorite state of a single item. The resolved target (server
     * view when online, local row when offline — see
     * [PlayedStateSync.toggleFavorite]) is returned as
     * [AppliedMutation.favorite] so the caller's flip is correct regardless of
     * path, even in [FlipMode.Silent] (the audio player's scalar case).
     */
    suspend fun setFavorite(
        itemId: String,
        mode: FlipMode = FlipMode.Silent,
        containers: List<UserDataContainer> = emptyList(),
        seriesId: String? = null,
    ): Result<AppliedMutation>

    /**
     * Season-level watched flip. One repository call on [seasonId] (Jellyfin's
     * mark-played endpoint recurses into the season's children); the series
     * screen knows both ids by construction, so it supplies [seriesId] — a
     * season id cannot resolve its parent series from any cache. On success
     * the active series session is rewritten wholesale via
     * [MediaDetailProvider.applyOptimisticSeasonRewrite] (which re-emits the
     * flipped episodes through [MediaDetailProvider.observe] and drops the
     * catalogue for re-entry itself); callers that also expose the episodes in
     * their own state receive the resolved [AppliedMutation] to mirror.
     */
    suspend fun setSeasonPlayed(
        seriesId: String,
        seasonId: String,
        played: Boolean,
    ): Result<AppliedMutation>
}

/**
 * Screen adapter: "here is where my exposed items live; patch the matching
 * one." The only per-screen code the mutation protocol needs — genuine screen
 * knowledge (which projections/lists exist), while the protocol (write,
 * ordering, resume rule, provider rewrite, invalidation) lives in
 * [UserDataMutator].
 *
 * Contract: [rewrite] must be a plain state patch. It must NOT call back into
 * [UserDataMutator] — the module holds its serialization mutex across the
 * rewrite, and `kotlinx.coroutines.sync.Mutex` is non-reentrant. Nothing in
 * the migrated adapters needs to (they only update in-memory state), and the
 * module's ordering guarantee assumes it stays that way.
 */
fun interface UserDataContainer {
    fun rewrite(itemId: String, patch: (MediaItem) -> MediaItem)
}

/**
 * The resolved mutation. [patch] is the ONE place the resume-clearing rule
 * lives: Jellyfin clears a manually (un)watched item's resume point, so every
 * played flip (both directions) mirrors that locally — a screen must never
 * retain an in-progress bar while the mutation syncs. Favorite-only mutations
 * ([played] == null) preserve the resume point untouched.
 */
data class AppliedMutation(
    val itemId: String,
    val played: Boolean? = null,
    /** Server/local-resolved favorite target (only set by [UserDataMutator.setFavorite]). */
    val favorite: Boolean? = null,
) {
    fun patch(item: MediaItem): MediaItem = item.copy(
        isPlayed = played ?: item.isPlayed,
        playbackPositionTicks = if (played != null) 0L else item.playbackPositionTicks,
        isFavorite = favorite ?: item.isFavorite,
    )
}

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
