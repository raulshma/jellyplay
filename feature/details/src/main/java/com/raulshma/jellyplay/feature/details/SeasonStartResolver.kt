package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Pure resolver for the season tab a series detail screen should land on.
 *
 * Precedence rules:
 * 1. The smart-play target is a Next Up or Resume episode → open its season
 *    so the user lands on the episode shown in the play button.
 * 2. Else, if the user previously pinned a season for this series (persistedSeasonId) → reopen it.
 * 3. Else, if the smart-play target resolved a season (e.g. play / replay) → fall back to it.
 * 4. Else, if the screen was opened from an episode (currentSeasonId) → that season.
 * 5. Else → the first season (index 0).
 *
 * Unresolved ids (not present in [seasons]) coerce to index 0 via
 * `coerceAtLeast(0)`.
 */
internal object SeasonStartResolver {

    fun resolveInitialSeasonIndex(
        seasons: List<MediaItem>,
        smartTargetEpisode: MediaItem?,
        currentSeasonId: String? = null,
        persistedSeasonId: String? = null,
        isNextUpOrResume: Boolean = false,
    ): Int {
        if (seasons.isEmpty()) return 0

        val smartTargetIndex = findSeasonIndex(seasons, smartTargetEpisode)
        val hasResume = smartTargetEpisode?.hasResumeProgress() == true
        val shouldPrioritizeSmartTarget = isNextUpOrResume || hasResume

        return when {
            shouldPrioritizeSmartTarget && smartTargetIndex >= 0 ->
                smartTargetIndex
            persistedSeasonId != null ->
                seasons.indexOfFirst { it.id == persistedSeasonId }.coerceAtLeast(0)
            smartTargetIndex >= 0 ->
                smartTargetIndex
            currentSeasonId != null ->
                seasons.indexOfFirst { it.id == currentSeasonId }.coerceAtLeast(0)
            else -> 0
        }
    }

    fun resolveInitialSeasonIndex(
        seasons: List<MediaItem>,
        smartPlayTarget: DetailUiState.SmartPlayTarget?,
        currentSeasonId: String? = null,
        persistedSeasonId: String? = null,
    ): Int = resolveInitialSeasonIndex(
        seasons = seasons,
        smartTargetEpisode = smartPlayTarget?.episode,
        currentSeasonId = currentSeasonId,
        persistedSeasonId = persistedSeasonId,
        isNextUpOrResume = smartPlayTarget?.isNextUpOrResume == true,
    )

    private fun findSeasonIndex(seasons: List<MediaItem>, episode: MediaItem?): Int {
        if (episode == null || seasons.isEmpty()) return -1
        val seasonId = episode.seasonId ?: episode.parentId
        if (seasonId != null) {
            val idx = seasons.indexOfFirst { it.id == seasonId }
            if (idx >= 0) return idx
        }
        val seasonNumber = episode.seasonNumber
        if (seasonNumber != null) {
            val idx = seasons.indexOfFirst { (it.indexNumber ?: it.seasonNumber) == seasonNumber }
            if (idx >= 0) return idx
        }
        return -1
    }
}
