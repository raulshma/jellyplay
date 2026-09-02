package com.raulshma.jellyplay.core.data.catalogue

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * The label a smart-play / up-next surface should show for the resolved target
 * episode. Mirrors `feature.details.LabelKind` one-for-one so the catalogue
 * owns the decision while the UI keeps owning the localized string.
 */
enum class NextUpKind { RESUME, NEXT_UP, PLAY, REPLAY, NONE }

/**
 * The pure next-up / adjacency decision over a playback-sorted episode list.
 *
 * This is the catalogue-side twin of `feature.details.SmartPlayResolver`: a
 * verbatim port of `SmartPlayResolver.resolveSeries` (resume > next-unplayed >
 * replay-first) plus the prev/next-by-sorted-order helpers the player needs.
 * It lives in `core:data` (below the feature layer) so the catalogue can expose
 * a [NextEpisode] from [EpisodeCatalogue.loadSeriesEpisodes] without forcing the
 * player to depend on `feature.details`, and so the decision has a synchronous,
 * reflection-free test surface of its own.
 *
 * @param kind the [NextUpKind] label; [NextUpKind.NONE] with a null episode when
 *   the sorted list is empty.
 * @param episode the resolved target, or null when there is nothing to play.
 * @param startPositionTicks the position to start at (the resume ticks for a
 *   RESUME target, 0 otherwise).
 */
@Immutable
data class NextEpisode(
    val kind: NextUpKind,
    val episode: MediaItem?,
    val startPositionTicks: Long,
) {
    companion object {
        /** A no-target sentinel for an empty/unresolved catalogue. */
        val NONE = NextEpisode(NextUpKind.NONE, null, 0L)

        /**
         * Series-level decision over a playback-sorted list, a verbatim port of
         * `SmartPlayResolver.resolveSeries`. Returns [NONE] when [sorted] is
         * empty.
         *
         * Resolution order:
         *   1. First episode with resume progress (>0 ticks, not finished).
         *   2. First unplayed episode — NEXT_UP when something before it was
         *      watched/started, else plain PLAY.
         *   3. All played → REPLAY the first from 0.
         */
        fun forSorted(sorted: List<MediaItem>): NextEpisode {
            if (sorted.isEmpty()) return NONE

            val resumeEpisode = sorted.firstOrNull { it.hasResumeProgress() }
            if (resumeEpisode != null) {
                return NextEpisode(
                    kind = NextUpKind.RESUME,
                    episode = resumeEpisode,
                    startPositionTicks = resumeEpisode.playbackPositionTicks ?: 0L,
                )
            }

            val nextEpisode = sorted.firstOrNull { !it.isPlayed }
            if (nextEpisode != null) {
                val hasWatchedBefore = sorted
                    .takeWhile { it.id != nextEpisode.id }
                    .any { it.isPlayed || (it.playbackPositionTicks ?: 0L) > 0L }
                return NextEpisode(
                    kind = if (hasWatchedBefore) NextUpKind.NEXT_UP else NextUpKind.PLAY,
                    episode = nextEpisode,
                    startPositionTicks = 0L,
                )
            }

            // All episodes played — replay the first.
            val first = sorted.first()
            return NextEpisode(
                kind = NextUpKind.REPLAY,
                episode = first,
                startPositionTicks = 0L,
            )
        }

        /**
         * Per-season adjacency helpers for the player's prev/next controls:
         * returns `(previous, next)` by sorted order around [currentId]. Either
         * side is null at the list boundary or when [currentId] isn't present.
         */
        fun neighbors(sorted: List<MediaItem>, currentId: String): Pair<MediaItem?, MediaItem?> {
            val index = sorted.indexOfFirst { it.id == currentId }
            if (index < 0) return null to null
            val previous = sorted.getOrNull(index - 1)
            val next = sorted.getOrNull(index + 1)
            return previous to next
        }

        private fun MediaItem.hasResumeProgress(): Boolean =
            (playbackPositionTicks ?: 0L) > 0L && !isPlayed
    }
}
