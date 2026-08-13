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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.sharedElementBoundsSpec
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRelativeTime
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun SeasonsSection(
    seriesItem: MediaItem,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
    getImageUrl: (String) -> String,
    currentItemId: String? = null,
    currentSeasonId: String? = null,
    onEpisodePlayClick: (MediaItem) -> Unit,
    onEpisodeDetailClick: (MediaItem) -> Unit,
    onEpisodeLongPress: (MediaItem) -> Unit = {},
    onFocusedEpisodeChange: (MediaItem) -> Unit = {},
    onSeasonSelected: (seasonId: String) -> Unit = {},
    hideEpisodeThumbnails: Boolean = false,
    episodesDescending: Boolean = true,
    onEpisodesDescendingChange: (Boolean) -> Unit = {},
    compactEpisodeList: Boolean = false,
    onCompactEpisodeListChange: (Boolean) -> Unit = {},
    onMarkSeasonPlayed: (seasonId: String) -> Unit = {},
    onMarkSeasonUnplayed: (seasonId: String) -> Unit = {},
    // ── Unified episode parity ──
    // Set of downloaded episode ids (gates the per-episode delete badge) — null
    // hides the affordance entirely (plain remote series with no downloads).
    downloadedEpisodeIds: Set<String>? = null,
    /** Per-episode delete (downloaded episodes only). */
    onEpisodeDeleteClick: (MediaItem) -> Unit = {},
    /**
     * Per-episode local artwork resolver: returns an on-disk path when the
     * episode has a downloaded thumbnail, else null (caller falls back to the
     * server [getImageUrl]). Null disables the local-first lookup.
     */
    getEpisodeLocalImagePath: ((MediaItem) -> String?)? = null,
) {
    // ── DEFERRED FOR LOCAL ORIGIN ────────────────────────────────────────────
    // The following affordances remain ONLINE-ONLY and are deliberately NOT
    // implemented for a local/offline origin:
    //   • hideEpisodeThumbnails / spoiler overlay — local cards always show art
    //     (hiding thumbnails without guaranteed local artwork yields blank tiles).
    //   • skipSpecials (S0 filtering) — local series render every season.
    //   • press-and-hold peek (rememberMediaPeek) — local cards don't peek.
    // Episode sort order ([episodesDescending]) and its toggle ARE honored for a
    // local origin: offline episodes load in canonical ascending playback order
    // (same as online), so reversing to newest-first is a meaningful choice.
    // ─────────────────────────────────────────────────────────────────────────
    val smartTargetSeasonId = smartPlayTarget?.episode?.seasonId
    val initialSeasonIndex = when {
        smartTargetSeasonId != null -> {
            seasons.indexOfFirst { it.id == smartTargetSeasonId }.coerceAtLeast(0)
        }
        currentSeasonId != null -> {
            seasons.indexOfFirst { it.id == currentSeasonId }.coerceAtLeast(0)
        }
        else -> 0
    }
    var selectedSeasonIndex by remember { mutableStateOf(initialSeasonIndex) }
    // Episode sort order within a season. Persisted app-wide (see
    // [DetailViewModel.setEpisodesDescending]) so the choice carries across
    // every series detail screen — the previous local `remember` reset it to
    // "newest first" on each navigation.

    LaunchedEffect(selectedSeasonIndex) {
        val season = seasons.getOrNull(selectedSeasonIndex)
        if (season != null) {
            onSeasonSelected(season.id)
        }
    }

    // Compact vertical list is mobile-only: the toggle is offered (and the list
    // rendered) solely on compact-width, non-TV form factors. TV keeps the
    // horizontal D-pad focus row; tablet/expanded keeps the denser horizontal
    // overview. Resolved once here so the header toggle and the episode branch
    // agree.
    val isTv = LocalTvMode.current
    val isCompactWidth = LocalAdaptiveInfo.current.windowSizeClass ==
        com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Compact
    val useCompactListAvailable = !isTv && isCompactWidth
    val useCompactList = useCompactListAvailable && compactEpisodeList

    Column {
        FadingItem {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_section_seasons),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Layout switch (compact vertical list ↔ horizontal cards).
                    // Only offered on compact/mobile widths — TV and tablet always
                    // use the horizontal focus row.
                    if (useCompactListAvailable) {
                        val layoutFocusState = rememberTvFocusState(focusedScale = 1.1f)
                        Surface(
                            modifier = Modifier
                                .clip(ShapeCache.smooth16)
                                .then(layoutFocusState.focusModifier)
                                .then(Modifier.tvFocusIndicator(layoutFocusState, ShapeCache.smooth16))
                                .clickable { onCompactEpisodeListChange(!compactEpisodeList) },
                            color = if (compactEpisodeList) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = ShapeCache.smooth16,
                        ) {
                            Icon(
                                imageVector = if (compactEpisodeList) Tabler.Outline.LayoutGrid else Tabler.Outline.List,
                                contentDescription = stringResource(
                                    if (compactEpisodeList) R.string.detail_cd_switch_to_cards
                                    else R.string.detail_cd_switch_to_list
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
                            .clickable { onEpisodesDescendingChange(!episodesDescending) },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = ShapeCache.smooth16,
                    ) {
                        Icon(
                            imageVector = if (episodesDescending) Tabler.Outline.SortDescending2 else Tabler.Outline.SortAscending2,
                            contentDescription = stringResource(
                                if (episodesDescending) R.string.detail_cd_sort_oldest_first
                                else R.string.detail_cd_sort_newest_first
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
                    R.string.detail_season_format, season.indexNumber ?: (index + 1),
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
                                onClick = { selectedSeasonIndex = index },
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
                                    .then(Modifier.tvFocusIndicator(trailingFocusState, ShapeCache.smooth4)),
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Eye,
                                    contentDescription = stringResource(R.string.detail_cd_season_options),
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
                            text = { Text(stringResource(R.string.detail_mark_season_watched)) },
                            onClick = {
                                menuExpanded = false
                                onMarkSeasonPlayed(season.id)
                            },
                            leadingIcon = { Icon(Tabler.Outline.Eye, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_mark_season_unwatched)) },
                            onClick = {
                                menuExpanded = false
                                onMarkSeasonUnplayed(season.id)
                            },
                            leadingIcon = { Icon(Tabler.Outline.EyeOff, contentDescription = null) },
                        )
                    }
                }
        }

        Spacer(Modifier.height(20.dp))

        val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
        val seasonEpisodes = selectedSeason?.let { episodes[it.id] }
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
            targetState = Triple(selectedSeasonIndex, seasonEpisodes?.size ?: 0, useCompactList),
            transitionSpec = {
                fadeIn(
                    animationSpec = seasonFadeIn,
                ) togetherWith fadeOut(
                    animationSpec = seasonFadeOut,
                )
            },
            label = "seasonEpisodes",
        ) { (seasonIdx, episodeCount, isCompact) ->
            val sharedTransitionScope = LocalSharedTransitionScope.current
            val animatedVisibilityScope = this
            // Memoize the sort + reverse so a recomposition triggered by an
            // unrelated parent state change (e.g. sibling animation) doesn't
            // re-sort this season's episode list.
            val currentEpisodes = remember(seasonIdx, episodes, episodesDescending) {
                seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
                    ?.sortedBy { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE }
                    ?.let { sorted -> if (episodesDescending) sorted.reversed() else sorted }
            }
            val currentIsFetched = seasons.getOrNull(seasonIdx)?.id?.let { fetchedSeasonIds.contains(it) } ?: false
            val currentIsLoading = currentEpisodes == null && seasons.getOrNull(seasonIdx) != null && !currentIsFetched

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
                    if (isCompact) {
                        // Plain Column (not lazy): this section is already nested
                        // inside the screen's LazyColumn, so a same-direction
                        // nested lazy list is disallowed. Season episode counts are
                        // small enough that composing every row is cheap.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            currentEpisodes.forEach { episode ->
                                CompactEpisodeRow(
                                    episode = episode,
                                    getImageUrl = getImageUrl,
                                    isCurrentEpisode = episode.id == currentItemId,
                                    onPlayClick = { onEpisodePlayClick(episode) },
                                    onDetailClick = { onEpisodeDetailClick(episode) },
                                    onLongPress = { onEpisodeLongPress(episode) },
                                    hideThumbnail = hideEpisodeThumbnails,
                                    isDownloaded = downloadedEpisodeIds?.contains(episode.id) == true,
                                    onDeleteClick = { onEpisodeDeleteClick(episode) },
                                    localImagePath = getEpisodeLocalImagePath?.invoke(episode),
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
                            items = currentEpisodes,
                            key = { "episode_${it.id}" },
                            contentType = { _, _ -> "episode" },
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            onFocusedIndexChange = { index ->
                                currentEpisodes.getOrNull(index)?.let(onFocusedEpisodeChange)
                            },
                        ) { _, episode, focusModifier ->
                                EpisodeCard(
                                    episode = episode,
                                    getImageUrl = getImageUrl,
                                    isCurrentEpisode = episode.id == currentItemId,
                                    onPlayClick = { onEpisodePlayClick(episode) },
                                    onDetailClick = { onEpisodeDetailClick(episode) },
                                    onLongPress = { onEpisodeLongPress(episode) },
                                    modifier = focusModifier,
                                    hideThumbnail = hideEpisodeThumbnails,
                                    isDownloaded = downloadedEpisodeIds?.contains(episode.id) == true,
                                    onDeleteClick = { onEpisodeDeleteClick(episode) },
                                    localImagePath = getEpisodeLocalImagePath?.invoke(episode),
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
                else -> {
                    // episode-less season was a plain text line.
                    // Use the standard empty state (icon + title + description).
                    FadingItem {
                        com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                            icon = Tabler.Outline.Movie,
                            title = stringResource(R.string.detail_season_empty_title),
                            description = stringResource(R.string.detail_season_empty_description),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
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
    getImageUrl: (String) -> String,
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
    /** On-disk thumbnail path; preferred over [getImageUrl] when non-null. */
    localImagePath: String? = null,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "episodeCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "episodePlayScale",
    )

    val cardFocusState = rememberTvFocusState(focusedScale = 1.03f)
    // Episode cards are wide (thumbnail + metadata), scaling ~1.5× the adaptive poster width.
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = (adaptiveInfo.rowCardWidth(isTv) * 1.5f).coerceAtLeast(260.dp)

    // Build the episode image URL once per episode instead of 3× per recomposition.
    // Prefer the on-disk local thumbnail (a downloaded episode's saved Primary
    // image) before the server [getImageUrl] fallback — matches the offline card.
    val episodeImageUrl = remember(episode.id, localImagePath) {
        localImagePath ?: getImageUrl(episode.id)
    }

    // Press-and-hold "peek" preview; no-op on TV / when no controller is wired.
    val peek = rememberMediaPeek(
        item = episode,
        posterUrl = episodeImageUrl,
        backdropUrl = episodeImageUrl,
        blurHash = episode.blurHashes.primary,
    )
    rememberReleaseDismiss(isCardPressed)

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    // Read theme colors here (composable scope) so the remember block below
    // doesn't need to call composable functions.
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    // Depends only on the active theme, not on the per-episode data — wrap in
    // remember so the Modifier + gradient aren't rebuilt per card per recompose.
    val borderModifier = remember(isSynthwave, isSoothing, primaryColor, secondaryColor, outlineColor) {
        when {
            isSynthwave -> Modifier.border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor, secondaryColor)
                ),
                shape = ShapeCache.smooth16
            )
            isSoothing -> Modifier.border(
                width = 0.8.dp,
                color = outlineColor.copy(alpha = 0.35f),
                shape = ShapeCache.smooth16
            )
            else -> Modifier
        }
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
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .then(cardFocusState.focusModifier)
            .then(peek.boundsModifier)
            .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
                onLongClick = onLongPress ?: peek.onLongClick,
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
            if (!hideThumbnail) {
                MediaImage(
                    url = episodeImageUrl,
                    contentDescription = episode.name,
                    blurHash = episode.blurHashes.primary,
                    // Episode thumbnails render up to ~480 dp wide × 16:9. Decode a
                    // right-sized bitmap (4–8 cards compose simultaneously).
                    size = coil3.size.Size(640, 360),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (episode.isPlayed) Modifier.graphicsLayer { alpha = 0.65f } else Modifier),
                    contentScale = ContentScale.Crop,
                )
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.detail_spoiler),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(R.string.detail_cd_episode_play),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                    .then(epPlayFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(epPlayFocusState, CircleShape))
                    .clickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                        onClick = onPlayClick,
                    )
                    .padding(8.dp)
            )

            val positionTicks = episode.playbackPositionTicks
            if (positionTicks != null && positionTicks > 0 && !episode.isPlayed) {
                val progress = episode.progressFraction() ?: 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else if (episode.isPlayed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
            }
            val cardPrefs = com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences.current
            if (episode.isPlayed && cardPrefs.showWatchedCheckmark) {
                com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag(
                    label = stringResource(R.string.detail_watched_badge),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 8.dp),
                )
            }

            // Per-episode delete affordance — only for a downloaded episode
            // (gated by `isDownloaded`, which the host sets from the downloaded-
            // episode-id set or the local origin). Online episodes never show
            // this (downloading stays in `SeriesDownloadSheet`).
            if (isDownloaded) {
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
                        contentDescription = stringResource(R.string.detail_delete_episode_cd),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = buildString {
                    episode.indexNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val runtimeTicks = episode.runTimeTicks
            val positionTicks = episode.playbackPositionTicks
            val hasWatchProgress = positionTicks != null && positionTicks > 0 && !episode.isPlayed
            val remainingTime = if (hasWatchProgress && runtimeTicks != null && positionTicks != null) {
                formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
            } else null
            val totalTime = if (runtimeTicks != null) {
                formatDurationFromTicks(runtimeTicks)
            } else null

            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_time_left_format, remainingTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (totalTime != null) {
                Text(
                    text = totalTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Last-watched relative timestamp (e.g. "2d ago"). Ports the offline
            // card's lastPlayedDate line so the field is not lost in the unified
            // card. Shown when the episode has any watch activity and the relative
            // formatter could parse the stored timestamp.
            val lastWatched = formatRelativeTime(episode.lastPlayedDate)
            if (lastWatched != null && (episode.isPlayed || (positionTicks != null && positionTicks > 0))) {
                Text(
                    text = lastWatched,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
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
 * watched tag pop while quickly swiping through a season.
 *
 * No per-episode download/delete: online episodes have no delete action
 * (downloading stays in `SeriesDownloadSheet`), matching [EpisodeCard]. A
 * downloaded episode shows a trailing trash affordance instead.
 */
@Composable
private fun CompactEpisodeRow(
    episode: MediaItem,
    getImageUrl: (String) -> String,
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

    val episodeImageUrl = remember(episode.id, localImagePath) {
        localImagePath ?: getImageUrl(episode.id)
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
            if (!hideThumbnail) {
                MediaImage(
                    url = episodeImageUrl,
                    contentDescription = episode.name,
                    blurHash = episode.blurHashes.primary,
                    size = coil3.size.Size(256, 144),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (episode.isPlayed) Modifier.graphicsLayer { alpha = 0.65f } else Modifier),
                    contentScale = ContentScale.Crop,
                )
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.detail_spoiler),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(R.string.detail_cd_episode_play),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                        onClick = onPlayClick,
                    )
                    .padding(6.dp)
            )

            val positionTicks = episode.playbackPositionTicks
            if (positionTicks != null && positionTicks > 0 && !episode.isPlayed) {
                val progress = episode.progressFraction() ?: 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else if (episode.isPlayed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
            }
            val cardPrefs = com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences.current
            if (episode.isPlayed && cardPrefs.showWatchedCheckmark) {
                com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag(
                    label = stringResource(R.string.detail_watched_badge),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 6.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = buildString {
                    episode.indexNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val runtimeTicks = episode.runTimeTicks
            val positionTicks = episode.playbackPositionTicks
            val hasWatchProgress = positionTicks != null && positionTicks > 0 && !episode.isPlayed
            val remainingTime = if (hasWatchProgress && runtimeTicks != null && positionTicks != null) {
                formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
            } else null
            val totalTime = if (runtimeTicks != null) {
                formatDurationFromTicks(runtimeTicks)
            } else null

            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_time_left_format, remainingTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (totalTime != null) {
                Text(
                    text = totalTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Last-watched relative timestamp — ports the offline compact row's
            // lastPlayedDate line so the field is not lost in the unified card.
            val lastWatched = formatRelativeTime(episode.lastPlayedDate)
            if (lastWatched != null && (episode.isPlayed || (positionTicks != null && positionTicks > 0))) {
                Text(
                    text = lastWatched,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Per-episode delete (downloaded episodes only). Sits at the trailing
        // edge of the row rather than overlaid on the thumbnail (as on the card)
        // — the compact row has room for a dedicated affordance. Mirrors the
        // offline compact row.
        if (isDownloaded) {
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
                    contentDescription = stringResource(R.string.detail_delete_episode_cd),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}