package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Pure resolver for the season tab a series detail screen should land on.
 *
 * Replaces the inline `when` that used to live in `SeasonsSection` so the
 * precedence rules are unit-testable without Compose. Has no side effects and
 * depends only on `core.model` types — deliberately free of any Android /
 * Compose import.
 *
 * Precedence is **hybrid**: an active resume beats the persisted season, but a
 * non-resume smart-play target does NOT (the user's last-browsed season wins).
 * Concretely:
 *
 * 1. The smart-play target is a real resume (in-progress episode) → open its
 *    season so the user lands on the episode they're actually watching.
 * 2. Else, if the user previously pinned a season for this series → reopen it.
 * 3. Else, if the smart-play target resolved a season (next-up / play / replay,
 *    none of which are an active resume) → fall back to it.
 * 4. Else, if the screen was opened from an episode (currentSeasonId) → that
 *    season.
 * 5. Else → the first season.
 *
 * Unresolved ids (not present in [seasons]) coerce to index 0 via
 * `coerceAtLeast(0)`, matching the prior inline behaviour.
 */
internal object SeasonStartResolver {

    fun resolveInitialSeasonIndex(
        seasons: List<MediaItem>,
        smartTargetEpisode: MediaItem?,
        currentSeasonId: String?,
        persistedSeasonId: String?,
    ): Int {
        val smartTargetSeasonId = smartTargetEpisode?.seasonId
        // hasResume: true iff the smart-play target represents an ACTIVE resume
        // position. `SmartPlayResolver.resolveSeries` returns a RESUME_EPISODE
        // only when the chosen episode satisfies `(playbackPositionTicks ?: 0L)
        // > 0L && !isPlayed` (see SmartPlayResolver.hasResumeProgress). We mirror
        // that exact predicate here against the target episode so the two
        // definitions cannot drift apart: a NEXT_UP / PLAY / REPLAY target has
        // position 0 (or is fully played) and therefore does NOT override the
        // user's pinned season.
        val hasResume = smartTargetEpisode != null &&
            (smartTargetEpisode.playbackPositionTicks ?: 0L) > 0L &&
            !smartTargetEpisode.isPlayed
        return when {
            hasResume && smartTargetSeasonId != null ->
                seasons.indexOfFirst { it.id == smartTargetSeasonId }.coerceAtLeast(0)
            persistedSeasonId != null ->
                seasons.indexOfFirst { it.id == persistedSeasonId }.coerceAtLeast(0)
            smartTargetSeasonId != null ->
                seasons.indexOfFirst { it.id == smartTargetSeasonId }.coerceAtLeast(0)
            currentSeasonId != null ->
                seasons.indexOfFirst { it.id == currentSeasonId }.coerceAtLeast(0)
            else -> 0
        }
    }
}
