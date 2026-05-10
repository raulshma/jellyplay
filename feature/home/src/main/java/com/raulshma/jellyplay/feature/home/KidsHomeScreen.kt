package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsHomeScreen(
    sections: List<HomeSection>,
    favorites: List<MediaItem>,
    isLoading: Boolean,
    error: String?,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String> = { emptyList() },
    onItemClick: (String) -> Unit,
    onRefresh: () -> Unit,
) {
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

    Scaffold(
        topBar = {
            TopAppBar(
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
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                enter = fadeIn(tween(400)) + slideInVertically(
                                    initialOffsetY = { it / 8 },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                                ),
                            ) {
                                SurpriseMeCard(
                                    item = surpriseItem,
                                    imageUrl = imageUrlBuilder(surpriseItem),
                                    fallbackUrls = fallbackImageUrlBuilder(surpriseItem),
                                    onClick = { onItemClick(surpriseItem.id) },
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
                        )
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.width(200.dp),
        ) {
            MediaImage(
                url = imageUrl,
                fallbackUrls = fallbackUrls,
                contentDescription = item.name,
                blurHash = item.blurHashes.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(24.dp)),
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
) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.id }, contentType = { "mediaItem" }) { item ->
                KidsPosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = { onItemClick(item.id) },
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )

    Column(
        modifier = Modifier
            .width(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MediaImage(
                url = imageUrl,
                fallbackUrls = fallbackUrls,
                contentDescription = item.name,
                blurHash = item.blurHashes.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp)),
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
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
