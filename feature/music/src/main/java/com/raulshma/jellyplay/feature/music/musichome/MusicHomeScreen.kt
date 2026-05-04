package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicHomeScreen(
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    onItemClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onTracksClick: () -> Unit,
    onGenresClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    viewModel: MusicHomeViewModel = hiltViewModel(),
) {
    val sections = viewModel.sections
    val isLoading = viewModel.isLoading
    val error = viewModel.error

    val listState = rememberLazyListState()
    val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat() +
            (listState.firstVisibleItemIndex * 1000f)
    val headerHeight = 320.dp
    val density = LocalDensity.current
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val scrollFraction = (scrollOffset / headerHeightPx).coerceIn(0f, 1f)

    val appBarAlpha by animateFloatAsState(
        targetValue = if (scrollFraction > 0.8f) 1f else 0f,
        animationSpec = tween(300), label = "appBarAlpha",
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            isLoading && sections.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null && sections.isEmpty() -> {
                ErrorScreen(message = error, onRetry = { viewModel.loadSections() })
            }
            else -> {
                if (sections.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No music available. Check your Jellyfin libraries.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp),
                    ) {
                        item {
                            MusicHeroHeader(
                                sections = sections,
                                backdropUrlBuilder = { viewModel.getBackdropUrl(it) },
                                imageUrlBuilder = { viewModel.getImageUrl(it) },
                                onClick = onItemClick,
                                scrollOffset = scrollOffset,
                                height = headerHeight,
                            )
                        }

                        item {
                            QuickAccessRow(
                                onArtistsClick = onArtistsClick,
                                onAlbumsClick = onAlbumsClick,
                                onTracksClick = onTracksClick,
                                onGenresClick = onGenresClick,
                                onPlaylistsClick = onPlaylistsClick,
                            )
                        }

                        items(sections.size) { index ->
                            val section = sections[index]
                            MusicSectionRow(
                                section = section,
                                imageUrlBuilder = { viewModel.getImageUrl(it.id) },
                                onItemClick = { onItemClick(it.id) },
                                modifier = Modifier.padding(top = if (index == 0) 8.dp else 16.dp),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = appBarAlpha))
        ) {
            TopAppBar(
                title = {
                    Text(
                        "JellyPlay",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = appBarAlpha),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    ModeSwitch(
                        currentMode = homeMode,
                        onModeChange = onModeChange,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                },
                actions = {
                    IconButton(onClick = { /* surprise me */ }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Surprise Me",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = onSyncPlayClick) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = "SyncPlay",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()),
            )
        }
    }
}

@Composable
private fun MusicHeroHeader(
    sections: List<MusicHomeSection>,
    backdropUrlBuilder: (String) -> String,
    imageUrlBuilder: (String) -> String,
    onClick: (String) -> Unit,
    scrollOffset: Float,
    height: androidx.compose.ui.unit.Dp,
) {
    val featuredItem = remember(sections) {
        sections.flatMap { it.items }.firstOrNull { it.mediaType == MediaType.ALBUM }
            ?: sections.firstOrNull()?.items?.firstOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(
                featuredItem?.let {
                    Modifier.clickable { onClick(it.id) }
                } ?: Modifier
            )
            .graphicsLayer { translationY = scrollOffset * 0.5f }
    ) {
        if (featuredItem != null) {
            MediaImage(
                url = featuredItem.parentId?.let { backdropUrlBuilder(it) }
                    ?: imageUrlBuilder(featuredItem.id),
                contentDescription = featuredItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background,
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )
                )
        )

        if (featuredItem != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = featuredItem.name,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    featuredItem.albumArtist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    featuredItem.year?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onClick(featuredItem.id) },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun QuickAccessRow(
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onTracksClick: () -> Unit,
    onGenresClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
) {
    val items = listOf(
        Triple("Artists", Icons.Default.Group, onArtistsClick),
        Triple("Albums", Icons.Default.Album, onAlbumsClick),
        Triple("Tracks", Icons.Default.MusicNote, onTracksClick),
        Triple("Genres", Icons.Default.GraphicEq, onGenresClick),
        Triple("Playlists", Icons.Default.QueueMusic, onPlaylistsClick),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        items(items) { (label, icon, onClick) ->
            AssistChip(
                onClick = onClick,
                label = { Text(label) },
                leadingIcon = {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun MusicSectionRow(
    section: MusicHomeSection,
    imageUrlBuilder: (MediaItem) -> String,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(section.items, key = { "${section.title}_${it.id}" }) { item ->
                when (section.items.firstOrNull()?.mediaType) {
                    MediaType.ARTIST -> {
                        ArtistCard(
                            name = item.name,
                            imageUrl = imageUrlBuilder(item),
                            onClick = { onItemClick(item) },
                            blurHash = item.blurHashes.primary,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    MediaType.AUDIO -> {
                        TrackItem(
                            item = item,
                            imageUrl = imageUrlBuilder(item),
                            onClick = { onItemClick(item) },
                        )
                    }
                    else -> {
                        AlbumCard(
                            name = item.name,
                            artist = item.albumArtist,
                            year = item.year,
                            imageUrl = imageUrlBuilder(item),
                            onClick = { onItemClick(item) },
                            blurHash = item.blurHashes.primary,
                            modifier = Modifier.width(160.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    item: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = item.name,
            blurHash = item.blurHashes.primary,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.albumArtist, item.album).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
