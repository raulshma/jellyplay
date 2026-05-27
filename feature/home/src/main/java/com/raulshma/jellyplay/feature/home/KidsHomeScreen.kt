package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.FastInvokeEasing
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KidsHomeScreen(
    sections: List<HomeSection>,
    favorites: List<MediaItem>,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    error: String?,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String> = { emptyList() },
    onItemClick: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)

    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var showSurprise by remember { mutableStateOf(false) }
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current
        .collectAsStateWithLifecycle()
    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    val allItems = remember(sections) { sections.flatMap { it.items } }
    val genres = remember(allItems) {
        allItems.flatMap { it.genres }.distinct().sorted()
    }
    val filteredSections = remember(sections, selectedGenre) {
        if (selectedGenre == null) sections
        else sections.map { section ->
            section.copy(items = section.items.filter { selectedGenre in it.genres })
        }.filter { it.items.isNotEmpty() }
    }

    val surpriseItem = remember(allItems, showSurprise) {
        if (allItems.isNotEmpty() && showSurprise) allItems.random() else null
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ChildCare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text("Kids Corner")
                        com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                            status = headerStatus,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSurprise = !showSurprise }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Surprise Me",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            error != null && sections.isEmpty() -> {
                ErrorScreen(
                    message = error,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    if (genres.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = contentPad),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                items(genres, key = { it }, contentType = { "genre" }) { genre ->
                                    FilterChip(
                                        selected = genre == selectedGenre,
                                        onClick = {
                                            selectedGenre = if (selectedGenre == genre) null else genre
                                        },
                                        label = { Text(genre) },
                                    )
                                }
                            }
                        }
                    }

                    if (favorites.isNotEmpty()) {
                        item {
                            KidsSection(
                                title = "My Favorites",
                                items = favorites,
                                imageUrlBuilder = imageUrlBuilder,
                                fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                onItemClick = onItemClick,
                            )
                        }
                    }

                    if (surpriseItem != null) {
                        item {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(400, easing = AlphaEasing)) + slideInVertically(
                                    initialOffsetY = { it / 8 },
                                    animationSpec = tween(400, easing = FancyTransitionEasing),
                                ),
                            ) {
                                SurpriseMeCard(
                                    item = surpriseItem,
                                    imageUrl = imageUrlBuilder(surpriseItem),
                                    fallbackUrls = fallbackImageUrlBuilder(surpriseItem),
                                    onClick = { onItemClick(surpriseItem.id) },
                                    contentPadding = contentPad,
                                )
                            }
                        }
                    }

                    items(filteredSections, key = { it.title }, contentType = { "kidsSection" }) { section ->
                        KidsSection(
                            title = section.title,
                            items = section.items,
                            imageUrlBuilder = imageUrlBuilder,
                            fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                            onItemClick = onItemClick,
                            contentPadding = contentPad,
                            spacing = spacing,
                            cardWidth = cardWidth,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun SurpriseMeCard(
    item: MediaItem,
    imageUrl: String,
    fallbackUrls: List<String> = emptyList(),
    onClick: () -> Unit,
    contentPadding: Dp = 16.dp,
) {
    val tvFocusState = rememberTvFocusState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPadding, vertical = 12.dp)
            .then(tvFocusState.focusModifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = "Surprise Me!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = ShapeCache.smooth24,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .width(200.dp)
                .tvFocusIndicator(tvFocusState, ShapeCache.smooth24),
        ) {
            MediaImage(
                url = imageUrl,
                fallbackUrls = fallbackUrls,
                contentDescription = item.name,
                blurHash = item.blurHashes.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(ShapeCache.smooth24),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KidsSection(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String> = { emptyList() },
    onItemClick: (String) -> Unit,
    contentPadding: Dp = 16.dp,
    spacing: Dp = 16.dp,
    cardWidth: Dp = 160.dp,
) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = contentPadding, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = { it.id }, contentType = { "mediaItem" }) { item ->
                KidsPosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = { onItemClick(item.id) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun KidsPosterCard(
    item: MediaItem,
    imageUrl: String,
    fallbackUrls: List<String> = emptyList(),
    onClick: () -> Unit,
    cardWidth: Dp = 160.dp,
) {
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150, easing = FastInvokeEasing),
        label = "cardScale",
    )

    Column(
        modifier = Modifier
            .width(cardWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFocusState.focusModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .tvFocusIndicator(tvFocusState, ShapeCache.smooth16)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = ShapeCache.smooth16,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            MediaImage(
                url = imageUrl,
                fallbackUrls = fallbackUrls,
                contentDescription = item.name,
                blurHash = item.blurHashes.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(ShapeCache.smooth16),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun IconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tvFocusState = rememberTvFocusState()
    Box(
        modifier = Modifier
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smoothPill)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
