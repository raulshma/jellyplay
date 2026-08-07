package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.BackdropLayer
import com.raulshma.jellyplay.core.ui.components.ChipRow
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.OfflinePersonItem
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.components.TransparentTopBar
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.rememberBackdropScrollState
import com.raulshma.jellyplay.core.ui.components.formatRelativeTime
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

@Composable
fun OfflineDetailScreen(
    itemId: String,
    onPlayOffline: (itemId: String, mediaType: MediaType) -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
    onNavigateToDetail: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle(initialValue = null)
    val children by viewModel.children.collectAsStateWithLifecycle(initialValue = emptyList())
    val seasons by viewModel.seasons.collectAsStateWithLifecycle(initialValue = emptyList())
    val episodes by viewModel.episodes.collectAsStateWithLifecycle(initialValue = emptyMap())
    val compactEpisodeList by viewModel.compactEpisodeList.collectAsStateWithLifecycle(initialValue = false)
    val syncState by viewModel.syncState.collectAsStateWithLifecycle(initialValue = null)
    val resyncState by viewModel.resyncState.collectAsStateWithLifecycle(initialValue = ResyncUiState.Idle)
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    // Track whether the first load has resolved so we can tell "loading" apart
    // from "the item was deleted / doesn't exist".
    var loaded by remember { mutableStateOf(false) }
    var showSyncSheet by remember { mutableStateOf(false) }
    LaunchedEffect(itemId) {
        viewModel.load(itemId)
        viewModel.item.first()
        loaded = true
        // TTL-gated freshness check on entry — the manager no-ops network-wise
        // when within the per-item TTL or offline, so this is cheap to fire on
        // every visit.
        viewModel.checkForUpdates()
    }

    when {
        item != null -> OfflineDetailContent(
            item = item!!,
            children = children,
            seasons = seasons,
            episodes = episodes,
            contentPad = contentPad,
            isTv = isTv,
            windowSizeClass = adaptiveInfo.windowSizeClass,
            personImageUrl = viewModel::personImageUrl,
            onPlayOffline = onPlayOffline,
            onNavigateToSeries = onNavigateToSeries,
            onNavigateToDetail = onNavigateToDetail,
            onEpisodeDelete = { viewModel.deleteEpisode(it) },
            onMarkSeasonPlayed = { viewModel.markSeasonPlayed(it) },
            onMarkSeasonUnplayed = { viewModel.markSeasonUnplayed(it) },
            onDelete = { viewModel.delete(onBack) },
            onBack = onBack,
            compactEpisodeList = compactEpisodeList,
            onCompactEpisodeListChange = viewModel::setCompactEpisodeList,
            syncState = syncState,
            resyncState = resyncState,
            onShowSyncSheet = { showSyncSheet = true },
        )
        !loaded -> JellyPlayScreenScaffold(title = stringResource(R.string.downloads_loading), onBack = onBack) { ScreenLoadingState() }
        else -> JellyPlayScreenScaffold(title = stringResource(R.string.downloads_not_found), onBack = onBack) {
            ScreenEmptyState(icon = Tabler.Outline.DeviceFloppy, title = stringResource(R.string.downloads_download_unavailable))
        }
    }

    if (showSyncSheet) {
        OfflineResyncSheet(
            syncState = syncState,
            resyncState = resyncState,
            onResync = viewModel::resync,
            onRedownloadMedia = viewModel::redownloadMedia,
            onDismiss = {
                showSyncSheet = false
                viewModel.clearResyncState()
            },
        )
    }
}

@Composable
private fun OfflineDetailContent(
    item: OfflineMediaItem,
    children: List<OfflineMediaItem>,
    seasons: List<OfflineMediaItem>,
    episodes: Map<String, List<OfflineMediaItem>>,
    contentPad: androidx.compose.ui.unit.Dp,
    isTv: Boolean,
    windowSizeClass: WindowSizeClass,
    personImageUrl: (String) -> String,
    onPlayOffline: (itemId: String, mediaType: MediaType) -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
    onNavigateToDetail: (itemId: String) -> Unit,
    onEpisodeDelete: (episodeId: String) -> Unit,
    onMarkSeasonPlayed: (seasonId: String) -> Unit,
    onMarkSeasonUnplayed: (seasonId: String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    compactEpisodeList: Boolean = false,
    onCompactEpisodeListChange: (Boolean) -> Unit = {},
    syncState: com.raulshma.jellyplay.core.model.OfflineSyncState? = null,
    resyncState: ResyncUiState = ResyncUiState.Idle,
    onShowSyncSheet: () -> Unit = {},
) {
    val isEpisode = item.mediaType == MediaType.EPISODE
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
        item.playedPercentage in 1.0..94.99 -> stringResource(R.string.downloads_play_resume)
        item.isPlayed -> stringResource(R.string.downloads_play_again)
        else -> stringResource(R.string.downloads_action_play)
    }
    val hasProgress = item.playedPercentage in 1.0..94.99

    // Parallax: track the scroll of the LazyColumn and translate/fade the
    // backdrop layer with it, mirroring the online detail screen. The first
    // item is the backdrop spacer (height baseBackdropHeight - 150dp), so the
    // raw scroll offset maps directly to how far the backdrop has scrolled.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollState = rememberBackdropScrollState(listState, backdropHeight)
    val scrollOffset = scrollState.scrollOffset
    val scrollFraction = scrollState.scrollFraction
    val scrollCollapsed = scrollState.scrollCollapsed
    val animatedContainerColor = scrollState.containerColor
    val animatedTitleAlpha = scrollState.titleAlpha

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Backdrop layer (parallax: translates up at half scroll speed
        // and fades, exactly like the online detail screen) ──
        BackdropLayer(
            backdropUrl = item.backdropPath,
            blurHash = item.blurHashBackdrop,
            height = backdropHeight,
            scrollTranslationY = -scrollOffset * 0.5f,
            scrollAlpha = 1f - (scrollFraction * 0.8f),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Spacer so content starts over the backdrop, matching the
            // online detail screen's overlap.
            item { Spacer(modifier = Modifier.height(baseBackdropHeight - 150.dp)) }

            // Poster overlapping the backdrop (phone layout, matching online).
            item(key = "poster") {
                val posterWidth = when {
                    isTv -> 160.dp
                    windowSizeClass == WindowSizeClass.Expanded -> 140.dp
                    else -> 120.dp
                }
                val posterHeight = posterWidth * 1.2f
                val overlap = 40.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(posterHeight - overlap),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = contentPad)
                            .offset(y = -overlap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (!item.posterPath.isNullOrBlank()) {
                            MediaImage(
                                url = item.posterPath!!,
                                contentDescription = item.name,
                                blurHash = item.blurHashPrimary,
                                modifier = Modifier
                                    .width(posterWidth)
                                    .requiredHeight(posterHeight)
                                    .clip(ShapeCache.smooth8),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

                // ── Title + metadata block (mirrors online DetailContentBody) ──
                item(key = "title") {
                    StaggeredSection(delayIndex = 0) {
                        Column(modifier = Modifier.padding(horizontal = contentPad)) {
                            // "Series › Season" breadcrumb (episode detail only),
                            // mirroring the online DetailContentBody.
                            if (isEpisode && item.seriesId != null) {
                                val seriesNavFocusState = rememberTvFocusState(focusedScale = 1.02f)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(ShapeCache.smooth8)
                                        .then(seriesNavFocusState.focusModifier)
                                        .then(Modifier.tvFocusIndicator(seriesNavFocusState, ShapeCache.smooth8))
                                        .clickable { item.seriesId?.let(onNavigateToSeries) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val seriesDefault = stringResource(R.string.downloads_series_default)
                                    Text(
                                        text = item.seriesName ?: seriesDefault,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    item.seasonName?.let { season ->
                                        Text(
                                            text = " › ",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                        Text(
                                            text = season,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            // "S{season} · E{episode} · {series}" context line
                            // (episode detail only), mirroring the online screen.
                            if (isEpisode) {
                                val episodeContext = buildString {
                                    item.seasonNumber?.let { append("S$it") }
                                    item.episodeNumber?.let {
                                        if (isNotEmpty()) append(" · ")
                                        append("E$it")
                                    }
                                    item.seriesName?.takeIf { it.isNotBlank() }?.let { series ->
                                        if (isNotEmpty()) append(" · ")
                                        append(series)
                                    }
                                }
                                if (episodeContext.isNotBlank()) {
                                    Text(
                                        text = episodeContext,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }

                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.originalTitle
                                ?.takeIf { it.isNotBlank() && !it.equals(item.name, ignoreCase = true) }
                                ?.let { originalTitle ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = originalTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
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
                                ChipRow(
                                    values = item.studios,
                                    container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                )
                            }
                            item.tagline?.takeIf { it.isNotBlank() }?.let { tagline ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = tagline,
                                    style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── Action row (Play + progress fill) ──
                item(key = "actions") {
                    StaggeredSection(delayIndex = 1) {
                        Column(modifier = Modifier.padding(horizontal = contentPad)) {
                            Spacer(Modifier.height(24.dp))
                            PlayButton(
                                label = playLabel,
                                hasProgress = hasProgress,
                                progressFraction = (item.playedPercentage / 100f).toFloat(),
                                focusState = playFocusState,
                                onClick = { onPlayOffline(item.id, item.mediaType) },
                            )
                        }
                    }
                }

                // ── Overview ──
                item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    item(key = "overview") {
                        StaggeredSection(delayIndex = 2) {
                            Column(modifier = Modifier.padding(horizontal = contentPad)) {
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    maxLines = if (overviewExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (overview.length > 200) {
                                    TextButton(onClick = { overviewExpanded = !overviewExpanded }) {
                                        Text(stringResource(if (overviewExpanded) R.string.downloads_show_less else R.string.downloads_show_more))
                                        Icon(
                                            Tabler.Outline.ChevronDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Seasons / episodes list (episode detail only). Mirrors the
                // online episode detail screen, which re-renders the seasons
                // section below the overview so users can jump between episodes.
                // Skipped when the episode has no series link (older downloads). ──
                if (isEpisode && item.seriesId != null && seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        StaggeredSection(delayIndex = 3) {
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                OfflineSeasonsSection(
                                    seasons = seasons,
                                    episodes = episodes,
                                    contentPad = contentPad,
                                    currentItemId = item.id,
                                    currentSeasonId = item.seasonId,
                                    onEpisodePlay = { episode ->
                                        onPlayOffline(episode.id, MediaType.EPISODE)
                                    },
                                    onEpisodeDetail = { episode -> onNavigateToDetail(episode.id) },
                                    onEpisodeDelete = { episode -> onEpisodeDelete(episode.id) },
                                    onMarkSeasonPlayed = onMarkSeasonPlayed,
                                    onMarkSeasonUnplayed = onMarkSeasonUnplayed,
                                    compactEpisodeList = compactEpisodeList,
                                    onCompactEpisodeListChange = onCompactEpisodeListChange,
                                )
                            }
                        }
                    }
                }

                // ── Album tracks ──
                if (isAudio && children.isNotEmpty()) {
                    item(key = "tracks") {
                        StaggeredSection(delayIndex = 3) {
                            Column(modifier = Modifier.padding(horizontal = contentPad)) {
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.downloads_tracks),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    children.forEachIndexed { index, track ->
                                        // Memoize the per-row click lambda so it isn't reallocated
                                        // on every recomposition of this detail item.
                                        val click = remember(track.id, track.mediaType) {
                                            { onPlayOffline(track.id, track.mediaType) }
                                        }
                                        OfflineTrackRow(
                                            track = track,
                                            index = index + 1,
                                            onClick = click,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Download info card ──
                item(key = "downloadInfo") {
                    StaggeredSection(delayIndex = 4) {
                        Column(modifier = Modifier.padding(horizontal = contentPad)) {
                            Spacer(Modifier.height(24.dp))
                            // Freshness banner: surfaces a server-detected update
                            // (metadata/images changed) or a media-file change that
                            // needs a full re-download. Tappable to open the resync
                            // sheet. Hidden when there's nothing to act on.
                            SyncUpdateBanner(
                                syncState = syncState,
                                resyncState = resyncState,
                                onClick = onShowSyncSheet,
                            )
                            DownloadInfoCard(item = item)
                        }
                    }
                }

                // ── Cast & crew ──
                if (item.cast.isNotEmpty()) {
                    item(key = "cast") {
                        StaggeredSection(delayIndex = 5) {
                            Column {
                                Text(
                                    text = stringResource(R.string.downloads_cast_crew),
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
                                        // Prefer the on-disk cast image (resolved
                                        // by the repository) so the row renders
                                        // without network even after Coil's memory
                                        // cache evicts the entry; fall back to the
                                        // remote URL (a blurhash placeholder if the
                                        // fetch fails offline).
                                        imageUrl = person.localImagePath ?: personImageUrl(person.id),
                                        modifier = focusModifier,
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom clearance.
                item { Spacer(Modifier.height(if (isTv) 80.dp else 100.dp)) }
            }

            // ── Transparent overlay top bar (matches the online detail screen):
            // sits on top of the backdrop, container + title fade in only once
            // the backdrop has scrolled away. The back button always shows with
            // its translucent circle so it stays legible over the image. ──
            TransparentTopBar(
                title = item.name,
                onBack = onBack,
                containerColor = animatedContainerColor,
                titleAlpha = animatedTitleAlpha,
                scrollCollapsed = scrollCollapsed,
                actions = {
                    val deleteFocus = rememberTvFocusState()
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .then(deleteFocus.focusModifier)
                            .tvFocusIndicator(deleteFocus, CircleShape)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (scrollCollapsed < 0.5f)
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                else Color.Transparent,
                            ),
                    ) {
                        Icon(
                            Tabler.Outline.Trash,
                            contentDescription = stringResource(R.string.downloads_delete_download_cd),
                            tint = if (scrollCollapsed < 0.5f) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text(stringResource(R.string.downloads_delete_download_title)) },
            text = { Text(stringResource(R.string.downloads_delete_download_message, item.name, item.totalSizeBytes.formatBytes())) },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.downloads_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.downloads_cancel)) }
            },
        )
    }
}

@Composable
private fun InfoRow(item: OfflineMediaItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isPlayed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Tabler.Outline.Check,
                    contentDescription = stringResource(R.string.downloads_watched),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.downloads_watched_label),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
private fun PlayButton(
    label: String,
    hasProgress: Boolean,
    progressFraction: Float,
    focusState: com.raulshma.jellyplay.core.ui.tv.TvFocusState,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.primary)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth16)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Progress fill behind the label when partially watched.
        if (hasProgress) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.24f)),
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
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun DownloadInfoCard(item: OfflineMediaItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Tabler.Outline.DeviceFloppy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.downloads_download_info), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        InfoLine(label = stringResource(R.string.downloads_file_size), value = item.totalSizeBytes.formatBytes().takeIf { it.isNotBlank() } ?: "—")
        InfoLine(label = stringResource(R.string.downloads_downloaded), value = if (item.createdAt > 0) formatDate(item.createdAt) else "—")
        InfoLine(label = stringResource(R.string.downloads_status), value = item.downloadStatus?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—")
        if (item.downloadedBytes > 0 && item.totalSizeBytes > 0 && item.downloadedBytes < item.totalSizeBytes) {
            InfoLine(label = stringResource(R.string.downloads_progress), value = "${(item.downloadedBytes.toFloat() / item.totalSizeBytes * 100).toInt()}%")
        }

        // ── Watch progress ──
        // Shown alongside the download info so the user can see, at a glance,
        // how far they got and when they last watched — the same fields the
        // offline-sync worker reconciles against the server. Skipped entirely
        // for items with no recorded progress (position == null/0 and not
        // played) so fresh downloads don't show a redundant "0%" row.
        val positionTicks = item.playbackPositionTicks ?: 0L
        val hasWatchProgress = item.isPlayed ||
            positionTicks > 0L ||
            item.playedPercentage > 0.0
        if (hasWatchProgress) {
            Spacer(Modifier.height(4.dp))
            WatchProgressSection(item)
        }
    }
}

@Composable
private fun WatchProgressSection(item: OfflineMediaItem) {
    val watchStatus = when {
        item.isPlayed -> stringResource(R.string.downloads_watched)
        item.playedPercentage in 1.0..94.99 -> stringResource(R.string.downloads_in_progress)
        else -> stringResource(R.string.downloads_started)
    }
    InfoLine(label = stringResource(R.string.downloads_watch_status), value = watchStatus)

    // Played percentage: prefer the derived stored value, fall back to computing
    // from position/runtime so an item seeded only with ticks still shows a %.
    val percentage = item.playedPercentage
        .takeIf { it > 0.0 }
        ?: computeWatchPercentage(item.playbackPositionTicks, item.runTimeTicks)
    if (percentage > 0.0) {
        InfoLine(label = stringResource(R.string.downloads_watched_label), value = "${percentage.toInt()}%")
    }

    // Position / runtime — "23m of 1h 2m".
    val position = item.playbackPositionTicks ?: 0L
    val runtime = item.runTimeTicks ?: 0L
    if (position > 0L) {
        val posStr = formatDurationFromTicks(position)
        val value = if (runtime > position) stringResource(R.string.downloads_position_of, posStr, formatDurationFromTicks(runtime)) else posStr
        InfoLine(label = stringResource(R.string.downloads_position), value = value)
    }

    // Last tracked time (relative, e.g. "2d ago"). Falls back to absolute date
    // when the relative formatter can't parse the stored timestamp.
    val lastPlayedRelative = formatRelativeTime(item.lastPlayedDate)
    val lastPlayedValue = lastPlayedRelative
        ?: item.lastPlayedDate?.let { formatAbsoluteDate(it) }
    if (lastPlayedValue != null) {
        InfoLine(label = stringResource(R.string.downloads_last_watched), value = lastPlayedValue)
    }
}

/** Derives a 0–100 watched percentage from position/runtime, guarding /0. */
private fun computeWatchPercentage(positionTicks: Long?, runTimeTicks: Long?): Double {
    if (positionTicks == null || positionTicks <= 0L) return 0.0
    if (runTimeTicks == null || runTimeTicks <= 0L) return 0.0
    return ((positionTicks.toDouble() / runTimeTicks.toDouble()) * 100.0).coerceIn(0.0, 100.0)
}

/** Best-effort absolute-date fallback for an ISO timestamp string. */
private fun formatAbsoluteDate(isoTimestamp: String): String? =
    runCatching {
        val millis = runCatching {
            java.time.OffsetDateTime.parse(isoTimestamp).toInstant().toEpochMilli()
        }.getOrElse {
            java.time.LocalDateTime.parse(isoTimestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }.getOrNull()

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
        Icon(Tabler.Outline.PlayerPlay, contentDescription = stringResource(R.string.downloads_action_play), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

/**
 * Inline freshness banner shown above the download-info card. Surfaces a
 * server-detected update or media-file change, and opens the resync sheet on
 * tap. Hidden unless there's something to act on (update available, media
 * changed, checking, or an active/complete resync).
 */
@Composable
private fun SyncUpdateBanner(
    syncState: com.raulshma.jellyplay.core.model.OfflineSyncState?,
    resyncState: ResyncUiState,
    onClick: () -> Unit,
) {
    when {
        resyncState is ResyncUiState.Working -> ResyncBannerRow(
            icon = Tabler.Outline.Refresh,
            tint = MaterialTheme.colorScheme.primary,
            text = stringResource(R.string.downloads_resync_in_progress),
            progress = true,
            onClick = onClick,
        )
        resyncState is ResyncUiState.Done -> ResyncBannerRow(
            icon = Tabler.Outline.Check,
            tint = MaterialTheme.colorScheme.primary,
            text = stringResource(R.string.downloads_resync_complete),
            onClick = onClick,
        )
        resyncState is ResyncUiState.Error -> ResyncBannerRow(
            icon = Tabler.Outline.AlertTriangle,
            tint = MaterialTheme.colorScheme.error,
            text = stringResource(R.string.downloads_resync_failed),
            onClick = onClick,
        )
        syncState?.status == com.raulshma.jellyplay.core.model.SyncStatus.ERROR -> ResyncBannerRow(
            icon = Tabler.Outline.AlertTriangle,
            tint = MaterialTheme.colorScheme.error,
            text = stringResource(R.string.downloads_resync_failed),
            onClick = onClick,
        )
        syncState?.status == com.raulshma.jellyplay.core.model.SyncStatus.CHECKING -> ResyncBannerRow(
            icon = Tabler.Outline.Refresh,
            tint = MaterialTheme.colorScheme.primary,
            text = stringResource(R.string.downloads_resync_checking),
            progress = true,
            onClick = onClick,
        )
        syncState?.status == com.raulshma.jellyplay.core.model.SyncStatus.UPDATE_AVAILABLE -> ResyncBannerRow(
            icon = Tabler.Outline.AlertCircle,
            tint = if (syncState.mediaFileChanged) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.tertiary,
            text = if (syncState.mediaFileChanged) {
                stringResource(R.string.downloads_resync_media_changed)
            } else {
                stringResource(R.string.downloads_resync_update_available)
            },
            onClick = onClick,
        )
        else -> Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun ResyncBannerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String,
    progress: Boolean = false,
    onClick: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (progress) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Tabler.Outline.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Resync detail bottom sheet. Lists what changed (metadata/images/media) and
 * offers a resync action with live status. For a media-file change it explains
 * that a full re-download is required (the sheet doesn't trigger that itself —
 * it points the user back to delete + download).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OfflineResyncSheet(
    syncState: com.raulshma.jellyplay.core.model.OfflineSyncState?,
    resyncState: ResyncUiState,
    onResync: () -> Unit,
    onRedownloadMedia: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.downloads_resync_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val status = syncState?.status
            if (status == com.raulshma.jellyplay.core.model.SyncStatus.UPDATE_AVAILABLE ||
                status == com.raulshma.jellyplay.core.model.SyncStatus.CHECKING
            ) {
                Text(
                    stringResource(R.string.downloads_resync_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (syncState?.metadataChanged == true) {
                    ResyncChangeChip(stringResource(R.string.downloads_resync_change_metadata))
                }
                if (syncState?.imagesChanged == true) {
                    ResyncChangeChip(stringResource(R.string.downloads_resync_change_images))
                }
                if (syncState?.mediaFileChanged == true) {
                    ResyncChangeChip(stringResource(R.string.downloads_resync_change_media), error = true)
                    Text(
                        stringResource(R.string.downloads_resync_media_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.downloads_resync_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (resyncState) {
                is ResyncUiState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.downloads_resync_in_progress), style = MaterialTheme.typography.bodyMedium)
                }
                is ResyncUiState.Error -> Text(
                    stringResource(R.string.downloads_resync_failed) + ": " + resyncState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {}
            }

            if (syncState?.needsResync == true && resyncState !is ResyncUiState.Working) {
                androidx.compose.material3.Button(
                    onClick = onResync,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.downloads_resync_action))
                }
            }
            // When only the media file changed, offer the full re-download path —
            // a metadata/images resync can't fix it. Uses an error-toned button so
            // it reads as destructive (it deletes the existing file first).
            if (syncState?.mediaFileChanged == true && resyncState !is ResyncUiState.Working) {
                androidx.compose.material3.Button(
                    onClick = onRedownloadMedia,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Tabler.Outline.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.downloads_resync_redownload))
                }
            }
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.downloads_close))
            }
        }
    }
}

@Composable
private fun ResyncChangeChip(text: String, error: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (error) Tabler.Outline.AlertTriangle else Tabler.Outline.Check,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
