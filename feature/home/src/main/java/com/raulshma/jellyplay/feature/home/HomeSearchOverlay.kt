package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import androidx.compose.ui.graphics.Color
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.X

@Composable
fun HomeSearchResultsOverlay(
    jellyfinResults: List<MediaItem>,
    seerrResults: List<SeerrSearchItem>,
    isSearching: Boolean,
    getImageUrl: (String) -> String,
    onJellyfinClick: (MediaItem) -> Unit,
    onSeerrClick: (SeerrSearchItem) -> Unit,
    searchHistory: List<com.raulshma.jellyplay.core.data.repository.SearchHistoryItem> = emptyList(),
    onHistoryClick: (String) -> Unit = {},
    onDeleteHistoryItem: (Long) -> Unit = {},
    onClearHistory: () -> Unit = {},
    settingsResults: List<ResolvedSettingsItem> = emptyList(),
    onSettingsClick: (ResolvedSettingsItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val totalItems = jellyfinResults.size + seerrResults.size + settingsResults.size
    val hasAnyResults = totalItems > 0

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
    ) {
        if (isSearching && !hasAnyResults) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
                androidx.compose.material3.LoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (!hasAnyResults && !isSearching && searchHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.home_no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Hoisted out of the per-item lambdas below: these three labels are
            // constant, but were previously looked up per visible row per
            // keystroke during search-as-you-type (N×3 string resolutions).
            val movieLabel = stringResource(R.string.home_movie)
            val seriesLabel = stringResource(R.string.home_tv_show)
            val musicLabel = stringResource(R.string.home_music)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (!hasAnyResults && searchHistory.isNotEmpty()) {
                    item(contentType = "historyHeader") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.home_recent_searches),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val clearAllFocusState = rememberTvFocusState()
                            Text(
                                text = stringResource(R.string.home_clear_all),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .then(clearAllFocusState.focusModifier)
                                    .tvFocusIndicator(clearAllFocusState, ShapeCache.smooth8)
                                    .clip(ShapeCache.smooth8)
                                    .clickable { showClearHistoryDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(
                        count = searchHistory.size,
                        key = { "history-${searchHistory[it].id}" },
                        contentType = { "historyItem" },
                    ) { index ->
                        val historyItem = searchHistory[index]
                        val placementSpec = lazyItemPlacementSpec()
                        val itemFocusState = rememberTvFocusState()
                        val deleteFocusState = rememberTvFocusState()
                        val isItemFocused = itemFocusState.isFocused
                        val isDeleteFocused = deleteFocusState.isFocused

                        val itemBgColor = if (isItemFocused) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }

                        val deleteBgColor = if (isDeleteFocused) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .animateItem(placementSpec = placementSpec)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(itemFocusState.focusModifier)
                                    .tvFocusIndicator(itemFocusState, ShapeCache.smooth12)
                                    .clip(ShapeCache.smooth12)
                                    .background(itemBgColor)
                                    .clickable { onHistoryClick(historyItem.query) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Tabler.Outline.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = historyItem.query,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .then(deleteFocusState.focusModifier)
                                    .tvFocusIndicator(deleteFocusState, ShapeCache.smooth8)
                                    .clip(ShapeCache.smooth8)
                                    .background(deleteBgColor)
                                    .clickable { onDeleteHistoryItem(historyItem.id) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Tabler.Outline.X,
                                    contentDescription = stringResource(R.string.home_remove),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (jellyfinResults.isNotEmpty()) {
                    item(contentType = "libraryHeader") {
                        SearchSectionHeader(stringResource(R.string.home_library))
                    }
                    items(
                        count = jellyfinResults.size,
                        key = { index -> "jf-${jellyfinResults[index].id}" },
                        contentType = { "libraryItem" },
                    ) { index ->
                        val item = jellyfinResults[index]
                        val placementSpec = lazyItemPlacementSpec()
                        // Memoized per row on stable primitives so search-as-you-type
                        // recompositions don't re-run buildString + branches for every
                        // visible row.
                        val subtitle = remember(item.id, item.year, item.mediaType) {
                            buildString {
                                item.year?.let { append(it) }
                                if (item.year != null && item.mediaType != null) append(" · ")
                                when (item.mediaType) {
                                    MediaType.MOVIE -> append(movieLabel)
                                    MediaType.SERIES -> append(seriesLabel)
                                    MediaType.AUDIO, MediaType.MUSIC -> append(musicLabel)
                                    else -> item.mediaType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }?.let { append(it) }
                                }
                            }
                        }
                        Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                            val imageUrl = getImageUrl(item.id)
                            HomeSearchResultRow(
                                title = item.name,
                                subtitle = subtitle,
                                onClick = { onJellyfinClick(item) },
                                tileBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                leading = {
                                    if (imageUrl.isNotBlank()) {
                                        MediaImage(
                                            url = imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(ShapeCache.smooth10),
                                            contentScale = ContentScale.Crop,
                                            // 44dp thumbnail at 3× density ≈ 132px; decode at 128² instead
                                            // of the 384² default to cut memory and decode cost during
                                            // search-as-you-type.
                                            size = CoilSize(128, 128),
                                        )
                                    } else {
                                        androidx.compose.material3.Icon(
                                            Tabler.Outline.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                if (seerrResults.isNotEmpty()) {
                    if (jellyfinResults.isNotEmpty()) {
                        item(contentType = "divider") {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            )
                        }
                    }
                    item(contentType = "seerrHeader") {
                        SearchSectionHeader(stringResource(R.string.home_request_via_seerr))
                    }
                    items(
                        count = seerrResults.size,
                        key = { index -> "sr-${seerrResults[index].id}" },
                        contentType = { "seerrItem" },
                    ) { index ->
                        val item = seerrResults[index]
                        val placementSpec = lazyItemPlacementSpec()
                        val subtitle = remember(item.id, item.year, item.mediaType, item.voteAverage) {
                            buildString {
                                item.year?.let { append(it) }
                                val typeLabel = when {
                                    item.mediaType.equals("movie", ignoreCase = true) -> movieLabel
                                    item.mediaType.equals("tv", ignoreCase = true) -> seriesLabel
                                    else -> item.mediaType
                                }
                                if (item.year != null) append(" · ")
                                append(typeLabel)
                                item.voteAverage?.let { rating ->
                                    if (rating > 0) {
                                        append(" · ★ ")
                                        append(String.format("%.1f", rating))
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                            val posterUrl = item.posterUrl ?: ""
                            HomeSearchResultRow(
                                title = item.displayName,
                                subtitle = subtitle,
                                onClick = { onSeerrClick(item) },
                                tileBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                leading = {
                                    if (posterUrl.isNotBlank()) {
                                        MediaImage(
                                            url = posterUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(ShapeCache.smooth10),
                                            contentScale = ContentScale.Crop,
                                            size = CoilSize(128, 128),
                                        )
                                    } else {
                                        androidx.compose.material3.Icon(
                                            Tabler.Outline.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                if (settingsResults.isNotEmpty()) {
                    if (jellyfinResults.isNotEmpty() || seerrResults.isNotEmpty()) {
                        item(contentType = "settingsDivider") {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            )
                        }
                    }
                    item(contentType = "settingsHeader") {
                        SearchSectionHeader(stringResource(R.string.home_settings))
                    }
                    items(
                        count = settingsResults.size,
                        key = { index -> "set-${settingsResults[index].id}" },
                        contentType = { "settingsItem" },
                    ) { index ->
                        val item = settingsResults[index]
                        val placementSpec = lazyItemPlacementSpec()
                        Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                            HomeSearchResultRow(
                                title = item.title,
                                subtitle = item.subtitle,
                                onClick = { onSettingsClick(item) },
                                tileBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                leading = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailing = if (item.isAdvanced) {
                                    {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .clip(ShapeCache.smooth8)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
                if (isSearching) {
                    item(contentType = "loadingIndicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
                            androidx.compose.material3.LoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        com.raulshma.jellyplay.core.ui.components.ConfirmDialog(
            title = stringResource(R.string.home_clear_search_history_title),
            message = stringResource(R.string.home_clear_search_history_message),
            confirmText = stringResource(R.string.home_clear),
            dismissText = stringResource(R.string.home_cancel),
            onConfirm = onClearHistory,
            onDismiss = { showClearHistoryDialog = false },
        )
    }
}

@Composable
private fun SearchSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = SearchSectionHeaderLetterSpacing,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Letter spacing shared by the search-overlay section headers ("Library",
 *  "Request via Seerr", "Settings"). Extracted so the four call sites stay in
 *  sync instead of each hardcoding `0.8.sp`. */
private val SearchSectionHeaderLetterSpacing = 0.8.sp

@Composable
private fun HomeSearchResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tileBackground: Color,
    leading: @Composable () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "searchItemScale",
    )
    val scale = if (isTv) 1f else baseScale
    val backgroundColor = when {
        isPressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        tvFocusState.isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    // No per-item entrance animation: in a LazyColumn, items are disposed on
    // scroll-out and re-composed on scroll-back, so a `LaunchedEffect(Unit)`
    // entrance animation would re-fire and flicker on every re-entry.

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth12)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ShapeCache.smooth12)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(ShapeCache.smooth10)
                .background(tileBackground),
            contentAlignment = Alignment.Center,
        ) {
            leading()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

