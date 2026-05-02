package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.image.MediaImage

@Composable
fun MediaDetailScreen(
    itemId: String,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    val detail by viewModel.detail
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.loadItem(itemId) }) {
                        Text("Retry")
                    }
                }
            }
        }
        detail != null -> {
            DetailContent(
                detail = detail!!,
                getImageUrl = { viewModel.getImageUrl(it) },
                getBackdropUrl = { viewModel.getBackdropUrl(it) },
                onPlayClick = { sourceId, start -> onPlayClick(itemId, sourceId, start) },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onMarkPlayed = { viewModel.markPlayed() },
                onMarkUnplayed = { viewModel.markUnplayed() },
                onItemClick = onItemClick,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetail,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    onPlayClick: (mediaSourceId: String?, startPosition: Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val item = detail.item
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        MediaImage(
            url = getBackdropUrl(item.id),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.statusBarsPadding(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 250.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.runTimeTicks?.let { ticks ->
                            val minutes = ticks / 600_000_000
                            Text(
                                text = "${minutes}min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.communityRating?.let { rating ->
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.officialRating?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (item.isPlayed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val sourceId = detail.mediaSources.firstOrNull()?.id
                        val startPos = item.playbackPositionTicks ?: 0L
                        onPlayClick(sourceId, startPos)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0)
                            "Resume" else "Play"
                    )
                }
                OutlinedButton(
                    onClick = { if (item.isPlayed) onMarkUnplayed() else onMarkPlayed() },
                ) {
                    Icon(
                        if (item.isPlayed) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            item.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                Text(
                    text = genres.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            item.overview?.let { overview ->
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
            }

            if (detail.people.isNotEmpty()) {
                Text(
                    text = "Cast & Crew",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(detail.people, key = { it.id }) { person ->
                        PersonItem(
                            person = person,
                            imageUrl = getImageUrl(person.id),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (detail.relatedItems.isNotEmpty()) {
                Text(
                    text = "More Like This",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(detail.relatedItems, key = { it.id }) { related ->
                        PosterCard(
                            item = related,
                            imageUrl = getImageUrl(related.id),
                            onClick = { onItemClick(related.id) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PersonItem(
    person: PersonInfo,
    imageUrl: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp),
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = person.name,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        person.role?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
