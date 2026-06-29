package com.raulshma.jellyplay.feature.music.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    onItemClick: (id: String, name: String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: GenresViewModel = hiltViewModel(),
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    JellyPlayScreenScaffold(
        title = "Genres",
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        PullToRefreshBox(
            isRefreshing = isLoading && genres.isNotEmpty(),
            onRefresh = {
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        ScreenLoadingState()
                    }
                    error != null -> {
                        ErrorScreen(
                            message = error!!,
                            onRetry = { viewModel.refresh() },
                        )
                    }
                    genres.isEmpty() -> {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Music,
                            title = "No genres found",
                        )
                    }
                    else -> {
                        val adaptiveInfo = LocalAdaptiveInfo.current
                        val isTv = LocalTvMode.current
                        TvFocusableGrid(
                            items = genres,
                            key = { it.id },
                            columns = GridCells.Adaptive(adaptiveInfo.gridMinSize(isTv)),
                            contentPadding = PaddingValues(
                                start = adaptiveInfo.contentPadding(isTv),
                                end = adaptiveInfo.contentPadding(isTv),
                                top = 8.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            modifier = Modifier.fillMaxSize(),
                            contentType = { "genre" },
                        ) { _, genre, itemModifier ->
                            GenreChip(
                                name = genre.name,
                                onClick = { onItemClick(genre.id, genre.name) },
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
