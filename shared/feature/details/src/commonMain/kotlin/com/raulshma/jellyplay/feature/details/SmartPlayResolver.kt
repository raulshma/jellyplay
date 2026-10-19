package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.catalogue.NextEpisode
import com.raulshma.jellyplay.core.data.catalogue.NextUpKind
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.hasWatchProgress
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
 * polling helper. Extracting the pure decision here gave the rules a home and
 * a direct (synchronous, reflection-free) test surface.
 *
 * The series-level rules' single owner is `core:data`'s
 * [NextEpisode.forSorted] (shared with the catalogue and the player);
 * [SmartPlayResolver.resolveSeries] delegates to it and only maps the
 * catalogue's [NextUpKind] to this file's [LabelKind] — the two enums mirror
 * one another one-for-one. The resolver returns a [LabelKind] rather than a
 * pre-formatted string so it stays pure; the ViewModel maps the kind to the
 * localized resource.
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
     * Delegates to [NextEpisode.forSorted] — the rule's single owner
     * (resolution order there: 1. first episode with resume progress; 2. first
     * unplayed episode, NEXT_UP when something before it was watched/started
     * else PLAY; 3. all played → REPLAY the first; virtual episodes never
     * candidates) — and maps the [NextUpKind] verdict onto [LabelKind].
     */
    fun resolveSeries(sortedEpisodes: List<MediaItem>): SmartPlayResult? {
        val decision = NextEpisode.forSorted(sortedEpisodes)
        val episode = decision.episode ?: return null
        return SmartPlayResult(
            episode = episode,
            label = decision.kind.toLabelKind(),
            startPositionTicks = decision.startPositionTicks,
        )
    }

    /**
     * Episode-level decision: the current episode is the target; the label is
     * "Resume" when it has in-progress position, else "Play".
     */
    fun resolveEpisode(currentEpisode: MediaItem): SmartPlayResult =
        SmartPlayResult(
            episode = currentEpisode,
            label = if (currentEpisode.hasWatchProgress) LabelKind.RESUME_EPISODE else LabelKind.PLAY_EPISODE,
            startPositionTicks = currentEpisode.playbackPositionTicks ?: 0L,
        )

    /**
     * Season-level decision (#168): a Play press on a SEASON detail entry
     * resumes/continues WITHIN that season only — the same resolution order as
     * [resolveSeries] applied to just the entry season's episodes. [seasonId]
     * is the detail entry's own id; episodes carry their season in
     * [MediaItem.seasonId]. Returns null when the season has no (non-virtual)
     * episodes in the current snapshot — the caller clears the smart target
     * and the Play button falls back to its non-smart path.
     */
    fun resolveSeason(seasonId: String, sortedEpisodes: List<MediaItem>): SmartPlayResult? =
        resolveSeries(sortedEpisodes.filter { it.seasonId == seasonId })

    /** The catalogue verdict → the UI label (the two enums' one-for-one mirror, in one table). */
    private fun NextUpKind.toLabelKind(): LabelKind = when (this) {
        NextUpKind.RESUME -> LabelKind.RESUME_EPISODE
        NextUpKind.NEXT_UP -> LabelKind.NEXT_UP_EPISODE
        NextUpKind.PLAY -> LabelKind.PLAY_EPISODE
        NextUpKind.REPLAY -> LabelKind.REPLAY_EPISODE
        // Unreachable: NONE always travels with a null episode (resolved above).
        NextUpKind.NONE -> LabelKind.PLAY_EPISODE
    }
}
