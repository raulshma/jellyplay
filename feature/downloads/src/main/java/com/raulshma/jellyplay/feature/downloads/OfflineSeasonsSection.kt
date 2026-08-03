package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.sharedElementBoundsSpec
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Shared seasons + episodes block for offline series, mirroring the online
 * SeasonsSection: a "Seasons" label, a row of animated season tabs, then a
 * fade-transitioning horizontal row of [OfflineEpisodeCard]s for the selected
 * season.
 *
 * Each season tab is a split button whose trailing affordance opens a menu to
 * mark the whole season as watched/unwatched — the offline counterpart of the
 * online season-mark controls. The row renders whenever the series has at
 * least one downloaded season so a single-season series can still be marked
 * (matching the online detail screen).
 *
 * Used by both [OfflineSeriesScreen] (the whole-series view) and
 * [OfflineDetailScreen] (when an episode is open) so the two stay visually
 * consistent.
 *
 * @param currentItemId id of the episode currently open (when used from the
 *   episode detail screen) so the matching card is highlighted; null on the
 *   series screen.
 * @param currentSeasonId id of the season to auto-select initially; null picks
 *   the first season.
 * @param onMarkSeasonPlayed marks every downloaded episode in a season watched.
 * @param onMarkSeasonUnplayed marks every downloaded episode in a season
 *   unwatched (clears position/percentage).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun OfflineSeasonsSection(
    seasons: List<OfflineMediaItem>,
    episodes: Map<String, List<OfflineMediaItem>>,
    contentPad: Dp,
    currentItemId: String? = null,
    currentSeasonId: String? = null,
    onEpisodePlay: (OfflineMediaItem) -> Unit,
    onEpisodeDetail: (OfflineMediaItem) -> Unit,
    onEpisodeDelete: (OfflineMediaItem) -> Unit,
    onMarkSeasonPlayed: (seasonId: String) -> Unit = {},
    onMarkSeasonUnplayed: (seasonId: String) -> Unit = {},
    compactEpisodeList: Boolean = false,
    onCompactEpisodeListChange: (Boolean) -> Unit = {},
) {
    val initialSeasonIndex = currentSeasonId?.let { id ->
        seasons.indexOfFirst { it.id == id }.coerceAtLeast(0)
    } ?: 0
    var selectedSeasonIndex by remember { mutableIntStateOf(initialSeasonIndex) }
    // Capture in composable scope; AnimatedContent's transitionSpec is not composable.
    val seasonFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val seasonFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    // Shared-element spring for the layout-switch morph — mirrors the online
    // SeasonsSection: the compact-row thumbnail and the wide-card thumbnail are
    // the same image, so the outgoing/incoming copies glide between their
    // bounds while the surrounding metadata crossfades.
    val episodeThumbBoundsTransform: BoundsTransform = { _, _ ->
        sharedElementBoundsSpec()
    }

    // Pending per-episode delete confirmation. The per-episode trash badge is a
    // small touch target overlapping the play affordance on a 16:9 thumbnail, so
    // confirm before removing the file — matching the per-series confirmation.
    var pendingDelete by remember { mutableStateOf<OfflineMediaItem?>(null) }

    // Re-select the matching season if the highlighted episode's season changes
    // (e.g. after the detail screen loads its seasons).
    LaunchedEffect(currentSeasonId, seasons) {
        if (currentSeasonId != null) {
            val idx = seasons.indexOfFirst { it.id == currentSeasonId }
            if (idx in seasons.indices && idx != selectedSeasonIndex) {
                selectedSeasonIndex = idx
            }
        }
    }

    // Compact vertical list is mobile-only — mirrors the online SeasonsSection:
    // offered (and rendered) solely on compact-width, non-TV form factors.
    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    val isCompactWidth = com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo.current.windowSizeClass ==
        com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Compact
    val useCompactListAvailable = !isTv && isCompactWidth
    val useCompactList = useCompactListAvailable && compactEpisodeList

    Column {
        if (seasons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPad),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.downloads_seasons),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (useCompactListAvailable) {
                    val layoutFocusState = rememberTvFocusState(focusedScale = 1.1f)
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .clip(ShapeCache.smooth16)
                            .then(layoutFocusState.focusModifier)
                            .tvFocusIndicator(layoutFocusState, ShapeCache.smooth16)
                            .clickable { onCompactEpisodeListChange(!compactEpisodeList) },
                        color = if (compactEpisodeList) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = ShapeCache.smooth16,
                    ) {
                        Icon(
                            imageVector = if (compactEpisodeList) Tabler.Outline.LayoutGrid else Tabler.Outline.List,
                            contentDescription = stringResource(
                                if (compactEpisodeList) R.string.downloads_cd_switch_to_cards
                                else R.string.downloads_cd_switch_to_list
                            ),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TvFocusableItemRow(
                items = seasons,
                key = { it.id },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { index, season, focusModifier ->
                OfflineSeasonTab(
                    season = season,
                    seasonIndex = index,
                    selected = selectedSeasonIndex == index,
                    onClick = { selectedSeasonIndex = index },
                    onMarkSeasonPlayed = { onMarkSeasonPlayed(season.id) },
                    onMarkSeasonUnplayed = { onMarkSeasonUnplayed(season.id) },
                    modifier = focusModifier,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        AnimatedContent(
            targetState = Triple(
                selectedSeasonIndex,
                seasons.getOrNull(selectedSeasonIndex)?.let { episodes[it.id]?.size } ?: 0,
                useCompactList,
            ),
            transitionSpec = {
                fadeIn(animationSpec = seasonFadeIn) togetherWith fadeOut(animationSpec = seasonFadeOut)
            },
            label = "offlineSeasonEpisodes",
        ) { (seasonIdx, _, isCompact) ->
            val sharedTransitionScope = LocalSharedTransitionScope.current
            val animatedVisibilityScope = this
            val season = seasons.getOrNull(seasonIdx)
            val seasonEpisodes = season?.let { episodes[it.id] } ?: emptyList()
            if (seasonEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    ScreenEmptyState(
                        icon = Tabler.Outline.DeviceFloppy,
                        title = stringResource(R.string.downloads_no_episodes_season),
                    )
                }
            } else {
                if (isCompact) {
                    // Plain Column (not lazy): nested inside the screen's
                    // LazyColumn, so a same-direction nested lazy list is
                    // disallowed. Season episode counts are small enough that
                    // composing every row is cheap.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = contentPad),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seasonEpisodes.forEach { episode ->
                            OfflineCompactEpisodeRow(
                                episode = episode,
                                isCurrentEpisode = episode.id == currentItemId,
                                onPlayClick = { onEpisodePlay(episode) },
                                onDetailClick = { onEpisodeDetail(episode) },
                                onDelete = { pendingDelete = episode },
                                sharedThumbnailModifier = episodeThumbSharedModifier(
                                    episodeId = episode.id,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = episodeThumbBoundsTransform,
                                ),
                            )
                        }
                    }
                } else {
                    TvFocusableItemRow(
                        items = seasonEpisodes,
                        key = { "episode_${it.id}" },
                        contentPadding = PaddingValues(horizontal = contentPad),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) { _, episode, focusModifier ->
                        OfflineEpisodeCard(
                            episode = episode,
                            isCurrentEpisode = episode.id == currentItemId,
                            onPlayClick = { onEpisodePlay(episode) },
                            onDetailClick = { onEpisodeDetail(episode) },
                            onDelete = { pendingDelete = episode },
                            modifier = focusModifier,
                            sharedThumbnailModifier = episodeThumbSharedModifier(
                                episodeId = episode.id,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = episodeThumbBoundsTransform,
                            ),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { episode ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text(stringResource(R.string.downloads_delete_episode_title)) },
            text = { Text(stringResource(R.string.downloads_delete_episode_message, episode.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onEpisodeDelete(episode)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.downloads_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.downloads_cancel)) }
            },
        )
    }
}

/**
 * Shared-element thumbnail morph between the compact vertical rows and the
 * horizontal cards — mirrors the online SeasonsSection helper: both layouts
 * render the same episode thumbnail, so the outgoing/incoming copies glide
 * between their bounds (128×72 ↔ 16:9 card width) while the rest of the card
 * crossfades. No-op when the app-level shared transition scope is unavailable
 * (performance mode) — the layouts then swap instantly.
 *
 * Keyed with an "offline_" prefix so the offline and online detail screens
 * never pair shared elements for the same episode id within the app-wide
 * shared transition scope.
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
            sharedContentState = rememberSharedContentState(key = "offline_episode_thumb_$episodeId"),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = boundsTransform,
        )
    }
}

/**
 * Animated season selector tab, matching the offline series screen's styling.
 *
 * Rendered as a split button: the leading button selects the season, and the
 * trailing button opens a menu to mark the season watched/unwatched — mirroring
 * the online [SeasonsSection] affordance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflineSeasonTab(
    season: OfflineMediaItem,
    seasonIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onMarkSeasonPlayed: () -> Unit,
    onMarkSeasonUnplayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val targetContentColor = if (selected) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.onSurface
    val surfaceColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineSeasonColor",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineSeasonContentColor",
    )
    val leadingFocusState = rememberTvFocusState(focusedScale = 1.05f)
    val trailingFocusState = rememberTvFocusState(focusedScale = 1.05f)
    val seasonColors = ButtonDefaults.buttonColors(
        containerColor = surfaceColor,
        contentColor = contentColor,
    )
    val defaultSeasonName = stringResource(R.string.downloads_season_default, season.seasonNumber ?: (seasonIndex + 1))
    val seasonName = season.name.takeIf { it.isNotBlank() } ?: defaultSeasonName
    // Compact (extra-small) split-button variant — these tabs sit in a dense
    // horizontal row, so use the xsmall container height (shorter than the
    // default SmallContainerHeight) and a tighter label style, matching the
    // online SeasonsSection.
    val containerHeight = SplitButtonDefaults.ExtraSmallContainerHeight
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .then(leadingFocusState.focusModifier)
            .tvFocusIndicator(leadingFocusState, ShapeCache.smooth16),
    ) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onClick,
                    colors = seasonColors,
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(containerHeight),
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(containerHeight),
                ) {
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
                        .tvFocusIndicator(trailingFocusState, ShapeCache.smooth4),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Eye,
                        contentDescription = stringResource(R.string.downloads_mark_season_cd),
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
                text = { Text(stringResource(R.string.downloads_mark_season_watched)) },
                onClick = {
                    menuExpanded = false
                    onMarkSeasonPlayed()
                },
                leadingIcon = { Icon(Tabler.Outline.Eye, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_mark_season_unwatched)) },
                onClick = {
                    menuExpanded = false
                    onMarkSeasonUnplayed()
                },
                leadingIcon = { Icon(Tabler.Outline.EyeOff, contentDescription = null) },
            )
        }
    }
}
