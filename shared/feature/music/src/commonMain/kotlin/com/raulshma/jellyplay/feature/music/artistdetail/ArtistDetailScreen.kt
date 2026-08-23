package com.raulshma.jellyplay.feature.music.artistdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.asText
import com.raulshma.jellyplay.feature.music.generated.resources.music_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_creating_mix
import com.raulshma.jellyplay.feature.music.generated.resources.music_instant_mix
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.animation.lessSpringySpec
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    LaunchedEffect(viewModel.mixFirstTrackId) {
        viewModel.mixFirstTrackId?.let {
            viewModel.consumeMixEvent()
            onTrackClick(it)
        }
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isLoading && viewModel.artistName.isNotEmpty(),
        onRefresh = {
            viewModel.refreshArtist(artistId)
        },
    ) {
    when {
        viewModel.isLoading -> {
            ScreenLoadingState()
        }
        viewModel.error != null -> {
            ErrorScreen(
                message = viewModel.error!!.asText(),
                onRetry = { viewModel.loadArtist(artistId) },
            )
        }
        else -> {
            AnimatedEntrance(visible = true) {
                ArtistDetailContent(
                    artistName = viewModel.artistName,
                    artistId = artistId,
                    albums = viewModel.albums,
                    getImageUrl = { viewModel.getImageUrl(it) },
                    getBackdropUrl = { viewModel.getBackdropUrl(it) },
                    onAlbumClick = onAlbumClick,
                    onInstantMix = { viewModel.startInstantMix(artistId) },
                    isStartingMix = viewModel.isStartingMix,
                    onTrackClick = onTrackClick,
                    onBack = onBack,
                )
            }
        }
    }
    }
}

@Composable
private fun ArtistDetailContent(
    artistName: String,
    artistId: String,
    albums: List<MediaItem>,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    onAlbumClick: (String) -> Unit,
    onInstantMix: () -> Unit,
    isStartingMix: Boolean,
    onTrackClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
    val albumCardWidth = if (isExpanded) 180.dp else 150.dp
    val backgroundColor = rememberScreenBackgroundColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        MediaImage(
            url = getBackdropUrl(artistId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (adaptiveInfo.isLandscape && isExpanded) 220.dp else 300.dp),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (adaptiveInfo.isLandscape && isExpanded) 220.dp else 300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            Color.Transparent,
                            androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        CircleBgBackButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding(),
            iconColor = Color.White,
        )

        if (adaptiveInfo.isLandscape && isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 180.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .padding(horizontal = 16.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = artistName,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    InstantMixButton(
                        isStartingMix = isStartingMix,
                        onClick = onInstantMix,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (albums.isNotEmpty()) {
                        androidx.compose.material3.Text(
                            text = stringResource(Res.string.music_albums),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                TvFocusableItemRow(
                    items = albums,
                    key = { it.id },
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(top = 40.dp),
                    contentType = { _, _ -> "album" },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) { _, album, itemModifier ->
                    AlbumCard(
                        album = album,
                        imageUrl = getImageUrl(album.id),
                        onClick = { onAlbumClick(album.id) },
                        cardWidth = albumCardWidth,
                        modifier = itemModifier,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 250.dp)
                    .tvFocusRestorer(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        androidx.compose.material3.Text(
                            text = artistName,
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(12.dp))

                        InstantMixButton(
                            isStartingMix = isStartingMix,
                            onClick = onInstantMix,
                        )

                        Spacer(Modifier.height(16.dp))

                        if (albums.isNotEmpty()) {
                            androidx.compose.material3.Text(
                                text = stringResource(Res.string.music_albums),
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item {
                        TvFocusableItemRow(
                            items = albums,
                            key = { it.id },
                            contentType = { _, _ -> "album" },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) { _, album, itemModifier ->
                            AlbumCard(
                                album = album,
                                imageUrl = getImageUrl(album.id),
                                onClick = { onAlbumClick(album.id) },
                                cardWidth = albumCardWidth,
                                modifier = itemModifier,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp = 150.dp,
    modifier: Modifier = Modifier,
) {
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = lessSpringySpec(),
        label = "albumScale",
    )

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val borderModifier = when {
        isSynthwave -> Modifier.border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            ),
            shape = ShapeCache.smooth8
        )
        isSoothing -> Modifier.border(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            shape = ShapeCache.smooth8
        )
        else -> Modifier
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth8)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(borderModifier)
                .clip(ShapeCache.smooth8),
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = album.name,
                blurHash = album.blurHashes.primary,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = album.name,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
        )
        album.year?.let {
            androidx.compose.material3.Text(
                text = it.toString(),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstantMixButton(
    isStartingMix: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.04f)
    Button(
        onClick = onClick,
        enabled = !isStartingMix,
        modifier = modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12),
    ) {
        if (isStartingMix) {
            JellyPlayCircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.music_creating_mix))
        } else {
            Icon(Tabler.Outline.Sparkles, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.music_instant_mix))
        }
    }
}
