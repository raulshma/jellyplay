package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicHomeScreen(
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    onItemClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
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
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()

    val headerStatus = resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            listState.firstVisibleItemScrollOffset.toFloat() +
                    (listState.firstVisibleItemIndex * 1000f)
        }
    }
    val headerHeight = 320.dp
    val density = LocalDensity.current
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val scrollFraction by remember {
        derivedStateOf {
            (scrollOffset / headerHeightPx).coerceIn(0f, 1f)
        }
    }

    val appBarAlpha by animateFloatAsState(
        targetValue = if (scrollFraction > 0.8f) 1f else 0f,
        animationSpec = tween(300), label = "appBarAlpha",
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
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
                    val adaptiveInfo = LocalAdaptiveInfo.current
                    val isTv = isTvDevice()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = adaptiveInfo.bottomPadding(isTv)),
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

                        itemsIndexed(sections, contentType = { _, _ -> "musicHomeSection" }) { index, section ->
                            val visible = remember { mutableStateOf(false) }
                            androidx.compose.runtime.LaunchedEffect(Unit) { visible.value = true }
                            AnimatedVisibility(
                                visible = visible.value,
                                enter = fadeIn(
                                    animationSpec = tween(350, delayMillis = index * 80)
                                ) + slideInVertically(
                                    initialOffsetY = { it / 12 },
                                    animationSpec = tween(350, delayMillis = index * 80, easing = FastOutSlowInEasing),
                                ),
                            ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        ModeSwitch(
                            currentMode = viewModel.homeMode,
                            onModeChange = onModeChange,
                        )
                        HeaderStatusIndicator(
                            status = headerStatus,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
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
                    BadgedBox(
                        badge = {
                            if (activeDownloadCount > 0) {
                                Badge {
                                    Text(activeDownloadCount.toString())
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onDownloadsClick) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloads",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
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
                modifier = Modifier.statusBarsPadding(),
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
    height: Dp,
) {
    val allItems = remember(sections) { sections.flatMap { it.items } }
    val currentIndex = remember { mutableStateOf(0) }
    val featuredItem = remember(allItems, currentIndex.value) {
        allItems.filter { it.mediaType == MediaType.ALBUM || it.mediaType == MediaType.AUDIO || it.mediaType == MediaType.ARTIST }
            .let { filtered -> filtered.getOrNull(currentIndex.value % filtered.size.coerceAtLeast(1)) }
            ?: allItems.firstOrNull()
    }

    AnimatedContent(
        targetState = featuredItem?.id,
        transitionSpec = {
            (fadeIn(tween(500)) togetherWith fadeOut(tween(300)))
        },
        label = "heroRotation",
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { translationY = scrollOffset * 0.5f },
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    featuredItem?.let {
                        Modifier.clickable { onClick(it.id) }
                    } ?: Modifier
                ),
        ) {
            featuredItem?.let { item ->
                MediaImage(
                    url = item.parentId?.let { backdropUrlBuilder(it) }
                        ?: imageUrlBuilder(item.id),
                    contentDescription = item.name,
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

            featuredItem?.let { item ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Text(
                        text = item.name,
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
                        item.albumArtist?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        item.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onClick(item.id) },
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
        items(items, contentType = { "quickAccess" }) { (label, icon, onClick) ->
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
            items(section.items, key = { "${section.title}_${it.id}" }, contentType = { "mediaItem" }) { item ->
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
