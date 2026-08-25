package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.details.generated.resources.Res

/**
 * Deep module: the smart-play target decision — which episode a "Play" press on
 * a series/episode detail screen should start, and from what position.
 *
 * Previously this logic lived inline as three private methods on the 1343-LOC
 * [DetailViewModel], interleaved with `_uiState` reads, `context.getString`
 * resource lookups, and `launch(Dispatchers.Default)` dispatching. The decision
 * rules (resume > next-unplayed > replay-first) were therefore untestable
 * except through the VM's async surface — which is exactly why the test needs a
 * polling helper. Extracting the pure decision here gives the rules a home and
 * a direct (synchronous, reflection-free) test surface.
 *
 * The resolver returns a [LabelKind] rather than a pre-formatted string so it
 * stays pure; the ViewModel maps the kind to the localized resource.
 */
internal data class SmartPlayResult(
    val episode: MediaItem,
    val label: LabelKind,
    val startPositionTicks: Long,
)

/**
 * Which localized label the smart-play button should show. The VM resolves each
 * to a `Res.string.detail_*` resource formatted with the season/episode numbers.
 */
enum class LabelKind { RESUME_EPISODE, NEXT_UP_EPISODE, PLAY_EPISODE, REPLAY_EPISODE }

internal object SmartPlayResolver {

    /**
     * Series-level decision: given the playback-sorted episode list, pick the
     * target. Returns null when there are no episodes to play.
     *
     * Resolution order:
     *   1. First episode with resume progress (>0 ticks, not finished).
     *   2. First unplayed episode — "Next up" if something before it was
     *      watched/started, else plain "Play".
     *   3. All played → replay the first episode from 0.
     */
    fun resolveSeries(sortedEpisodes: List<MediaItem>): SmartPlayResult? {
        if (sortedEpisodes.isEmpty()) return null

        val resumeEpisode = sortedEpisodes.firstOrNull { it.hasResumeProgress() }
        if (resumeEpisode != null) {
            return SmartPlayResult(
                episode = resumeEpisode,
                label = LabelKind.RESUME_EPISODE,
                startPositionTicks = resumeEpisode.playbackPositionTicks ?: 0L,
            )
        }

        val nextEpisode = sortedEpisodes.firstOrNull { !it.isPlayed }
        if (nextEpisode != null) {
            val hasWatchedBefore = sortedEpisodes
                .takeWhile { it.id != nextEpisode.id }
                .any { it.isPlayed || (it.playbackPositionTicks ?: 0L) > 0L }
            return SmartPlayResult(
                episode = nextEpisode,
                label = if (hasWatchedBefore) LabelKind.NEXT_UP_EPISODE else LabelKind.PLAY_EPISODE,
                startPositionTicks = 0L,
            )
        }

        // All episodes played — replay the first.
        val first = sortedEpisodes.first()
        return SmartPlayResult(
            episode = first,
            label = LabelKind.REPLAY_EPISODE,
            startPositionTicks = 0L,
        )
    }

    /**
     * Episode-level decision: the current episode is the target; the label is
     * "Resume" when it has in-progress position, else "Play".
     */
    fun resolveEpisode(currentEpisode: MediaItem): SmartPlayResult =
        SmartPlayResult(
            episode = currentEpisode,
            label = if (currentEpisode.hasResumeProgress()) LabelKind.RESUME_EPISODE else LabelKind.PLAY_EPISODE,
            startPositionTicks = currentEpisode.playbackPositionTicks ?: 0L,
        )
}

/**
 * The single definition of "this episode is mid-playback": a positive resume
 * position on an episode not yet fully watched. Shared by [SmartPlayResolver]
 * and [SeasonStartResolver] so the smart-play and season-tab decisions can't
 * drift apart on what counts as a resume.
 */
internal fun MediaItem.hasResumeProgress(): Boolean =
    (playbackPositionTicks ?: 0L) > 0L && !isPlayed
