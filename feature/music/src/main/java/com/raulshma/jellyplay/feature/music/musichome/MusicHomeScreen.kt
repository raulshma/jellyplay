package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.animation.AnimatedContent
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.sp
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
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.music.components.AlbumCard
import com.raulshma.jellyplay.feature.music.components.ArtistCard
import com.raulshma.jellyplay.feature.music.components.BlobArtCollage
import com.raulshma.jellyplay.feature.music.components.GraphicEqVisualizer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlinx.coroutines.launch

@Composable
fun MusicHomeScreen(
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    onItemClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
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

    val featuredImageUrl = remember(sections) {
        val allItems = sections.flatMap { it.items }
        val firstItem = allItems.firstOrNull { it.mediaType == MediaType.ALBUM || it.mediaType == MediaType.AUDIO }
        firstItem?.id?.let { viewModel.getImageUrl(it) }
    }

    ArtworkThemeWrapper(imageUrl = featuredImageUrl) {
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
        val headerHeight = 380.dp
        val density = LocalDensity.current
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val headerHeightPx = with(density) { headerHeight.toPx() }
        
        val transitionRange = 140.dp
        val transitionRangePx = with(density) { transitionRange.toPx() }
        val scrollFraction by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset.toFloat() / transitionRangePx).coerceIn(0f, 1f)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Ambient Fluid Mesh Gradient Backdrop
            AmbientMeshGradient(imageUrl = featuredImageUrl)

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

                            itemsIndexed(sections, key = { _, section -> section.title }, contentType = { _, _ -> "musicHomeSection" }) { index, section ->
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
                                        onItemClick = { item ->
                                            if (item.mediaType == MediaType.ALBUM) {
                                                onAlbumClick(item.id)
                                            } else {
                                                onItemClick(item.id)
                                            }
                                        },
                                        modifier = Modifier.padding(top = if (index == 0) 8.dp else 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val borderAlpha = 0.12f * scrollFraction
            val dockScale = 1f - (0.04f * scrollFraction)

            var isFabExpanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = (16f * scrollFraction).dp,
                        vertical = (8f * scrollFraction).dp
                    )
                    .graphicsLayer {
                        scaleX = dockScale
                        scaleY = dockScale
                    }
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadius = (28f * scrollFraction).dp,
                            smoothnessAsPercent = 60
                        )
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f * scrollFraction)
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = borderAlpha * 2f),
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = borderAlpha * 0.5f),
                                )
                            )
                        ),
                        AbsoluteSmoothCornerShape(
                            cornerRadius = (28f * scrollFraction).dp,
                            smoothnessAsPercent = 60
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeSwitch(
                        currentMode = viewModel.homeMode,
                        onModeChange = onModeChange,
                    )
                    HeaderStatusIndicator(
                        status = headerStatus,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    )
                }
            }

            FloatingActionButtonMenu(
                expanded = isFabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = isFabExpanded,
                        onCheckedChange = { isFabExpanded = it },
                        containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                            initialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            finalColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Icon(
                            if (isFabExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                            contentDescription = if (isFabExpanded) "Close menu" else "More options",
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        viewModel.surpriseMe { trackId ->
                            onItemClick(trackId)
                        }
                    },
                    text = { Text("Surprise Me") },
                    icon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        onSyncPlayClick()
                    },
                    text = { Text("SyncPlay") },
                    icon = {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        onDownloadsClick()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Downloads")
                            if (activeDownloadCount > 0) {
                                Badge(
                                    modifier = Modifier.padding(start = 6.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        activeDownloadCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        onSettingsClick()
                    },
                    text = { Text("Settings") },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AmbientMeshGradient(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val artworkColors = LocalArtworkColors.current
    val color1 = artworkColors?.vibrant ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val color2 = artworkColors?.lightVibrant ?: MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    val color3 = artworkColors?.accentColor ?: MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)

    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val animOffset1 by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = FancyTransitionEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    val animOffset2 by infiniteTransition.animateFloat(
        initialValue = 250f,
        targetValue = -120f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = FancyTransitionEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        (artworkColors?.tintedBackground ?: MaterialTheme.colorScheme.background).copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                drawCircle(
                    color = color1.copy(alpha = 0.22f),
                    radius = width * 0.75f,
                    center = Offset(width * 0.15f + animOffset1, height * 0.12f + animOffset2 * 0.2f)
                )
                drawCircle(
                    color = color2.copy(alpha = 0.18f),
                    radius = width * 0.65f,
                    center = Offset(width * 0.85f - animOffset2, height * 0.32f + animOffset1 * 0.15f)
                )
                drawCircle(
                    color = color3.copy(alpha = 0.15f),
                    radius = width * 0.55f,
                    center = Offset(width * 0.5f + animOffset1 * 0.4f, height * 0.52f - animOffset2 * 0.3f)
                )
            }
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
    val featuredItem = remember(allItems) { allItems.firstOrNull() }
    val artworkUrls = remember(allItems) {
        allItems.take(3).map { item ->
            item.parentId?.let { backdropUrlBuilder(it) } ?: imageUrlBuilder(item.id)
        }
    }

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Late Night Beats"
        }
    }

    val subtitle = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Ready for a fresh start?"
            in 12..16 -> "Let's keep the vibe alive"
            in 17..21 -> "Time to wind down"
            else -> "Late night chill mode"
        }
    }

    val artworkColors = LocalArtworkColors.current
    val startColor = artworkColors?.vibrant ?: MaterialTheme.colorScheme.primary
    val endColor = artworkColors?.accentColor ?: MaterialTheme.colorScheme.tertiary
    val titleBrush = remember(startColor, endColor) {
        Brush.linearGradient(colors = listOf(startColor, endColor))
    }

    val wavePhase by rememberInfiniteTransition(label = "wave_phase").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = scrollOffset * 0.25f }
            .padding(top = 76.dp, bottom = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = 0.2f }
            ) {
                val width = size.width
                val height = size.height
                val path = Path()
                path.moveTo(0f, height * 0.5f)
                for (x in 0..width.toInt() step 5) {
                    val xRad = (x.toFloat() / width) * 2f * Math.PI.toFloat() * 1.5f + wavePhase
                    val y = height * 0.5f + Math.sin(xRad.toDouble()).toFloat() * 14.dp.toPx()
                    path.lineTo(x.toFloat(), y)
                }
                path.lineTo(width, height)
                path.lineTo(0f, height)
                path.close()
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        colors = listOf(startColor.copy(alpha = 0.45f), Color.Transparent),
                        startY = height * 0.1f,
                        endY = height
                    )
                )
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2.5).sp,
                        brush = titleBrush
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (featuredItem != null) {
            val breathTransition = rememberInfiniteTransition(label = "blob_breath")
            val breathScale by breathTransition.animateFloat(
                initialValue = 0.97f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2800, easing = FancyTransitionEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breath"
            )

            val playSpinAngle by rememberInfiniteTransition(label = "vinyl_spin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spin"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(ShapeCache.smooth28)
                    .clickable { onClick(featuredItem.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.48f)
                ),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                    ShapeCache.smoothPill
                                )
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    ShapeCache.smoothPill
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "RECOMMENDED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Your Daily Mix",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Based on your recent listening",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .graphicsLayer { rotationZ = playSpinAngle },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play Mix",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = -playSpinAngle }
                                )
                            }

                            GraphicEqVisualizer(
                                color = MaterialTheme.colorScheme.primary,
                                barCount = 4,
                                barWidth = 3.dp,
                                maxBarHeight = 16.dp,
                                spacing = 3.dp
                            )
                        }
                    }

                    if (artworkUrls.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .scale(breathScale)
                        ) {
                            BlobArtCollage(
                                imageUrls = artworkUrls,
                                modifier = Modifier.size(width = 160.dp, height = 120.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveCategoryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cat_btn_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            containerColor,
                            containerColor.copy(alpha = 0.8f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        CategoryItem("Artists", Icons.Default.Group, onArtistsClick, ShapeCache.smooth24, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
        CategoryItem("Albums", Icons.Default.Album, onAlbumsClick, ShapeCache.smooth16, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
        CategoryItem("Tracks", Icons.Default.MusicNote, onTracksClick, ShapeCache.smooth32, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
        CategoryItem("Genres", Icons.Default.GraphicEq, onGenresClick, ShapeCache.smooth20, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant),
        CategoryItem("Playlists", Icons.Default.QueueMusic, onPlaylistsClick, ShapeCache.smooth28, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        itemsIndexed(items) { index, item ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 60L)
                visible = true
            }
            val slideOffset by animateFloatAsState(
                targetValue = if (visible) 0f else 60f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "stagger"
            )
            val alphaState by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(300),
                label = "stagger_alpha"
            )
            ExpressiveCategoryButton(
                label = item.label,
                icon = item.icon,
                onClick = item.onClick,
                shape = item.shape,
                containerColor = item.containerColor,
                contentColor = item.contentColor,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = slideOffset
                        alpha = alphaState
                    }
            )
        }
    }
}

private data class CategoryItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val shape: androidx.compose.ui.graphics.Shape,
    val containerColor: Color,
    val contentColor: Color
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MusicSectionRow(
    section: MusicHomeSection,
    imageUrlBuilder: (MediaItem) -> String,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { section.items.size },
            itemWidth = when (section.items.firstOrNull()?.mediaType) {
                MediaType.ARTIST -> 120.dp
                MediaType.AUDIO -> 280.dp
                else -> 160.dp
            },
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { index ->
            val item = section.items[index]
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

@Composable
private fun TrackItem(
    item: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(280.dp)
            .tvFocusable().clickable(onClick = onClick)
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
                .background(MaterialTheme.colorScheme.surfaceVariant, ShapeCache.smooth4),
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

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "expressive_btn_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
