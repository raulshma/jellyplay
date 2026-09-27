package com.raulshma.jellyplay.feature.music.genres

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberPagedCollectionStatus
import com.raulshma.jellyplay.feature.music.components.PagedList
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_failed_load_tracks
import com.raulshma.jellyplay.feature.music.generated.resources.music_no_tracks_found
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Music

@Composable
fun GenreDetailScreen(
    genreName: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GenreDetailViewModel = koinViewModel(),
) {
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val headerStatus = rememberPagedCollectionStatus(tracks)

    JellyPlayScreenScaffold(
        title = genreName,
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        // The music module's one paged ladder (list variant): refresh
        // loading/error/empty decisions, the append footer and the TV
        // focus-on-launch grab (first row takes D-pad focus once data
        // arrives) — previously hand-rolled here even inside music. No
        // pull-to-refresh on this route, matching the old wiring. Declared
        // delta: the chassis's content-wins precedence keeps the stale page
        // visible during a deferred refresh where the hand-rolled ladder
        // blanked to a full-screen spinner.
        PagedList(
            items = tracks,
            itemKey = { it.id },
            contentPadding = PaddingValues(
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            rowSpacing = 2.dp,
            pullToRefresh = false,
            emptyIcon = Tabler.Outline.Music,
            emptyTitle = stringResource(Res.string.music_no_tracks_found),
            errorFallbackMessage = stringResource(Res.string.music_failed_load_tracks),
            tvInitialFocusTag = "genre_detail_init",
        ) { track ->
            // Memoize per-item so getImageUrl + the click
            // lambdas aren't rebuilt on every recomposition
            // of this visible row (matches Tracks/Search).
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
                    com.raulshma.jellyplay.core.ui.components.formatDurationMs(ticks / 10_000)
                },
                imageUrl = imageUrl,
                onClick = onClick,
                onAddToQueue = onAddToQueue,
                blurHash = track.blurHashes.primary,
            )
        }
    }
}
