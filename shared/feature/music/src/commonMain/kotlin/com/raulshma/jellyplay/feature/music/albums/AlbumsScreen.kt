package com.raulshma.jellyplay.feature.music.albums

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridCellSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.music.components.MusicSortMenuButton
import com.raulshma.jellyplay.feature.music.components.MusicCollectionPagedGrid
import com.raulshma.jellyplay.feature.music.components.rememberPagedCollectionStatus
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_albums

/**
 * Standalone albums route — a thin adapter over the collection chassis:
 * [MusicCollectionKind.ALBUMS] supplies the declared sort set (the full
 * [com.raulshma.jellyplay.feature.music.collection.MusicSortOption] entries —
 * YEAR is an albums-only axis) and empty/error presentation, [MusicCollectionPagedGrid]
 * supplies the ladder, and the screen keeps only its scaffold chrome and the
 * card factory. Grid geometry stays the standalone family's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onItemClick: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val kind = MusicCollectionKind.ALBUMS

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_albums),
        onBack = onBack,
        actions = {
            MusicSortMenuButton(
                selected = selectedSort,
                options = kind.sortOptions,
                onSelect = viewModel::setSort,
            )
            HeaderStatusIndicator(
                status = rememberPagedCollectionStatus(albums),
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        MusicCollectionPagedGrid(
            items = albums,
            itemKey = { it.id },
            kind = kind,
            columns = GridCells.Adaptive(adaptiveInfo.gridCellSize(isTv)),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                top = 8.dp,
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            spacing = adaptiveInfo.itemSpacing(isTv),
        ) { album, itemModifier ->
            AlbumCard(
                name = album.name,
                artist = album.albumArtist,
                year = album.year,
                imageUrl = viewModel.getImageUrl(album.id),
                onClick = { onItemClick(album.id) },
                modifier = itemModifier,
                blurHash = album.blurHashes.primary,
            )
        }
    }
}
