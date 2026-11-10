package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.detailCardBorder
import com.raulshma.jellyplay.core.designsystem.theme.sharedElementBoundsSpec
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.model.EpisodeStreamInfo
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MissingEpisodeReason
import com.raulshma.jellyplay.core.model.hasDisplayableBadges
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.components.MediaCardProgressOverlay
import com.raulshma.jellyplay.core.ui.components.clickModifier
import com.raulshma.jellyplay.core.ui.components.formatRelativeTime
import com.raulshma.jellyplay.core.ui.components.localDateFromIsoTimestamp
import com.raulshma.jellyplay.core.ui.components.shortMonthDayYear
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.preview.MediaPreview
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberCardChrome
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_airs_date_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_episode_play
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_season_options
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_sort_newest_first
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_sort_oldest_first
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_switch_to_cards
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_switch_to_list
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_episode_cd
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_unwatched
import com.raulshma.jellyplay.feature.details.generated.resources.detail_mark_season_watched
import com.raulshma.jellyplay.feature.details.generated.resources.detail_missing_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_season_empty_description
import com.raulshma.jellyplay.feature.details.generated.resources.detail_season_empty_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_season_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seasons
import com.raulshma.jellyplay.feature.details.generated.resources.detail_spoiler
import com.raulshma.jellyplay.feature.details.generated.resources.detail_time_left_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_watched_badge
import com.raulshma.jellyplay.feature.details.generated.resources.jellyplay_det_filler_badge
import com.raulshma.jellyplay.feature.details.generated.resources.jellyplay_det_mixed_badge
import com.raulshma.jellyplay.feature.details.generated.resources.jellyplay_det_recap_badge
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun SeasonsSection(
    /**
     * The section's folded presentation (season tabs, filtered episode map,
     * display-preference echoes, downloaded/current-item slices) — produced by
     * [SeasonsPresentation.from] and memoized by [DetailSeasonsSection], which
     * owns the local-origin neutralization decisions this section used to
     * re-derive inline.
     */
    presentation: SeasonsPresentation,
    seriesItem: MediaItem,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
    fetchedSeasonIds: Set<String>,
    /**
     * The season id the user last pinned for this series (persisted across
     * navigation). Fed into [SeasonStartResolver]; an active resume still wins.
     * Null when nothing is persisted (or the screen is for a non-series item),
     * in which case resolution behaves exactly as it did before this feature.
     */
    persistedSeasonId: String?,
    /** The item/tag-aware artwork resolver for the episode thumbnails. */
    urls: ArtworkUrls,
    /** Season-tab, episode-list and mark callbacks (see [SeasonsSectionCallbacks] for the pin/select split). */
    callbacks: SeasonsSectionCallbacks,
    /**
     * The jellyfin-plugin-jellyplay anime markers for this series
     * (episodeNumber → badge kind, ADR 0010). Empty = no badges anywhere
     * (plugin absent / probe failed / series carries no markers — silent
     * absence by contract).
     */
    animeMarkers: Map<Int, AnimeBadgeKind> = emptyMap(),
    /**
     * The jellyfin-plugin-jellyplay TMDB season ratings for this series
     * (season id → episodeNumber → ratings, ADR 0010). Drives the compact
     * "★ score" chip on the episode rows and the season-average chip beside
     * the section header. Empty = no scores anywhere (silent absence).
     */
    seasonRatings: Map<String, Map<Int, com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore>> = emptyMap(),
) {
    // ── DEFERRED FOR LOCAL ORIGIN (decided in [SeasonsPresentation.from]) ────
    // The following affordances remain ONLINE-ONLY and are deliberately NOT
    // implemented for a local/offline origin:
    //   • hideEpisodeThumbnails / spoiler overlay — local cards always show art
    //     (hiding thumbnails without guaranteed local artwork yields blank tiles).
    //   • skipSpecials (S0 filtering) — local series render every season.
    //   • press-and-hold peek (rememberMediaPeek) — local cards don't peek.
    // Episode sort order ([SeasonsPresentation.episodesDescending]) and its
    // toggle ARE honored for a local origin: offline episodes load in canonical
    // ascending playback order (same as online), so reversing to newest-first
    // is a meaningful choice.
    // ─────────────────────────────────────────────────────────────────────────
    // This section is the expanded-width (tablet/desktop/TV) renderer: the
    // compact-width portrait body renders the same band as flattened list
    // items instead ([detailSeasonBandItems]), where the compact vertical
    // episode list is virtualized in the scroll list itself. The horizontal
    // D-pad focus row below is the only episode layout this section reaches,
    // so the layout toggle is never offered here.
    val selection = remember(seriesItem.id) { SeasonsBandSelection() }
    val selectedSeasonIndex = rememberSeasonSelectedIndex(
        selection = selection,
        seasons = presentation.seasons,
        smartPlayTarget = smartPlayTarget,
        currentSeasonId = presentation.currentSeasonId,
        persistedSeasonId = persistedSeasonId,
        onSeasonSelected = callbacks.onSeasonSelected,
    )

    Column {
        SeasonsSectionHeader(
            presentation = presentation,
            seasons = presentation.seasons,
            selectedSeasonIndex = selectedSeasonIndex,
            selection = selection,
            seasonRatings = seasonRatings,
            urls = urls,
            callbacks = callbacks,
            showLayoutToggle = false,
        )

        Spacer(Modifier.height(20.dp))

        SeasonsEpisodesBlock(
            presentation = presentation,
            seasons = presentation.seasons,
            selectedSeasonIndex = selectedSeasonIndex,
            fetchedSeasonIds = fetchedSeasonIds,
            urls = urls,
            callbacks = callbacks,
            animeMarkers = animeMarkers,
            seasonRatings = seasonRatings,
        )
    }
}

/**
 * The seasons band's hoisted tab selection: the user's pinned season id for
 * the current series. Shared by [SeasonsSection] and the flattened band's
 * header item ([detailSeasonBandItems]) — both keyed on the series item id via
 * their `remember`, so navigating to another series resets the pin exactly as
 * the former in-section state did.
 */
internal class SeasonsBandSelection {
    var userSelectedSeasonId by mutableStateOf<String?>(null)
}

/**
 * The seasons band's selected-tab resolution — the [SeasonStartResolver]
 * initial index (smart-play resume > the user's persisted pin > the current
 * context season > the first tab) overridden by the user's live tab selection,
 * plus the one init effect that reports the rendered season back to the host
 * (which drives the per-season episode fetch). Verbatim from the former
 * in-section state; shared by [SeasonsSection] and the flattened band.
 */
@Composable
internal fun rememberSeasonSelectedIndex(
    selection: SeasonsBandSelection,
    seasons: List<MediaItem>,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
    currentSeasonId: String?,
    persistedSeasonId: String?,
    onSeasonSelected: (String) -> Unit,
): Int {
    val initialSeasonIndex = SeasonStartResolver.resolveInitialSeasonIndex(
        seasons = seasons,
        smartPlayTarget = smartPlayTarget,
        currentSeasonId = currentSeasonId,
        persistedSeasonId = persistedSeasonId,
    )
    val selectedSeasonIndex = remember(seasons, selection.userSelectedSeasonId, initialSeasonIndex) {
        if (selection.userSelectedSeasonId != null) {
            val userIdx = seasons.indexOfFirst { it.id == selection.userSelectedSeasonId }
            if (userIdx >= 0) userIdx else initialSeasonIndex
        } else {
            initialSeasonIndex
        }
    }

    LaunchedEffect(selectedSeasonIndex, seasons) {
        val season = seasons.getOrNull(selectedSeasonIndex)
        if (season != null) {
            onSeasonSelected(season.id)
        }
    }
    return selectedSeasonIndex
}

/**
 * The flattened seasons band's hoisted model: every fold the band's list items
 * share, computed once in the scroll body's composable scope (the LazyColumn's
 * item builder is not a composable context, so nothing below can `remember`).
 * Mirrors the former single-slot section's fold-for-once discipline —
 * [presentation] and the callback bundle are memoized on exactly their inputs.
 */
internal class SeasonsBandModel(
    val presentation: SeasonsPresentation,
    val selectedSeasonIndex: Int,
    val selectedSeason: MediaItem?,
    /** The selected season's display-sorted episodes; null = not fetched yet. */
    val seasonEpisodes: List<MediaItem>?,
    val isLoading: Boolean,
    /** The compact vertical list is active (compact-width, non-TV, pref on). */
    val useCompactList: Boolean,
    /** The compact↔cards layout toggle is offered (compact-width, non-TV). */
    val showLayoutToggle: Boolean,
    val selection: SeasonsBandSelection,
    val sectionCallbacks: SeasonsSectionCallbacks,
    val urls: ArtworkUrls,
    val animeMarkers: Map<Int, AnimeBadgeKind>,
    val seasonRatings: Map<String, Map<Int, com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore>>,
    val fetchedSeasonIds: Set<String>,
) {
    val seasons: List<MediaItem> get() = presentation.seasons

    /** The selected season's TMDB per-episode scores (ADR 0010); empty = no chips. */
    val selectedSeasonRatings: Map<Int, com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore>
        get() = selectedSeason?.let { seasonRatings[it.id] } ?: emptyMap()
}

/**
 * Folds the screen state into the flattened band's model, or null when the
 * snapshot carries no seasons (the band's slot then renders its spacing only —
 * the former section's early return). The tab selection's init effect runs
 * here, in the scroll body's scope, exactly as it ran in the former section.
 */
@Composable
internal fun rememberDetailSeasonsBand(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    item: MediaItem,
    isLocalOrigin: Boolean,
    persistedSeasonId: String?,
): SeasonsBandModel? {
    if (state.core.seasons.isEmpty()) return null

    // The section's presentation (skip-specials filter + hide-thumbnails
    // neutralization for a LOCAL origin, the downloaded-episode three-way
    // fold, the SEASON entry filter, the current-item/current-season pair —
    // see SeasonsPresentation) folds once here, memoized on exactly the
    // inputs that feed it.
    val presentation = remember(
        state.core.seasons,
        state.core.episodes,
        state.preferences,
        state.downloads.downloadedEpisodeIds,
        state.core.assets,
        item.id,
        item.mediaType,
        item.seasonId,
        isLocalOrigin,
    ) {
        SeasonsPresentation.from(state, item, isLocalOrigin)
    }
    // The section-level callback bundle: the screen-level seasons bundle plus
    // the episode interactions, which the screen bundles express one level up
    // (play dispatch, item navigation, quick-action intake, per-episode
    // delete). Remembered on exactly the screen-level bundles its lambdas
    // capture (each of them remembered on stable keys upstream).
    val sectionCallbacks = remember(
        callbacks.seasons,
        callbacks.playback,
        callbacks.navigation,
        callbacks.screen,
        callbacks.download,
    ) {
        SeasonsSectionCallbacks(
            onSeasonSelected = callbacks.seasons.onSeasonSelected,
            onSeasonPinned = callbacks.seasons.onSeasonPinned,
            onEpisodesDescendingChange = callbacks.seasons.onEpisodesDescendingChange,
            onCompactEpisodeListChange = callbacks.seasons.onCompactEpisodeListChange,
            onMarkSeasonPlayed = callbacks.seasons.onMarkSeasonPlayed,
            onMarkSeasonUnplayed = callbacks.seasons.onMarkSeasonUnplayed,
            onEpisodePlayClick = { episode ->
                val sourceId = null
                val startPos = episode.playbackPositionTicks ?: 0L
                callbacks.playback.onPlayClick(episode.id, sourceId, startPos)
            },
            onEpisodeDetailClick = { episode ->
                callbacks.navigation.onItemClick(episode.id)
            },
            onEpisodeLongPress = callbacks.screen.onMediaQuickActions,
            onFocusedEpisodeChange = callbacks.screen.onFocusedMediaItem,
            onEpisodeDeleteClick = { episode -> callbacks.download.onDeleteEpisode(episode.id) },
        )
    }
    val selection = remember(item.id) { SeasonsBandSelection() }
    val selectedSeasonIndex = rememberSeasonSelectedIndex(
        selection = selection,
        seasons = presentation.seasons,
        smartPlayTarget = state.core.smartPlayTarget,
        currentSeasonId = presentation.currentSeasonId,
        persistedSeasonId = persistedSeasonId,
        onSeasonSelected = sectionCallbacks.onSeasonSelected,
    )
    val selectedSeason = presentation.seasons.getOrNull(selectedSeasonIndex)
    // The selected season's display-sorted episodes, memoized on exactly the
    // sort's inputs so a sibling-section recomposition doesn't re-sort.
    val seasonEpisodes = remember(selectedSeasonIndex, presentation.episodes, presentation.episodesDescending) {
        selectedSeason?.let { presentation.episodes[it.id] }
            ?.sortedBy { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE }
            ?.let { sorted -> if (presentation.episodesDescending) sorted.reversed() else sorted }
    }
    val isFetched = selectedSeason?.id?.let { state.core.fetchedSeasonIds.contains(it) } ?: false
    val isLoading = seasonEpisodes == null && selectedSeason != null && !isFetched
    // Compact vertical list is mobile-only: the toggle is offered (and the
    // flattened band emits the compact episode items) solely on compact-width,
    // non-TV form factors. TV keeps the horizontal D-pad focus row;
    // tablet/expanded keeps the denser horizontal overview.
    val isTv = LocalTvMode.current
    val isCompactWidth = LocalAdaptiveInfo.current.windowSizeClass ==
        com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Compact
    val showLayoutToggle = !isTv && isCompactWidth
    val useCompactList = showLayoutToggle && presentation.compactEpisodeList

    return SeasonsBandModel(
        presentation = presentation,
        selectedSeasonIndex = selectedSeasonIndex,
        selectedSeason = selectedSeason,
        seasonEpisodes = seasonEpisodes,
        isLoading = isLoading,
        useCompactList = useCompactList,
        showLayoutToggle = showLayoutToggle,
        selection = selection,
        sectionCallbacks = sectionCallbacks,
        urls = callbacks.artwork.urls,
        animeMarkers = state.core.animeMarkers,
        seasonRatings = state.core.seasonRatings,
        fetchedSeasonIds = state.core.fetchedSeasonIds,
    )
}

/**
 * The flattened seasons band's header: the seasons section's
 * [SeasonsSectionHeader] chrome plus the trailing gap the episode rows
 * follow, composed as ONE list item. The band's episode items are emitted by
 * [detailSeasonBandItems] directly after it.
 */
@Composable
private fun SeasonsBandHeaderItem(
    band: SeasonsBandModel,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column {
            SeasonsSectionHeader(
                presentation = band.presentation,
                seasons = band.seasons,
                selectedSeasonIndex = band.selectedSeasonIndex,
                selection = band.selection,
                seasonRatings = band.seasonRatings,
                urls = band.urls,
                callbacks = band.sectionCallbacks,
                showLayoutToggle = band.showLayoutToggle,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * The flattened seasons band's items: the header/tabs item, then the selected
 * season's episodes as INDIVIDUAL items in the scroll list (the compact
 * vertical list) or the horizontal focus-row block as one item. Emits the
 * slot's spacing only when the band is null — an admitted-but-empty slot keeps
 * the body's vertical rhythm exactly as the former section's early return did.
 * The compact rows' keys incorporate the season id, so a season switch is a
 * wholesale replacement of the row items (no item animations reuse a slot
 * across seasons) while the scroll position stays anchored.
 */
internal fun LazyListScope.detailSeasonBandItems(
    band: SeasonsBandModel?,
    scrollState: DetailScrollState,
    /** The spacing after the band's last item (the section tail or the body tail). */
    sectionTailSpacing: Dp,
) {
    if (band == null) {
        item(key = "detail_section_SEASONS", contentType = "detail_section_SEASONS") {
            DetailBodySectionSlot(scrollState = scrollState, bottomSpacing = sectionTailSpacing) { }
        }
        return
    }
    item(key = "detail_section_SEASONS", contentType = "detail_section_SEASONS") {
        DetailBodySectionSlot(scrollState = scrollState, bottomSpacing = 0.dp) {
            SeasonsBandHeaderItem(band)
        }
    }
    when {
        band.useCompactList && band.isLoading -> item(
            key = "detail_season_loading_${band.selectedSeason?.id.orEmpty()}",
            contentType = "detail_season_loading",
        ) {
            DetailBodySectionSlot(scrollState = scrollState, bottomSpacing = sectionTailSpacing) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SeasonLoadingSlot()
                }
            }
        }
        band.useCompactList -> {
            val episodes = band.seasonEpisodes
            if (episodes != null && episodes.isNotEmpty()) {
                episodes.forEachIndexed { episodeIndex, episode ->
                    item(
                        key = "detail_episode_${band.selectedSeason?.id.orEmpty()}_${episode.id}",
                        contentType = "detail_compact_episode",
                    ) {
                        DetailBodySectionSlot(
                            scrollState = scrollState,
                            bottomSpacing = if (episodeIndex == episodes.lastIndex) {
                                sectionTailSpacing
                            } else {
                                CompactEpisodeRowSpacing
                            },
                        ) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                    val arms = rememberEpisodeArms(episode, band.sectionCallbacks)
                                    CompactEpisodeRow(
                                        episode = episode,
                                        urls = band.urls,
                                        isCurrentEpisode = episode.id == band.presentation.currentItemId,
                                        onPlayClick = arms.onPlayClick,
                                        onDetailClick = arms.onDetailClick,
                                        onLongPress = arms.onLongPress,
                                        hideThumbnail = band.presentation.hideEpisodeThumbnails,
                                        isDownloaded = band.presentation.downloadedEpisodeIds?.contains(episode.id) == true,
                                        onDeleteClick = arms.onDeleteClick,
                                        localImagePath = band.presentation.episodeLocalImagePaths[episode.id],
                                        animeBadge = band.animeMarkers[episode.episodeNumber ?: episode.indexNumber],
                                        tmdbScore = band.selectedSeasonRatings[episode.episodeNumber ?: episode.indexNumber]?.tmdbScore,
                                        showStreamBadges = band.presentation.showStreamBadges,
                                        showDownloadProgress = band.presentation.showDownloadProgress,
                                        episodeOverviewPreview = band.presentation.episodeOverviewPreview,
                                        // No shared-element scope in the flattened band —
                                        // the thumbnail morph rode the former
                                        // AnimatedContent's transition, which the item
                                        // split replaces with a wholesale item swap.
                                        sharedThumbnailModifier = Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item(
                    key = "detail_season_empty_${band.selectedSeason?.id.orEmpty()}",
                    contentType = "detail_season_empty",
                ) {
                    DetailBodySectionSlot(scrollState = scrollState, bottomSpacing = sectionTailSpacing) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            SeasonEmptySlot()
                        }
                    }
                }
            }
        }
        else -> item(
            key = "detail_section_SEASONS_episodes",
            contentType = "detail_section_SEASONS_episodes",
        ) {
            DetailBodySectionSlot(scrollState = scrollState, bottomSpacing = sectionTailSpacing) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SeasonsEpisodesBlock(
                        presentation = band.presentation,
                        seasons = band.seasons,
                        selectedSeasonIndex = band.selectedSeasonIndex,
                        fetchedSeasonIds = band.fetchedSeasonIds,
                        urls = band.urls,
                        callbacks = band.sectionCallbacks,
                        animeMarkers = band.animeMarkers,
                        seasonRatings = band.seasonRatings,
                    )
                }
            }
        }
    }
}

/** The compact episode rows' former spacedBy gap, now each row item's bottom pad. */
private val CompactEpisodeRowSpacing = 8.dp

/** The selected season's per-season fetch spinner (the former loading branch's content). */
@Composable
private fun SeasonLoadingSlot() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        FadingItem {
            JellyPlayLoadingIndicator()
        }
    }
}

/** The episode-less season's empty state (the former else branch's content). */
@Composable
private fun SeasonEmptySlot() {
    FadingItem {
        com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
            icon = Tabler.Outline.Movie,
            title = stringResource(Res.string.detail_season_empty_title),
            description = stringResource(Res.string.detail_season_empty_description),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        )
    }
}

/**
 * The seasons band's header chrome: the title + the selected season's TMDB
 * average chip, the layout/sort toggles, and the season tab strip. Extracted
 * verbatim from the former single-slot section so [SeasonsSection] and the
 * flattened band's header item render the same controls.
 */
@Composable
internal fun SeasonsSectionHeader(
    presentation: SeasonsPresentation,
    seasons: List<MediaItem>,
    selectedSeasonIndex: Int,
    selection: SeasonsBandSelection,
    seasonRatings: Map<String, Map<Int, com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore>>,
    urls: ArtworkUrls,
    callbacks: SeasonsSectionCallbacks,
    /** The compact↔cards toggle — offered only where the compact list exists. */
    showLayoutToggle: Boolean,
) {
    FadingItem {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Title + the selected season's TMDB average chip (ADR 0010,
            // plugin `ratings`): one fold over the loaded season's
            // score-carrying episodes; absent when none carry a score.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.detail_section_seasons),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                seasons.getOrNull(selectedSeasonIndex)
                    ?.let { seasonRatings[it.id] }
                    ?.let { seasonAverageScore(it) }
                    ?.let { average -> TmdbScoreChip(score = average) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Layout switch (compact vertical list ↔ horizontal cards).
                // Only offered on compact/mobile widths — TV and tablet always
                // use the horizontal focus row.
                if (showLayoutToggle) {
                    val layoutFocusState = rememberTvFocusState(focusedScale = 1.1f)
                    Surface(
                        modifier = Modifier
                            .clip(ShapeCache.smooth16)
                            .then(layoutFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(layoutFocusState, ShapeCache.smooth16))
                            .clickable { callbacks.onCompactEpisodeListChange(!presentation.compactEpisodeList) },
                        color = if (presentation.compactEpisodeList) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = ShapeCache.smooth16,
                    ) {
                        Icon(
                            imageVector = if (presentation.compactEpisodeList) Tabler.Outline.LayoutGrid else Tabler.Outline.List,
                            contentDescription = stringResource(
                                if (presentation.compactEpisodeList) Res.string.detail_cd_switch_to_cards
                                else Res.string.detail_cd_switch_to_list
                            ),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                val sortFocusState = rememberTvFocusState(focusedScale = 1.1f)
                Surface(
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .then(sortFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(sortFocusState, ShapeCache.smooth16))
                        .clickable { callbacks.onEpisodesDescendingChange(!presentation.episodesDescending) },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = ShapeCache.smooth16,
                ) {
                    Icon(
                        imageVector = if (presentation.episodesDescending) Tabler.Outline.SortDescending2 else Tabler.Outline.SortAscending2,
                        contentDescription = stringResource(
                            if (presentation.episodesDescending) Res.string.detail_cd_sort_oldest_first
                            else Res.string.detail_cd_sort_newest_first
                        ),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    TvFocusableItemRow(
        items = seasons,
        key = { it.id },
        contentType = { _, _ -> "season" },
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { index, season, focusModifier ->
        val isSelected = index == selectedSeasonIndex
        val targetColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        val targetContentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
        val surfaceColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "seasonColor",
        )
        val contentColor by animateColorAsState(
            targetValue = targetContentColor,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "seasonContentColor",
        )
        val seasonTabFocusState = rememberTvFocusState(focusedScale = 1.05f)
        val seasonColors = ButtonDefaults.buttonColors(
            containerColor = surfaceColor,
            contentColor = contentColor,
        )
        val trailingFocusState = rememberTvFocusState(focusedScale = 1.05f)
        var menuExpanded by remember { mutableStateOf(false) }
        val seasonName = season.name ?: stringResource(
            Res.string.detail_season_format, season.indexNumber ?: (index + 1),
        )
        // Compact (extra-small) split-button variant — these tabs sit in a
        // dense horizontal row, so use the xsmall container height (shorter
        // than the default SmallContainerHeight) and a tighter label style.
        val containerHeight = SplitButtonDefaults.ExtraSmallContainerHeight
        Box(
            modifier = focusModifier
                .clip(ShapeCache.smooth16)
                .then(seasonTabFocusState.focusModifier)
                .then(Modifier.tvFocusIndicator(seasonTabFocusState, ShapeCache.smooth16)),
        ) {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = {
                            selection.userSelectedSeasonId = season.id
                            // Persist the user's tab choice. This fires
                            // ONLY on a real tab select — never from the
                            // init LaunchedEffect (which calls only
                            // onSeasonSelected) — so the smart-play /
                            // default season can't overwrite the pin.
                            callbacks.onSeasonPinned(season.id)
                        },
                        colors = seasonColors,
                        shapes = SplitButtonDefaults.leadingButtonShapesFor(containerHeight),
                        contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(containerHeight),
                    ) {
                        // The detail_season_tabs_artwork pref: a small
                        // season poster beside the label — only when the
                        // season actually carries its own Primary tag
                        // (a tagless season would 404 an untagged URL).
                        // The tag lookup keys the resolver; keyed on the
                        // season so a tag change re-resolves.
                        val seasonTabImageUrl = if (presentation.showSeasonTabArtwork) {
                            remember(season) {
                                season.imageTags.primary?.let { urls.poster(season) }.orEmpty()
                            }
                        } else {
                            ""
                        }
                        if (seasonTabImageUrl.isNotEmpty()) {
                            MediaImage(
                                url = seasonTabImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                                // The chip renders at 24 dp — decode at
                                // 2× density instead of the 384² default
                                // (the compact episode row's explicit-
                                // budget precedent).
                                size = coil3.size.Size(48, 48),
                            )
                        }
                        Text(
                            text = seasonName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        onClick = { menuExpanded = true },
                        colors = seasonColors,
                        shapes = SplitButtonDefaults.trailingButtonShapesFor(containerHeight),
                        // Tighter than the xsmall default content padding so the
                        // watch-state affordance sits flush against the title.
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                        modifier = Modifier
                            .then(trailingFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(trailingFocusState, ShapeCache.smooth4)),
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Eye,
                            contentDescription = stringResource(Res.string.detail_cd_season_options),
                            modifier = Modifier.size(SplitButtonDefaults.ExtraSmallTrailingButtonIconSize),
                        )
                    }
                },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.detail_mark_season_watched)) },
                    onClick = {
                        menuExpanded = false
                        callbacks.onMarkSeasonPlayed(season.id)
                    },
                    leadingIcon = { Icon(Tabler.Outline.Eye, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.detail_mark_season_unwatched)) },
                    onClick = {
                        menuExpanded = false
                        callbacks.onMarkSeasonUnplayed(season.id)
                    },
                    leadingIcon = { Icon(Tabler.Outline.EyeOff, contentDescription = null) },
                )
            }
        }
    }
}

/**
 * The seasons band's episode area for the horizontal-card layout: the selected
 * season's D-pad focus row (or the per-season fetch spinner / the
 * episode-less empty state), crossfading on a season switch inside the
 * AnimatedContent. This is the expanded-width renderer's slot — the
 * compact-width portrait body emits its compact episode rows as flattened
 * scroll-list items instead ([detailSeasonBandItems]), so the compact branch
 * the former single-slot section carried here is gone: it was unreachable
 * from this renderer (its layout toggle is never offered at expanded widths),
 * and the compact rows now virtualize in the scroll list itself.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SeasonsEpisodesBlock(
    presentation: SeasonsPresentation,
    seasons: List<MediaItem>,
    selectedSeasonIndex: Int,
    fetchedSeasonIds: Set<String>,
    urls: ArtworkUrls,
    callbacks: SeasonsSectionCallbacks,
    animeMarkers: Map<Int, AnimeBadgeKind>,
    seasonRatings: Map<String, Map<Int, com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore>>,
) {
    val episodes = presentation.episodes
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    val seasonEpisodes = selectedSeason?.let { episodes[it.id] }
    // The selected season's TMDB per-episode scores (ADR 0010) — the
    // per-episode chip lookup for the episode-row layout. Empty =
    // the plugin sent nothing for this season (no chips anywhere).
    val selectedSeasonRatings = selectedSeason?.let { seasonRatings[it.id] } ?: emptyMap()
    val isFetched = selectedSeason?.id?.let { fetchedSeasonIds.contains(it) } ?: false
    val isLoading = seasonEpisodes == null && selectedSeason != null && !isFetched
    // Capture in composable scope; AnimatedContent's transitionSpec is not composable.
    val seasonFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val seasonFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    // Shared-element spring for the layout-switch morph: the compact-row
    // thumbnail and the wide-card thumbnail are the same image, so the
    // outgoing/incoming copies glide between their bounds while the
    // surrounding metadata crossfades.
    val episodeThumbBoundsTransform: BoundsTransform = { _, _ ->
        sharedElementBoundsSpec()
    }

    AnimatedContent(
        targetState = SeasonEpisodesTargetState(
            seasonIndex = selectedSeasonIndex,
            isLoading = isLoading,
        ),
        transitionSpec = {
            fadeIn(
                animationSpec = seasonFadeIn,
            ) togetherWith fadeOut(
                animationSpec = seasonFadeOut,
            )
        },
        label = "seasonEpisodes",
    ) { target ->
        val seasonIdx = target.seasonIndex
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = this
        // Memoize the sort + reverse so a recomposition triggered by an
        // unrelated parent state change (e.g. sibling animation) doesn't
        // re-sort this season's episode list.
        val currentEpisodes = remember(seasonIdx, episodes, presentation.episodesDescending) {
            seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
                ?.sortedBy { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE }
                ?.let { sorted -> if (presentation.episodesDescending) sorted.reversed() else sorted }
        }
        val currentIsFetched = seasons.getOrNull(seasonIdx)?.id?.let { fetchedSeasonIds.contains(it) } ?: false
        val currentIsLoading = target.isLoading || (currentEpisodes == null && seasons.getOrNull(seasonIdx) != null && !currentIsFetched)

        when {
            currentIsLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FadingItem {
                        JellyPlayLoadingIndicator()
                    }
                }
            }
            currentEpisodes != null && currentEpisodes.isNotEmpty() -> {
                TvFocusableItemRow(
                    items = currentEpisodes,
                    key = { "episode_${it.id}" },
                    contentType = { _, _ -> "episode" },
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    onFocusedIndexChange = { index ->
                        currentEpisodes.getOrNull(index)?.let(callbacks.onFocusedEpisodeChange)
                    },
                ) { _, episode, focusModifier ->
                    val arms = rememberEpisodeArms(episode, callbacks)
                    EpisodeCard(
                        episode = episode,
                        urls = urls,
                        isCurrentEpisode = episode.id == presentation.currentItemId,
                        onPlayClick = arms.onPlayClick,
                        onDetailClick = arms.onDetailClick,
                        onLongPress = arms.onLongPress,
                        modifier = focusModifier,
                        hideThumbnail = presentation.hideEpisodeThumbnails,
                        isDownloaded = presentation.downloadedEpisodeIds?.contains(episode.id) == true,
                        onDeleteClick = arms.onDeleteClick,
                        localImagePath = presentation.episodeLocalImagePaths[episode.id],
                        animeBadge = animeMarkers[episode.episodeNumber ?: episode.indexNumber],
                        tmdbScore = selectedSeasonRatings[episode.episodeNumber ?: episode.indexNumber]?.tmdbScore,
                        showStreamBadges = presentation.showStreamBadges,
                        showDownloadProgress = presentation.showDownloadProgress,
                        episodeOverviewPreview = presentation.episodeOverviewPreview,
                        sharedThumbnailModifier = episodeThumbSharedModifier(
                            episodeId = episode.id,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = episodeThumbBoundsTransform,
                        ),
                    )
                }
            }
            else -> {
                // episode-less season was a plain text line.
                // Use the standard empty state (icon + title + description).
                FadingItem {
                    com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                        icon = Tabler.Outline.Movie,
                        title = stringResource(Res.string.detail_season_empty_title),
                        description = stringResource(Res.string.detail_season_empty_description),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Shared-element thumbnail morph between the compact vertical rows and the
 * horizontal cards: both layouts render the same episode thumbnail, so the
 * outgoing/incoming copies glide between their bounds (128×72 ↔ 16:9 card
 * width) while the rest of the card crossfades. No-op when the app-level
 * shared transition scope is unavailable (performance mode) — the layouts then
 * swap instantly.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun episodeThumbSharedModifier(
    episodeId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    boundsTransform: BoundsTransform,
): Modifier {
    val scope = sharedTransitionScope ?: return Modifier
    return with(scope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(key = "episode_thumb_$episodeId"),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = boundsTransform,
        )
    }
}

@Composable
internal fun EpisodeCard(
    episode: MediaItem,
    urls: ArtworkUrls,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
    hideThumbnail: Boolean = false,
    sharedThumbnailModifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    // ── Unified episode parity ──
    /** True when this episode has a completed download — surfaces the trash badge. */
    isDownloaded: Boolean = false,
    /** Per-episode delete (downloaded episodes only). No-op default. */
    onDeleteClick: () -> Unit = {},
    /** On-disk thumbnail path; preferred over the resolver URL when non-null. */
    localImagePath: String? = null,
    /** Plugin anime badge for this episode number (null = none — ADR 0010). */
    animeBadge: AnimeBadgeKind? = null,
    /** Plugin TMDB score for this episode number (null = none — ADR 0010). */
    tmdbScore: Double? = null,
    /** Render the stream-info badge row (CC / audio layout / languages). */
    showStreamBadges: Boolean = false,
    /** Render the in-flight download bar + percent chip (fraction comes from [LocalEpisodeDownloadProgress]). */
    showDownloadProgress: Boolean = false,
    /**
     * The detail_episode_overview_preview pref: ON tightens this card's
     * shipped 3-line overview to the 2-line snippet (the compact row's
     * snippet gate rides the same pref); OFF renders exactly today.
     */
    episodeOverviewPreview: Boolean = false,
) {
    // Build the episode image URL once per episode instead of 3× per recomposition.
    // Prefer the on-disk local thumbnail (a downloaded episode's saved Primary
    // image) before the server fallback — matches the offline card. Keyed on the
    // episode (not its id) so a tag change re-keys.
    val episodeImageUrl = remember(episode, localImagePath) {
        localImagePath ?: urls.poster(episode)
    }

    // Card chrome — press scale, focus tracking, click, peek wiring — seats on
    // core/ui's shared CardChrome layer (the implementation MediaCardScaffold
    // consumes). This file keeps its own horizontal thumbnail+metadata layout
    // and its historical chrome values: 0.96 press scale on the fast effects
    // spec, scaling even under reduced motion, 1.03 nominal focus scale, and
    // the smooth16 focus indicator. Long-press keeps resolving to the caller's
    // handler before the peek's.
    val episodePreviewFactory = remember(episode, episodeImageUrl) {
        { sourceBounds: Rect? ->
            MediaPreview(
                item = episode,
                posterUrl = episodeImageUrl,
                backdropUrl = episodeImageUrl,
                blurHash = episode.blurHashes.primary,
                sourceBounds = sourceBounds,
            )
        }
    }
    val chrome = rememberCardChrome(
        previewFactory = episodePreviewFactory,
        focusedScale = 1.03f,
        pressScaleValue = 0.96f,
        pressScaleSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        pressScaleOverridesReducedMotion = true,
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "episodePlayScale",
    )

    // Episode cards are wide (thumbnail + metadata), scaling ~1.5× the adaptive poster width.
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = (adaptiveInfo.rowCardWidth(isTv) * 1.5f).coerceAtLeast(260.dp)

    // Border depends only on the active theme, not on the per-episode data — wrap
    // in remember so the Modifier + gradient aren't rebuilt per card per recompose.
    // includeAurora = false keeps the historical no-border look under Aurora
    // (only the Seerr detail cards glow there).
    val themeVariant = LocalThemeVariant.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val borderModifier = remember(themeVariant, primaryColor, secondaryColor, outlineColor) {
        themeVariant.detailCardBorder(primaryColor, secondaryColor, outlineColor, includeAurora = false)
            ?.let { Modifier.border(it, ShapeCache.smooth16) }
            ?: Modifier
    }

    // Virtual (missing/unaired) episodes dim like watched ones: the row is a
    // placeholder, not playable content, so it recedes behind real episodes.
    // The whole watch-state decision set (dim, overlays, tags, meta lines)
    // folds once here and is shared verbatim with the compact row — see
    // [EpisodeRowPresentation].
    val cardPrefs = com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences.current
    val presentation = EpisodeRowPresentation.from(
        episode = episode,
        hideThumbnail = hideThumbnail,
        isDownloaded = isDownloaded,
        showWatchedCheckmark = cardPrefs.showWatchedCheckmark,
    )
    val isDimmed = presentation.isDimmed

    // This row's in-flight download fraction (provided by the screen from
    // the download-lifecycle slice); null = not downloading. Read here so a
    // ~2 s progress tick recomposes only the downloading rows — the local is a
    // compositionLocalOf (reader-tracked), not static.
    val downloadProgressFraction = if (showDownloadProgress) {
        LocalEpisodeDownloadProgress.current[episode.id]
    } else {
        null
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .then(borderModifier)
            .clip(ShapeCache.smooth16)
            .background(
                if (isCurrentEpisode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .then(
                if (isCurrentEpisode) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                else Modifier
            )
            .then(chrome.pressScale)
            .then(chrome.focus.modifier)
            .then(chrome.peek?.boundsModifier ?: Modifier)
            .then(Modifier.tvFocusIndicator(chrome.focus.focusState, ShapeCache.smooth16))
            .then(
                chrome.clickModifier(
                    onClick = onDetailClick,
                    onLongPress = onLongPress,
                    useReducedMotionIndication = false,
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .then(sharedThumbnailModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (presentation.showSpoilerPlaceholder) {
                EpisodeSpoilerPlaceholder(
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EpisodeThumbArt(
                    url = episodeImageUrl,
                    episode = episode,
                    isDimmed = presentation.isDimmed,
                    decodeSize = coil3.size.Size(640, 360),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // No play affordance on a virtual episode — there is no file to
            // play behind it (detail navigation stays available).
            if (presentation.showPlayAffordance) {
                EpisodePlayAffordance(
                    playScale = playScale,
                    interactionSource = playInteractionSource,
                    onPlayClick = onPlayClick,
                    iconSize = 48.dp,
                    iconPadding = 8.dp,
                    tvFocusable = true,
                )
            }

            // Missing/unaired badge — the virtual episode's own state marker,
            // top-start so it never collides with the watched tag or progress.
            if (presentation.showVirtualBadge) {
                VirtualEpisodeBadge(
                    episode = episode,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }

            // In-flight download bar + percent chip — along the thumbnail's
            // TOP edge (the watch-progress / played bars own the bottom edge);
            // the delete affordance co-occupies top-end only once the download
            // completes and this overlay is gone.
            downloadProgressFraction?.let { fraction ->
                EpisodeDownloadOverlay(fraction = fraction)
            }

            if (presentation.hasWatchProgress) {
                val progress = presentation.progressFraction ?: 0f
                MediaCardProgressOverlay(
                    progressFraction = progress,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress),
                    trackColor = null,
                )
            } else if (presentation.showPlayedBar) {
                EpisodePlayedBar(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
            if (presentation.showWatchedTag) {
                com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag(
                    label = stringResource(Res.string.detail_watched_badge),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 8.dp),
                )
            }

            // Plugin anime badge — bottom-end, mirrored against the watched
            // tag (a filler episode can be watched too; the two never collide).
            animeBadge?.let { kind ->
                AnimeEpisodeBadge(
                    kind = kind,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 6.dp, bottom = 8.dp),
                )
            }

            // Per-episode delete affordance — only for a downloaded episode
            // (gated by `isDownloaded`, which the host sets from the downloaded-
            // episode-id set or the local origin). Online episodes never show
            // this (downloading stays in `SeriesDownloadSheet`), and a virtual
            // episode has no file on disk to delete either.
            if (presentation.showDeleteAffordance) {
                val deleteFocusState = rememberTvFocusState(focusedScale = 1.1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .then(deleteFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(deleteFocusState, CircleShape))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDeleteClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.Trash,
                        contentDescription = stringResource(Res.string.detail_delete_episode_cd),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .playedAlpha(isDimmed, PLAYED_META_ALPHA),
        ) {
            EpisodeMetaLines(
                episode = episode,
                presentation = presentation,
                titleStyle = MaterialTheme.typography.titleMedium,
                metaStyle = MaterialTheme.typography.labelMedium,
                runtimeTopPadding = 4.dp,
                tmdbScore = tmdbScore,
                showStreamBadges = showStreamBadges,
            )
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The detail_episode_overview_preview pref tightens the
                    // shipped 3-line card overview to the 2-line snippet.
                    maxLines = if (episodeOverviewPreview) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }
        }
    }
}

/**
 * Compact, mobile-first episode row for the optional vertical episode list.
 *
 * A single-line [Row]: a 128×72 (16:9) thumbnail with play affordance + watch
 * progress + watched tag on the left, and a metadata column (title, runtime /
 * "Xm left") on the right. Mirrors the [EpisodeCard] semantics — tap opens the
 * episode detail screen, long-press peeks — but trades the wide-card horizontal
 * scroller for vertical scrolling, which is more natural on a phone and lets the
 * watched tag pop while quickly swiping through a season. Both layouts read the
 * same [EpisodeRowPresentation] fold, so the watch-state decisions are shared
 * verbatim and only the geometry differs.
 *
 * No per-episode download/delete: online episodes have no delete action
 * (downloading stays in `SeriesDownloadSheet`), matching [EpisodeCard]. A
 * downloaded episode shows a trailing trash affordance instead.
 */
@Composable
private fun CompactEpisodeRow(
    episode: MediaItem,
    urls: ArtworkUrls,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    hideThumbnail: Boolean = false,
    modifier: Modifier = Modifier,
    sharedThumbnailModifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    // ── Unified episode parity ──
    isDownloaded: Boolean = false,
    onDeleteClick: () -> Unit = {},
    localImagePath: String? = null,
    /** Plugin anime badge for this episode number (null = none — ADR 0010). */
    animeBadge: AnimeBadgeKind? = null,
    /** Plugin TMDB score for this episode number (null = none — ADR 0010). */
    tmdbScore: Double? = null,
    /** Render the stream-info badge row (CC / audio layout / languages). */
    showStreamBadges: Boolean = false,
    /** Render the in-flight download bar + percent chip (fraction comes from [LocalEpisodeDownloadProgress]). */
    showDownloadProgress: Boolean = false,
    /**
     * The detail_episode_overview_preview pref: ON adds the 2-line overview
     * snippet to this row (the card's shipped overview tightens to 2 lines
     * under the same pref); OFF renders exactly today.
     */
    episodeOverviewPreview: Boolean = false,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "compactEpisodeRowScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "compactEpisodeRowPlayScale",
    )

    // Local thumbnail preferred over the server URL (EpisodeCard's chain);
    // keyed on the episode (not its id) so a tag change re-keys.
    val episodeImageUrl = remember(episode, localImagePath) {
        localImagePath ?: urls.poster(episode)
    }
    // Same watch-state fold as EpisodeCard — dim/overlays/tags/meta lines all
    // come from [EpisodeRowPresentation] so the two layouts can't drift.
    val cardPrefs = com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences.current
    val presentation = EpisodeRowPresentation.from(
        episode = episode,
        hideThumbnail = hideThumbnail,
        isDownloaded = isDownloaded,
        showWatchedCheckmark = cardPrefs.showWatchedCheckmark,
    )
    val isDimmed = presentation.isDimmed
    // This row's in-flight download fraction (see EpisodeCard).
    val downloadProgressFraction = if (showDownloadProgress) {
        LocalEpisodeDownloadProgress.current[episode.id]
    } else {
        null
    }

    // Press-and-hold "peek" preview; mirrors EpisodeCard.
    val peek = rememberMediaPeek(
        item = episode,
        posterUrl = episodeImageUrl,
        backdropUrl = episodeImageUrl,
        blurHash = episode.blurHashes.primary,
    )
    rememberReleaseDismiss(isCardPressed)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(
                if (isCurrentEpisode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .then(peek.boundsModifier)
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
                onLongClick = onLongPress ?: peek.onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = 128.dp, height = 72.dp)
                .clip(ShapeCache.smooth16)
                .then(sharedThumbnailModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (presentation.showSpoilerPlaceholder) {
                EpisodeSpoilerPlaceholder(
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EpisodeThumbArt(
                    url = episodeImageUrl,
                    episode = episode,
                    isDimmed = presentation.isDimmed,
                    decodeSize = coil3.size.Size(256, 144),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // No play affordance on a virtual episode — see EpisodeCard.
            if (presentation.showPlayAffordance) {
                EpisodePlayAffordance(
                    playScale = playScale,
                    interactionSource = playInteractionSource,
                    onPlayClick = onPlayClick,
                    iconSize = 32.dp,
                    iconPadding = 6.dp,
                    tvFocusable = false,
                )
            }

            // Missing/unaired badge — top-start so it never collides with the
            // watched tag or progress overlay.
            if (presentation.showVirtualBadge) {
                VirtualEpisodeBadge(
                    episode = episode,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
            }

            // In-flight download bar + percent chip — see EpisodeCard.
            downloadProgressFraction?.let { fraction ->
                EpisodeDownloadOverlay(fraction = fraction)
            }

            if (presentation.hasWatchProgress) {
                val progress = presentation.progressFraction ?: 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else if (presentation.showPlayedBar) {
                EpisodePlayedBar(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
            if (presentation.showWatchedTag) {
                com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag(
                    label = stringResource(Res.string.detail_watched_badge),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 6.dp),
                )
            }

            // Plugin anime badge — bottom-end, mirrored against the watched
            // tag (see EpisodeCard).
            animeBadge?.let { kind ->
                AnimeEpisodeBadge(
                    kind = kind,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 4.dp, bottom = 6.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .playedAlpha(isDimmed, PLAYED_META_ALPHA),
        ) {
            EpisodeMetaLines(
                episode = episode,
                presentation = presentation,
                titleStyle = MaterialTheme.typography.titleSmall,
                metaStyle = MaterialTheme.typography.labelSmall,
                runtimeTopPadding = 2.dp,
                tmdbScore = tmdbScore,
                showStreamBadges = showStreamBadges,
            )
            // The detail_episode_overview_preview pref's snippet: the compact
            // row renders no overview without it (the shipped look).
            if (episodeOverviewPreview) {
                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Per-episode delete (downloaded episodes only). Sits at the trailing
        // edge of the row rather than overlaid on the thumbnail (as on the card)
        // — the compact row has room for a dedicated affordance. Mirrors the
        // offline compact row. A virtual episode has no on-disk file to delete.
        if (presentation.showDeleteAffordance) {
            val deleteFocusState = rememberTvFocusState(focusedScale = 1.1f)
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .then(deleteFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(deleteFocusState, CircleShape))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDeleteClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.detail_delete_episode_cd),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// region Watched-episode dimming
// Watched episodes dim their artwork + meta so a "completed" state reads at a glance.
// The bright EpisodeWatchedTag already carries the watched signal, so meta text stays
// legible (0.80) rather than receding into the dimmed thumbnail.

private const val PLAYED_THUMBNAIL_ALPHA = 0.40f
private const val PLAYED_META_ALPHA = 0.80f

/** Scrim over episode thumbnails — heavier when watched to reinforce the dimmed artwork. */
@Composable
private fun episodeScrimColor(isPlayed: Boolean): Color =
    MaterialTheme.colorScheme.scrim.copy(alpha = if (isPlayed) 0.5f else 0.3f)

/** Applies [alpha] only when [isPlayed], leaving unplayed cards untouched. */
private fun Modifier.playedAlpha(isPlayed: Boolean, alpha: Float): Modifier =
    if (isPlayed) graphicsLayer { this.alpha = alpha } else this
// endregion

// region Shared episode-row leaves
// Leaf pieces the two episode layouts (EpisodeCard, CompactEpisodeRow) render
// identically; the layouts pass their own type scale / paddings / decode
// budget and keep their geometry. Every decision feeding them lives in
// [EpisodeRowPresentation], so the two layouts cannot drift on watch-state
// semantics — only on chrome.

/**
 * The episode row's artwork: image + the watched-dim scrim pair. Shared by
 * both layouts — they differ only in the decode budget ([decodeSize]).
 */
@Composable
private fun EpisodeThumbArt(
    url: String,
    episode: MediaItem,
    isDimmed: Boolean,
    decodeSize: coil3.size.Size,
    modifier: Modifier = Modifier,
) {
    MediaImage(
        url = url,
        contentDescription = episode.name,
        blurHash = episode.blurHashes.primary,
        // Episode thumbnails render up to ~480 dp wide × 16:9. Decode a
        // right-sized bitmap (4–8 cards compose simultaneously).
        size = decodeSize,
        modifier = modifier.playedAlpha(isDimmed, PLAYED_THUMBNAIL_ALPHA),
        contentScale = ContentScale.Crop,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(episodeScrimColor(isDimmed))
    )
}

/**
 * Spoiler-safe placeholder rendered instead of artwork when thumbnails are
 * hidden. Both layouts show it at full thumbnail size; only the type scale
 * differs.
 */
@Composable
private fun EpisodeSpoilerPlaceholder(
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.detail_spoiler),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The 3-dp watched bar under the thumbnail (a fully-played episode without a progress overlay). */
@Composable
private fun EpisodePlayedBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
    )
}

/**
 * The episode row's metadata stack — title ("N. Name"), the runtime line
 * ("Xm left • 42m", or a bare "42m") with stream-info badges on the
 * right side of the time, the TMDB score chip (ADR 0010), and the last-watched
 * relative timestamp. Shared by [EpisodeCard] and [CompactEpisodeRow]; the caller
 * picks the type scale and the runtime line's top padding (the only
 * per-layout differences) and keeps its own column padding/geometry.
 * Extension on [ColumnScope] so the texts join the caller's column directly
 * (no wrapper node between them).
 */
@Composable
private fun ColumnScope.EpisodeMetaLines(
    episode: MediaItem,
    presentation: EpisodeRowPresentation,
    titleStyle: TextStyle,
    metaStyle: TextStyle,
    runtimeTopPadding: Dp,
    /** Plugin TMDB score for this episode (null = no chip — ADR 0010). */
    tmdbScore: Double? = null,
    /** Render the stream-info badge row (CC / audio layout / languages). */
    showStreamBadges: Boolean = false,
) {
    Text(
        text = buildString {
            episode.indexNumber?.let { append("$it. ") }
            append(episode.name)
        },
        style = titleStyle,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val remainingTime = presentation.remainingTime
    val totalTime = presentation.totalTime
    val hasTime = remainingTime != null || totalTime != null
    val streamInfo = if (showStreamBadges) episode.episodeStreamInfo else null
    val hasBadges = streamInfo?.hasDisplayableBadges == true

    if (hasTime || hasBadges) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = runtimeTopPadding),
        ) {
            // remainingTime derives from runtimeTicks, which also produces
            // totalTime — a remaining-without-total case can't occur.
            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.detail_time_left_format, remainingTime),
                        style = metaStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "•",
                        style = metaStyle,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = totalTime,
                        style = metaStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (totalTime != null) {
                Text(
                    text = totalTime,
                    style = metaStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Stream-info badge row: CC / audio layout / audio-language count,
            // from the per-season fetch's MediaStreams projection. Rendered along the
            // right side of the episode runtime line.
            if (hasBadges) {
                EpisodeStreamBadges(info = streamInfo)
            }
        }
    }

    // Plugin TMDB score chip (ADR 0010, `ratings`) — the compact "★ 7.8"
    // badge under the runtime line, before the last-watched timestamp.
    // Rendered only when the plugin sent a score for this episode number.
    if (tmdbScore != null) {
        TmdbScoreChip(
            score = tmdbScore,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    // Last-watched relative timestamp (e.g. "2d ago"). Ports the offline
    // card's lastPlayedDate line so the field is not lost in the unified
    // card. Shown when the episode has any watch activity (the fold's
    // [EpisodeRowPresentation.showLastWatched] gate) and the relative
    // formatter could parse the stored timestamp.
    val lastWatched = remember(episode.lastPlayedDate) { formatRelativeTime(episode.lastPlayedDate) }
    if (lastWatched != null && presentation.showLastWatched) {
        Text(
            text = lastWatched,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
// endregion

// region Virtual (missing/unaired) episode badge

/**
 * What a virtual episode row's badge shows: [Missing] for an episode whose
 * file is simply absent, [Airs] for an unaired one (carrying the parsed
 * premiere [Airs.date] for the "Airs \<date\>" label).
 */
internal sealed interface MissingEpisodeBadge {
    data object Missing : MissingEpisodeBadge
    data class Airs(val date: kotlinx.datetime.LocalDate) : MissingEpisodeBadge
}

/**
 * The pure badge decision for a virtual episode row: UNAIRED renders its air
 * date (when the premiere stamp parses); everything else — absent file,
 * unknown reason, null/unparseable premiere date — renders "Missing". The
 * rows only invoke this for `isVirtual` items, but a non-virtual item (null
 * reason) degrades to [MissingEpisodeBadge.Missing] rather than crashing.
 */
internal fun missingEpisodeBadge(
    reason: MissingEpisodeReason?,
    premiereDate: String?,
): MissingEpisodeBadge {
    if (reason != MissingEpisodeReason.UNAIRED) return MissingEpisodeBadge.Missing
    val date = premiereDate?.let(::localDateFromIsoTimestamp) ?: return MissingEpisodeBadge.Missing
    return MissingEpisodeBadge.Airs(date)
}

/**
 * The dimmed-state badge for a virtual (missing/unaired) episode row. Dark
 * scrim chrome (mirrors the delete affordance's) so it reads on top of the
 * dimmed thumbnail without fighting the watched tag's tonal container.
 */
@Composable
private fun MissingEpisodeTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White.copy(alpha = 0.9f),
        shape = ShapeCache.smooth12,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.EyeOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The in-thumbnail play affordance shared by both episode-row layouts; they
 * differ only in geometry and TV focusability.
 */
@Composable
private fun EpisodePlayAffordance(
    playScale: Float,
    interactionSource: MutableInteractionSource,
    onPlayClick: () -> Unit,
    iconSize: Dp,
    iconPadding: Dp,
    tvFocusable: Boolean,
) {
    val focusState = if (tvFocusable) rememberTvFocusState(focusedScale = 1.15f) else null
    Icon(
        Tabler.Outline.PlayerPlay,
        contentDescription = stringResource(Res.string.detail_cd_episode_play),
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(iconSize)
            .graphicsLayer { scaleX = playScale; scaleY = playScale }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
            .then(focusState?.focusModifier ?: Modifier)
            .then(
                if (focusState != null) Modifier.tvFocusIndicator(focusState, CircleShape)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlayClick,
            )
            .padding(iconPadding)
    )
}

/**
 * The virtual (missing/unaired) episode's own state marker; callers align it
 * top-start so it never collides with the watched tag or progress overlay.
 */
@Composable
private fun VirtualEpisodeBadge(episode: MediaItem, modifier: Modifier = Modifier) {
    val badge = remember(episode.id, episode.missingReason, episode.premiereDate) {
        missingEpisodeBadge(episode.missingReason, episode.premiereDate)
    }
    val label = when (badge) {
        is MissingEpisodeBadge.Airs ->
            stringResource(Res.string.detail_airs_date_format, shortMonthDayYear(badge.date))
        MissingEpisodeBadge.Missing -> stringResource(Res.string.detail_missing_badge)
    }
    MissingEpisodeTag(label = label, modifier = modifier)
}

/**
 * The jellyfin-plugin-jellyplay anime badge (filler / mixed / recap) for an
 * episode row — a compact tonal chip mirroring [EpisodeWatchedTag]'s chrome
 * minus the check icon, in the secondary container so it never reads as a
 * watch-state. Rendered only when the plugin's marker probe returned a
 * badge-carrying kind for the episode number (ADR 0010: silent absence).
 */
@Composable
internal fun AnimeEpisodeBadge(
    kind: AnimeBadgeKind,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        when (kind) {
            AnimeBadgeKind.FILLER -> Res.string.jellyplay_det_filler_badge
            AnimeBadgeKind.MIXED -> Res.string.jellyplay_det_mixed_badge
            AnimeBadgeKind.RECAP -> Res.string.jellyplay_det_recap_badge
        },
    )
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = ShapeCache.smooth12,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The jellyfin-plugin-jellyplay TMDB score chip (ADR 0010, `ratings`) — the
 * compact "★ 7.8" badge rendered on the episode rows and beside the seasons
 * header's title (the season average). Chip chrome mirrors
 * [AnimeEpisodeBadge]'s compact sizing; the colors MATCH the plugin ratings
 * section's mdblist chips (`JellyPlayPluginRating`
 * rendering in MediaDetailBodySections: onSurface 15% / 95%). Rendered only
 * when the plugin sent a score (silent absence).
 */
@Composable
internal fun TmdbScoreChip(
    score: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
        shape = ShapeCache.smooth12,
    ) {
        Text(
            text = "★ ${formatPluginScore(score)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The episode row's stream-info badge row: the "CC" chip when any
 * subtitle stream exists, the best audio track's layout chip ("Atmos" /
 * "7.1" / "5.1" — absent for stereo and below), and the audio-language-count
 * chip (only when more than one language — a single language is the default
 * and carries no information). Chip chrome mirrors [TmdbScoreChip]'s compact
 * sizing; callers render nothing at all when the episode carries no
 * [EpisodeStreamInfo].
 */
@Composable
private fun EpisodeStreamBadges(
    info: EpisodeStreamInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (info.hasSubtitles) {
            EpisodeStreamChip(text = "CC")
        }
        info.audioLayout?.let { layout ->
            EpisodeStreamChip(text = layout)
        }
        if (info.audioLanguageCount > 1) {
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                shape = ShapeCache.smooth12,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Language,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = info.audioLanguageCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One compact text chip of the [EpisodeStreamBadges] row (the [TmdbScoreChip] chrome). */
@Composable
private fun EpisodeStreamChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
        shape = ShapeCache.smooth12,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The episode thumbnail's in-flight download overlay: the thin
 * determinate bar along the thumbnail's TOP edge — mirroring the played-bar
 * styling (3 dp, primary) the rows render along the bottom edge for watch
 * state, so the two never stack — plus the percent chip at the top-end (the
 * delete affordance owns top-end only once the download completes and this
 * overlay is gone). The fraction comes from [LocalEpisodeDownloadProgress]
 * via the row composables.
 */
@Composable
private fun BoxScope.EpisodeDownloadOverlay(fraction: Float) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth(fraction)
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary),
    )
    Surface(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White.copy(alpha = 0.9f),
        shape = ShapeCache.smooth12,
    ) {
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
// endregion

@Immutable
private data class SeasonEpisodesTargetState(
    val seasonIndex: Int,
    val isLoading: Boolean,
)

/** The per-episode click arms both episode-list variants share. */
private class EpisodeArms(
    val onPlayClick: () -> Unit,
    val onDetailClick: () -> Unit,
    val onLongPress: () -> Unit,
    val onDeleteClick: () -> Unit,
)

/**
 * Per-row lambdas remembered on the episode (the sibling sections'
 * item-keyed pattern) — they capture the full item, so keying on the id
 * alone would keep a re-fetched instance's stale playback position on the
 * play path.
 */
@Composable
private fun rememberEpisodeArms(episode: MediaItem, callbacks: SeasonsSectionCallbacks): EpisodeArms =
    remember(episode) {
        EpisodeArms(
            onPlayClick = { callbacks.onEpisodePlayClick(episode) },
            onDetailClick = { callbacks.onEpisodeDetailClick(episode) },
            onLongPress = { callbacks.onEpisodeLongPress(episode) },
            onDeleteClick = { callbacks.onEpisodeDeleteClick(episode) },
        )
    }
