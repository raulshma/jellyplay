package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

private enum class PersonFilmographyFilter(val label: String, val match: (MediaItem) -> Boolean) {
    ALL("All", { true }),
    MOVIES("Movies", { it.mediaType == MediaType.MOVIE }),
    TV("TV", { it.mediaType == MediaType.SERIES }),
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonFilmography(
    filmography: List<MediaItem>,
    biography: String?,
    getImageUrl: (String) -> String,
    onItemClick: (String) -> Unit,
    contentPad: androidx.compose.ui.unit.Dp,
    gridMin: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    var filter by remember { mutableStateOf(PersonFilmographyFilter.ALL) }
    val visibleFilmography by remember(filmography, filter) {
        derivedStateOf { filmography.filter(filter.match) }
    }

    TvFocusableGrid(
        itemCount = visibleFilmography.size,
        key = { visibleFilmography[it].id },
        columns = GridCells.Adaptive(gridMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPad,
            end = contentPad,
            top = topInset,
            bottom = bottomInset,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentType = { "mediaItem" },
        // Full-span header (biography + movie/TV filter chips) stays in scroll
        // flow above the poster grid. extraContent is emitted before the items.
        extraContent = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PersonHeader(
                    biography = biography,
                    filter = filter,
                    onFilterChange = { filter = it },
                    contentPad = contentPad,
                )
            }
        },
    ) { index, itemModifier ->
        val item = visibleFilmography[index]
        val visible = remember { mutableStateOf(false) }
        val clickHandler = remember(item.id) { { onItemClick(item.id) } }
        LaunchedEffect(Unit) { visible.value = true }
        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
            ) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ),
        ) {
            PosterCard(
                item = item,
                imageUrl = getImageUrl(item.id),
                onClick = clickHandler,
                modifier = itemModifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonHeader(
    biography: String?,
    filter: PersonFilmographyFilter,
    onFilterChange: (PersonFilmographyFilter) -> Unit,
    contentPad: androidx.compose.ui.unit.Dp,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!biography.isNullOrBlank()) {
            Text(
                text = biography,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = contentPad),
            )
        }
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPad),
        ) {
            PersonFilmographyFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}
@Composable
fun PersonDetailScreen(
    personId: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = (state as? PersonDetailUiState.Success)?.name ?: ""
    val isLoading = state is PersonDetailUiState.Loading

    JellyPlayScreenScaffold(
        title = title,
        onBack = onBack,
        topBarStyle = TopBarStyle.Collapsing,
        actions = {
            if (isLoading) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp)
                )
            }
        },
    ) { padding ->
        when (state) {
            is PersonDetailUiState.Error -> {
                val message = (state as PersonDetailUiState.Error).message
                ErrorScreen(
                    message = message,
                    onRetry = { viewModel.loadPerson(personId) },
                    modifier = Modifier.padding(padding),
                )
            }
            PersonDetailUiState.Loading -> {
                DelayedLoadingScreen(modifier = Modifier.padding(padding))
            }
            is PersonDetailUiState.Success -> {
                val success = state as PersonDetailUiState.Success
                val adaptiveInfo = LocalAdaptiveInfo.current
                val isTv = LocalTvMode.current
                val contentPad = adaptiveInfo.contentPadding(isTv)
                val gridMin = adaptiveInfo.gridMinSize(isTv)
                val spacing = adaptiveInfo.itemSpacing(isTv)

                // Filter + sort are client-side over the already-loaded filmography,
                // so they don't add server round-trips. Defaults surface everything
                // newest-first, matching how most cast pages read.
                val filmography = remember(success.filmography) {
                    success.filmography.sortedByDescending { it.year ?: 0 }
                }

                PersonFilmography(
                    filmography = filmography,
                    biography = success.biography,
                    getImageUrl = viewModel::getImageUrl,
                    onItemClick = onItemClick,
                    contentPad = contentPad,
                    gridMin = gridMin,
                    spacing = spacing,
                    topInset = padding.calculateTopPadding() + 16.dp,
                    bottomInset = padding.calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv),
                )
            }
        }
    }
}
