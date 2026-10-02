package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.model.progressFraction
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks

/**
 * The episode row's watch-state presentation — the decisions [EpisodeCard]
 * and `CompactEpisodeRow` used to hand-copy in parallel (the compact row
 * mirrors the card's semantics by contract). Both layouts now read this fold
 * instead of re-deriving it, so the two can't drift; they keep their own
 * geometry (sizes, paddings, alignment) and chrome.
 *
 * Owns:
 *  - the dim rule ([isDimmed]) — played OR virtual (missing/unaired) rows
 *    recede behind real episodes;
 *  - which thumbnail overlays show — play affordance (suppressed on a
 *    virtual episode: there is no file to play behind it), the virtual
 *    badge, the progress overlay ([progressFraction]) vs the played bar
 *    ([showPlayedBar]), the watched tag (preference-gated) and the
 *    downloaded-episode delete affordance (suppressed on virtual episodes:
 *    nothing on disk to delete);
 *  - the metadata lines ([remainingTime] / [totalTime], the last-watched
 *    relative-timestamp gate [showLastWatched]).
 *
 * Compose-free and directly unit-testable. Pure — same inputs as the former
 * inline conditions, so the rendered output is identical by construction.
 */
@Immutable
internal data class EpisodeRowPresentation(
    /** Played or virtual — the row's artwork + meta dim to read "completed/placeholder". */
    val isDimmed: Boolean,
    /** Missing/unaired placeholder episode (no playable file behind the row). */
    val isVirtual: Boolean,
    val isPlayed: Boolean,
    /** In-progress (has a non-zero saved position and not fully played). */
    val hasWatchProgress: Boolean,
    /** `episode.progressFraction()` — null when runtime/position ticks are missing or invalid. */
    val progressFraction: Float?,
    /** The 3-dp watched bar shown when there is no progress overlay and the episode is played. */
    val showPlayedBar: Boolean,
    /** In-thumbnail play affordance — suppressed on a virtual episode. */
    val showPlayAffordance: Boolean,
    /** The virtual episode's own missing/unaired badge. */
    val showVirtualBadge: Boolean,
    /** The watched checkmark tag (played AND the card-display preference is on). */
    val showWatchedTag: Boolean,
    /** The downloaded-episode delete affordance (downloaded AND not virtual). */
    val showDeleteAffordance: Boolean,
    /** Spoiler-safe placeholder instead of artwork ([hideThumbnail] passed through). */
    val showSpoilerPlaceholder: Boolean,
    /** Last-watched relative timestamp gate — any watch activity (played or a saved position). */
    val showLastWatched: Boolean,
    /** "Xm left" label; non-null only for an in-progress episode with a known runtime + position. */
    val remainingTime: String?,
    /** Formatted total runtime; null when the episode carries no runtime. */
    val totalTime: String?,
) {
    companion object {
        /**
         * Folds one episode into its row presentation. [showWatchedCheckmark]
         * comes from the composition's [com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences]
         * (a display preference, not episode state, so it is an input rather
         * than a lookup).
         */
        fun from(
            episode: MediaItem,
            hideThumbnail: Boolean,
            isDownloaded: Boolean,
            showWatchedCheckmark: Boolean = false,
        ): EpisodeRowPresentation {
            val isPlayed = episode.isPlayed
            val isVirtual = episode.isVirtual
            val hasWatchProgress = episode.hasWatchProgress
            val runtimeTicks = episode.runTimeTicks
            val positionTicks = episode.playbackPositionTicks
            return EpisodeRowPresentation(
                isDimmed = isPlayed || isVirtual,
                isVirtual = isVirtual,
                isPlayed = isPlayed,
                hasWatchProgress = hasWatchProgress,
                progressFraction = episode.progressFraction(),
                showPlayedBar = !hasWatchProgress && isPlayed,
                showPlayAffordance = !isVirtual,
                showVirtualBadge = isVirtual,
                showWatchedTag = isPlayed && showWatchedCheckmark,
                showDeleteAffordance = isDownloaded && !isVirtual,
                showSpoilerPlaceholder = hideThumbnail,
                // Same gate the rows rendered inline: any watch activity —
                // fully played, or a non-zero saved position.
                showLastWatched = isPlayed || (positionTicks != null && positionTicks > 0),
                remainingTime = if (hasWatchProgress && runtimeTicks != null && positionTicks != null) {
                    formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
                } else {
                    null
                },
                totalTime = runtimeTicks?.let(::formatDurationFromTicks),
            )
        }
    }
}
