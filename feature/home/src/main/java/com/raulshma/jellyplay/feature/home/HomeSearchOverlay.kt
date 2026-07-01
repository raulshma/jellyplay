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
import androidx.compose.runtime.LaunchedEffect
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
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier,
) {
    val totalItems = jellyfinResults.size + seerrResults.size
    val hasAnyResults = totalItems > 0

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
                    "No results found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
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
                                text = "Recent Searches",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val clearAllFocusState = rememberTvFocusState()
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .then(clearAllFocusState.focusModifier)
                                    .tvFocusIndicator(clearAllFocusState, ShapeCache.smooth8)
                                    .clip(ShapeCache.smooth8)
                                    .clickable { onClearHistory() }
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
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (jellyfinResults.isNotEmpty()) {
                    item(contentType = "libraryHeader") {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(
                        count = jellyfinResults.size,
                        key = { index -> "jf-${jellyfinResults[index].id}" },
                        contentType = { "libraryItem" },
                    ) { index ->
                        val item = jellyfinResults[index]
                        SearchItemRow(
                            title = item.name,
                            subtitle = buildString {
                                item.year?.let { append(it) }
                                if (item.year != null && item.mediaType != null) append(" · ")
                                when (item.mediaType) {
                                    MediaType.MOVIE -> append("Movie")
                                    MediaType.SERIES -> append("TV Show")
                                    MediaType.AUDIO, MediaType.MUSIC -> append("Music")
                                    else -> item.mediaType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }?.let { append(it) }
                                }
                            },
                            imageUrl = getImageUrl(item.id),
                            onClick = { onJellyfinClick(item) },
                            index = index,
                        )
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
                        Text(
                            text = "Request via Seerr",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(
                        count = seerrResults.size,
                        key = { index -> "sr-${seerrResults[index].id}" },
                        contentType = { "seerrItem" },
                    ) { index ->
                        val item = seerrResults[index]
                        SearchItemRow(
                            title = item.displayName,
                            subtitle = buildString {
                                item.year?.let { append(it) }
                                val typeLabel = when {
                                    item.mediaType.equals("movie", ignoreCase = true) -> "Movie"
                                    item.mediaType.equals("tv", ignoreCase = true) -> "TV Show"
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
                            },
                            imageUrl = item.posterUrl ?: "",
                            onClick = { onSeerrClick(item) },
                            index = index + jellyfinResults.size,
                        )
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
}

@Composable
private fun SearchItemRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    index: Int,
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
    val isFocused = tvFocusState.isFocused
    val backgroundColor = when {
        isPressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "searchItemAnim",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth12)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = animationProgress
                translationY = (1f - animationProgress) * 8f
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
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ShapeCache.smooth10),
                    contentScale = ContentScale.Crop,
                )
            } else {
                androidx.compose.material3.Icon(
                    Tabler.Outline.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    }
}
