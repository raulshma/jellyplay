package com.raulshma.jellyplay.feature.music.tracks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.feature.music.collection.MusicCollectionKind
import com.raulshma.jellyplay.feature.music.components.MusicSortMenuButton
import com.raulshma.jellyplay.feature.music.components.PagedList
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.raulshma.jellyplay.feature.music.components.rememberPagedCollectionStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks

/**
 * Standalone tracks route — a thin adapter over the collection chassis:
 * [MusicCollectionKind.TRACKS] supplies the declared sort set and
 * empty/error presentation, the chassis list variant [PagedList] supplies the
 * ladder (including the TV focus-on-launch grab via [PagedList.tvInitialFocusTag]
 * — kept from the former standalone screen), and the screen keeps only its scaffold
 * chrome and the track-row factory, whose play-all / add-to-queue lambdas are
 * the tracks-only commands ([TracksViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    onItemClick: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: TracksViewModel = koinViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val kind = MusicCollectionKind.TRACKS

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_tracks),
        onBack = onBack,
        actions = {
            MusicSortMenuButton(
                selected = selectedSort,
                options = kind.sortOptions,
                onSelect = viewModel::setSort,
            )
            HeaderStatusIndicator(
                status = rememberPagedCollectionStatus(tracks),
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        PagedList(
            items = tracks,
            itemKey = { it.id },
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            emptyIcon = kind.emptyIcon,
            emptyTitle = stringResource(kind.emptyTitleRes),
            errorFallbackMessage = stringResource(kind.errorFallbackRes),
            tvInitialFocusTag = "tracks_init",
        ) { track ->
            // Memoize per-item so getImageUrl + the click lambdas aren't
            // rebuilt on every recomposition of this visible row (matches
            // Search/Library).
            val imageUrl = remember(track.id) { viewModel.getImageUrl(track.id) }
            val onClick = remember(track.id) {
                {
                    val loadedTracks = tracks.itemSnapshotList.items
                    val clickIndex = loadedTracks.indexOfFirst { it.id == track.id }
                    viewModel.playAll(loadedTracks, if (clickIndex >= 0) clickIndex else 0)
                    onItemClick(track.id)
                }
            }
            val onAddToQueue = remember(track.id) { { viewModel.addToQueue(track) } }
            TrackRow(
                name = track.name,
                artist = track.albumArtist,
                album = track.album,
                duration = track.runTimeTicks?.let { ticks ->
                    remember(ticks) {
                        formatDurationMs(ticks / 10_000)
                    }
                },
                imageUrl = imageUrl,
                onClick = onClick,
                onAddToQueue = onAddToQueue,
                blurHash = track.blurHashes.primary,
            )
        }
    }
}
