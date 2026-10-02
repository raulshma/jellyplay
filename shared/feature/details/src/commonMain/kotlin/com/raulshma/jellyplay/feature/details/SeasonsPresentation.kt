package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * The seasons section's presentation model: every decision [SeasonsSection]
 * renders, folded once from the screen state and the entry item so the
 * section composable reads data instead of re-deriving it (the same seam
 * [SeasonStartResolver] owns for the initial-tab choice).
 *
 * Owns the four derivations [DetailSeasonsSection] used to hand-splice inline
 * before each [SeasonsSection] call:
 *  - the skip-specials episode filter ([episodes]) and the
 *    hide-thumbnails neutralization ([hideEpisodeThumbnails]) — both
 *    disabled for a LOCAL origin (see the "DEFERRED FOR LOCAL ORIGIN" note in
 *    MediaDetailSeasons);
 *  - the downloaded-episode three-way fold ([downloadedEpisodeIds]) — every
 *    episode for a local origin, the loaded remote set when non-empty, else
 *    null (which hides the per-episode delete affordance entirely);
 *  - the SEASON entry filter ([seasons]) — a SEASON page renders only its own
 *    tab so the tree matches the header;
 *  - the current-item/current-season pair ([currentItemId] /
 *    [currentSeasonId]) — EPISODE preselects its season tab and highlights
 *    its own row; SEASON preselects itself; every other media type passes
 *    nulls.
 *
 * Compose-free and directly unit-testable; the caller memoizes the fold.
 */
@Immutable
internal data class SeasonsPresentation(
    /** Season tabs to render (SEASON pages carry only the entry season). */
    val seasons: List<MediaItem>,
    /** Per-season episode lists with specials (season 0) dropped when the preference asks. */
    val episodes: Map<String, List<MediaItem>>,
    /** Spoiler-safe thumbnails; always false for a LOCAL origin (local cards always show art). */
    val hideEpisodeThumbnails: Boolean,
    /** Newest-first episode order (honored for a local origin too). */
    val episodesDescending: Boolean,
    /** Compact vertical episode list (offered only on compact-width, non-TV form factors). */
    val compactEpisodeList: Boolean,
    /**
     * Downloaded episode ids — gates the per-episode delete badge. Null hides
     * the affordance entirely (a plain remote series with no downloads).
     */
    val downloadedEpisodeIds: Set<String>?,
    /** On-disk episode thumbnails (downloaded art) preferred before the server URL. */
    val episodeLocalImagePaths: Map<String, String>,
    /** The currently-playing episode id to highlight (EPISODE pages only, else null). */
    val currentItemId: String?,
    /** The season tab to anchor (EPISODE → its season; SEASON → itself; else null). */
    val currentSeasonId: String?,
) {
    companion object {
        /**
         * Folds [state] + the entry [item] into the section's presentation.
         * Pure — the caller (DetailSeasonsSection) remembers the result on the
         * inputs that feed it so scroll/animation recompositions don't
         * re-allocate the filtered episode map.
         */
        fun from(
            state: DetailContentState,
            item: MediaItem,
            isLocalOrigin: Boolean,
        ): SeasonsPresentation {
            // Episode-card preferences: hideEpisodeThumbnails and skipSpecials
            // are neutralized for a LOCAL origin — local cards always show art
            // (hiding without guaranteed local artwork yields blank tiles) and
            // render every season. Episode sort ([episodesDescending]) IS
            // honored for a local origin since offline episodes load in
            // canonical ascending playback order, same as online.
            val effectiveSkipSpecials = !isLocalOrigin && state.preferences.skipSpecials
            val effectiveHideThumbnails = !isLocalOrigin && state.preferences.hideEpisodeThumbnails
            return SeasonsPresentation(
                seasons = if (item.mediaType == MediaType.SEASON) {
                    // A SEASON entry carries the parent series' whole snapshot
                    // (the resolver loads it through seriesIdForDetail), but
                    // the page IS the entry season: render only its tab so the
                    // tree matches the header ("Season 5" shows Season 5's
                    // episodes, not every sibling season's).
                    state.seasons.filter { it.id == item.id }
                } else {
                    state.seasons
                },
                episodes = if (effectiveSkipSpecials) {
                    state.episodes.mapValues { (_, eps) -> eps.filter { it.seasonNumber != 0 } }
                } else {
                    state.episodes
                },
                hideEpisodeThumbnails = effectiveHideThumbnails,
                episodesDescending = state.preferences.episodesDescending,
                compactEpisodeList = state.preferences.compactEpisodeList,
                // Downloaded-episode set: for a LOCAL origin every episode is
                // downloaded; for a REMOTE series we surface the loaded
                // downloadedEpisodeIds (populated when the download sheet
                // opened) so the trash badge matches the on-disk truth.
                downloadedEpisodeIds = when {
                    isLocalOrigin -> state.episodes.values.flatten().map { it.id }.toSet()
                    state.downloadedEpisodeIds.isNotEmpty() -> state.downloadedEpisodeIds
                    else -> null
                },
                episodeLocalImagePaths = state.assets.episodeImages,
                currentItemId = if (item.mediaType == MediaType.EPISODE) item.id else null,
                currentSeasonId = when (item.mediaType) {
                    MediaType.EPISODE -> item.seasonId
                    MediaType.SEASON -> item.id
                    else -> null
                },
            )
        }
    }
}

/**
 * Callback bundle for [SeasonsSection] — the season-tab controls, the episode
 * list's interactions, and the season-level mark pair. Matches the shape of
 * the sibling `DetailContentCallbacks` bundles: an `@Immutable` data class of
 * lambdas, every field defaulting to a no-op so partially-wired consumers keep
 * compiling. `DetailSeasonsSection` builds it from the screen-level bundles;
 * the section composable takes only this bundle (its 25 former flat parameters
 * collapse to the presentation + the state slices it reads + this bundle).
 */
@Immutable
internal data class SeasonsSectionCallbacks(
    /** A season tab became the rendered one (also fired by the init effect with the computed default). */
    val onSeasonSelected: (seasonId: String) -> Unit = {},
    /**
     * Persists the user's season-tab choice for the current series. Fires ONLY
     * on a user-initiated season-tab select (the split-button leading
     * onClick). Distinct from [onSeasonSelected], which the init
     * `LaunchedEffect` also calls with the computed default. Routing the
     * persistence through this callback (and never through
     * [onSeasonSelected]) is what stops the default/smart-play season from
     * overwriting the user's real pinned choice on every screen open.
     */
    val onSeasonPinned: (seasonId: String) -> Unit = {},
    /** Newest-first episode sort toggle. */
    val onEpisodesDescendingChange: (Boolean) -> Unit = {},
    /** Compact vertical episode list toggle. */
    val onCompactEpisodeListChange: (Boolean) -> Unit = {},
    val onMarkSeasonPlayed: (seasonId: String) -> Unit = {},
    val onMarkSeasonUnplayed: (seasonId: String) -> Unit = {},
    /** Play an episode in the player. */
    val onEpisodePlayClick: (MediaItem) -> Unit = {},
    /** Open an episode's detail screen. */
    val onEpisodeDetailClick: (MediaItem) -> Unit = {},
    /** Long-press on an episode row (quick-action sheet intake). */
    val onEpisodeLongPress: (MediaItem) -> Unit = {},
    /** Track the TV-focused episode row so the Menu key can open its quick actions. */
    val onFocusedEpisodeChange: (MediaItem) -> Unit = {},
    /** Per-episode delete (downloaded episodes only; gated by [SeasonsPresentation.downloadedEpisodeIds]). */
    val onEpisodeDeleteClick: (MediaItem) -> Unit = {},
)
