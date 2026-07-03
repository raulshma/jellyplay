package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.OfflinePersonItem
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import java.text.DateFormat
import java.util.Date

@Composable
fun OfflineDetailScreen(
    itemId: String,
    onPlayOffline: (itemId: String, mediaType: MediaType) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle(initialValue = null)
    val children by viewModel.children.collectAsStateWithLifecycle(initialValue = emptyList())
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    // Track whether the first load has resolved so we can tell "loading" apart
    // from "the item was deleted / doesn't exist".
    var loaded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(itemId) {
        viewModel.load(itemId)
        // Flip the flag as soon as the flow emits its first value (even null).
        viewModel.item.first()
        loaded = true
    }

    when {
        item != null -> OfflineDetailContent(
            item = item!!,
            children = children,
            contentPad = contentPad,
            isTv = isTv,
            windowSizeClass = adaptiveInfo.windowSizeClass,
            personImageUrl = viewModel::personImageUrl,
            onPlayOffline = onPlayOffline,
            onDelete = { viewModel.delete(onBack) },
            onBack = onBack,
        )
        !loaded -> JellyPlayScreenScaffold(title = "Loading…", onBack = onBack) { ScreenLoadingState() }
        else -> JellyPlayScreenScaffold(title = "Not found", onBack = onBack) {
            ScreenEmptyState(icon = Tabler.Outline.DeviceFloppy, title = "This download is no longer available")
        }
    }
}

@Composable
private fun OfflineDetailContent(
    item: OfflineMediaItem,
    children: List<OfflineMediaItem>,
    contentPad: androidx.compose.ui.unit.Dp,
    isTv: Boolean,
    windowSizeClass: WindowSizeClass,
    personImageUrl: (String) -> String,
    onPlayOffline: (itemId: String, mediaType: MediaType) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var overviewExpanded by remember { mutableStateOf(false) }
    val playFocusState = rememberTvFocusState()

    val backdropHeight = when {
        isTv -> AdaptiveBackdropHeight.Tv
        windowSizeClass == WindowSizeClass.Expanded -> AdaptiveBackdropHeight.Expanded
        else -> AdaptiveBackdropHeight.Portrait
    }
    val baseBackdropHeight = backdropHeight / 1.2f

    val isAudio = item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC ||
        item.mediaType == MediaType.ALBUM
    val playLabel = when {
        item.playedPercentage in 1.0..94.99 -> "Resume"
        item.isPlayed -> "Play again"
        else -> "Play"
    }
    val hasProgress = item.playedPercentage in 1.0..94.99

    JellyPlayScreenScaffold(
        title = item.name,
        onBack = onBack,
        actions = {
            val deleteFocus = rememberTvFocusState()
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .then(deleteFocus.focusModifier)
                    .tvFocusIndicator(deleteFocus, CircleShape),
            ) {
                Icon(Tabler.Outline.Trash, contentDescription = "Delete download")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = adaptiveBottom(isTv)),
            ) {
                // Backdrop hero with parallax + scrim.
                item(key = "backdrop") {
                    BackdropHero(
                        backdropUrl = item.backdropPath,
                        blurHash = item.blurHashBackdrop,
                        height = baseBackdropHeight,
                    )
                }
                item { Spacer(modifier = Modifier.height(baseBackdropHeight - 170.dp)) }

                // Title + metadata row.
                item(key = "title") {
                    Column(modifier = Modifier.padding(horizontal = contentPad)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.originalTitle?.takeIf { it.isNotBlank() && !it.equals(item.name, true) }?.let { ot ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = ot,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        item.tagline?.takeIf { it.isNotBlank() }?.let { tagline ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tagline,
                                style = MaterialTheme.typography.titleSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow(item = item)
                        if (item.genres.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            ChipRow(values = item.genres)
                        }
                        if (item.studios.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            ChipRow(values = item.studios, container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        }
                    }
                }

                // Action row: Play + Delete.
                item(key = "actions") {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = contentPad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(ShapeCache.smooth16)
                                .background(MaterialTheme.colorScheme.primary)
                                .then(playFocusState.focusModifier)
                                .tvFocusIndicator(playFocusState, ShapeCache.smooth16)
                                .clickable { onPlayOffline(item.id, item.mediaType) },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Progress fill behind the label when partially watched.
                            if (hasProgress) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                        .fillMaxWidth((item.playedPercentage / 100f).toFloat()),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Tabler.Outline.PlayerPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = playLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }

                // Overview.
                item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    item(key = "overview") {
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.padding(horizontal = contentPad)) {
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                maxLines = if (overviewExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { overviewExpanded = !overviewExpanded }) {
                                Text(if (overviewExpanded) "Show less" else "Show more")
                                Icon(
                                    Tabler.Outline.ChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                // Album tracks.
                if (isAudio && children.isNotEmpty()) {
                    item(key = "tracks") {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = contentPad),
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            children.forEachIndexed { index, track ->
                                OfflineTrackRow(
                                    track = track,
                                    index = index + 1,
                                    onClick = { onPlayOffline(track.id, track.mediaType) },
                                )
                            }
                        }
                    }
                }

                // Download info card.
                item(key = "downloadInfo") {
                    Spacer(Modifier.height(20.dp))
                    DownloadInfoCard(item = item, modifier = Modifier.padding(horizontal = contentPad))
                }

                // Cast & crew.
                if (item.cast.isNotEmpty()) {
                    item(key = "cast") {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Cast & Crew",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = contentPad),
                        )
                        Spacer(Modifier.height(16.dp))
                        TvFocusableItemRow(
                            items = item.cast,
                            key = { "offline_person_${it.id}" },
                            contentPadding = PaddingValues(horizontal = contentPad),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) { _, person, focusModifier ->
                            OfflinePersonItem(
                                person = person,
                                imageUrl = personImageUrl(person.id),
                                modifier = focusModifier,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text("Delete download") },
            text = { Text("Remove \"${item.name}\" from your device? This frees up ${item.totalSizeBytes.formatBytes()}.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackdropHero(
    backdropUrl: String?,
    blurHash: String?,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            MediaImage(
                url = backdropUrl,
                contentDescription = null,
                blurHash = blurHash,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun InfoRow(item: OfflineMediaItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        item.runTimeTicks?.let { ticks ->
            val minutes = ticks / 600_000_000
            if (minutes > 0) {
                Text("${minutes}m", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        item.officialRating?.takeIf { it.isNotBlank() }?.let { rating ->
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smooth4)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(rating, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        item.communityRating?.takeIf { it > 0 }?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Tabler.Outline.Heart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(String.format("%.1f", rating), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        item.criticRating?.takeIf { it > 0 }?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Tabler.Outline.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${rating.toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ChipRow(
    values: List<String>,
    container: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values, key = { it }) { value ->
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smooth16)
                    .background(container)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f))
            }
        }
    }
}

@Composable
private fun DownloadInfoCard(item: OfflineMediaItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Tabler.Outline.DeviceFloppy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download info", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        InfoLine(label = "File size", value = item.totalSizeBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—")
        InfoLine(label = "Downloaded", value = if (item.createdAt > 0) formatDate(item.createdAt) else "—")
        InfoLine(label = "Status", value = item.downloadStatus?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—")
        if (item.downloadedBytes > 0 && item.totalSizeBytes > 0 && item.downloadedBytes < item.totalSizeBytes) {
            InfoLine(label = "Progress", value = "${(item.downloadedBytes.toFloat() / item.totalSizeBytes * 100).toInt()}%")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OfflineTrackRow(
    track: OfflineMediaItem,
    index: Int,
    onClick: () -> Unit,
) {
    val playFocus = rememberTvFocusState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth8)
            .then(playFocus.focusModifier)
            .tvFocusIndicator(playFocus, ShapeCache.smooth8)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.runTimeTicks?.let { ticks ->
                val seconds = (ticks / 10_000_000L).toInt()
                Text("${seconds / 60}:${"%02d".format(seconds % 60)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Tabler.Outline.PlayerPlay, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

private fun adaptiveBottom(isTv: Boolean): androidx.compose.ui.unit.Dp =
    if (isTv) 80.dp else 100.dp
