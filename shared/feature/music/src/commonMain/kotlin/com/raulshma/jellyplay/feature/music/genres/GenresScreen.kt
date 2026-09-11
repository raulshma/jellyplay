package com.raulshma.jellyplay.feature.music.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import com.raulshma.jellyplay.feature.music.components.GenreChip
import com.raulshma.jellyplay.feature.music.components.SimpleCollectionGrid
import com.raulshma.jellyplay.feature.music.components.rememberSimpleCollectionStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_genres

/**
 * Standalone genres route — a thin adapter over the collection chassis:
 * [SimpleCollectionGrid] (the list-sourced ladder — genres are not paged)
 * supplies the refresh ladder, pull-to-refresh and empty state; the screen
 * keeps only its scaffold chrome and the chip factory, whose click carries
 * the genres-only drill-down payload (id + display name for
 * [GenreDetailScreen]). Grid geometry stays the standalone family's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    onItemClick: (id: String, name: String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: GenresViewModel = koinViewModel(),
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val kind = MusicCollectionKind.GENRES

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_genres),
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = rememberSimpleCollectionStatus(
                    isLoading = isLoading,
                    hasError = error != null,
                ),
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        SimpleCollectionGrid(
            items = genres,
            itemKey = { it.id },
            isLoading = isLoading,
            error = error,
            errorFallbackMessage = stringResource(kind.errorFallbackRes),
            onRefresh = viewModel::refresh,
            columns = GridCells.Adaptive(adaptiveInfo.gridMinSize(isTv)),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                top = 8.dp,
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
            emptyIcon = kind.emptyIcon,
            emptyTitle = stringResource(kind.emptyTitleRes),
        ) { _, genre, itemModifier ->
            GenreChip(
                name = genre.name,
                onClick = { onItemClick(genre.id, genre.name) },
                modifier = itemModifier,
            )
        }
    }
}
