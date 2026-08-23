package com.raulshma.jellyplay.feature.music.smartplaylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.SmartPlaylist
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_smart_playlist_favorites
import com.raulshma.jellyplay.feature.music.generated.resources.music_smart_playlist_recently_added
import com.raulshma.jellyplay.feature.music.generated.resources.music_smart_playlist_top_rated
import com.raulshma.jellyplay.feature.music.generated.resources.music_smart_playlist_unplayed
import com.raulshma.jellyplay.feature.music.generated.resources.music_smart_playlists
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun smartPlaylistDisplayName(playlist: com.raulshma.jellyplay.core.model.SmartPlaylist): String {
    return when (playlist.id) {
        "top_rated" -> stringResource(Res.string.music_smart_playlist_top_rated)
        "recently_added" -> stringResource(Res.string.music_smart_playlist_recently_added)
        "unplayed" -> stringResource(Res.string.music_smart_playlist_unplayed)
        "favorites" -> stringResource(Res.string.music_smart_playlist_favorites)
        else -> playlist.name
    }
}

@Composable
fun SmartPlaylistsScreen(
    onPlaylistClick: (SmartPlaylist) -> Unit,
    onBack: () -> Unit,
    viewModel: SmartPlaylistsViewModel = koinViewModel(),
) {
    val playlists = viewModel.playlists
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    // TV focus-on-launch: focus the first smart playlist once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = playlists.size,
        tag = "smart_playlists_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_smart_playlists),
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { innerPadding ->
        Spacer(Modifier.height(8.dp))

        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer()
                .focusRequester(listFocusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                top = 8.dp,
                bottom = adaptiveInfo.bottomPadding(isTv) + innerPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
        ) {
            items(playlists.size, key = { playlists[it].id }, contentType = { "smartPlaylist" }) { index ->
                val playlist = playlists[index]
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: SmartPlaylist,
    onClick: () -> Unit,
) {
    val icon = when (playlist.id) {
        "top_rated" -> Tabler.Outline.Star
        "recently_added" -> Tabler.Outline.Certificate
        "unplayed" -> Tabler.Outline.History
        "favorites" -> Tabler.Outline.Heart
        else -> Tabler.Outline.Wand
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusIndicator(ShapeCache.smooth16)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = smartPlaylistDisplayName(playlist),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (playlist.criteria.isNotEmpty()) {
                    Text(
                        text = playlist.criteria.joinToString { it.type.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
