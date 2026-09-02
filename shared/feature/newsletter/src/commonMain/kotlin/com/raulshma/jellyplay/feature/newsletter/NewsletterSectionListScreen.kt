package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.feature.newsletter.generated.resources.Res
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_continue_watching
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_fresh_picks
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_next_up
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_no_items_available
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_recently_added
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NewsletterSectionListScreen(
    sectionType: String,
    onBack: () -> Unit,
    onItemClick: (MediaItem) -> Unit = {},
    viewModel: NewsletterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val title = when (sectionType) {
        "FRESH_PICKS" -> stringResource(Res.string.newsletter_fresh_picks)
        "RECENTLY_ADDED" -> stringResource(Res.string.newsletter_recently_added)
        "CONTINUE_WATCHING" -> stringResource(Res.string.newsletter_continue_watching)
        "NEXT_UP" -> stringResource(Res.string.newsletter_next_up)
        else -> sectionType
    }

    val items: List<MediaItem> = when (sectionType) {
        "FRESH_PICKS" -> state.curatedPicks.filter { it.mediaType != MediaType.COLLECTION }
        "RECENTLY_ADDED" -> state.recentlyAdded
        "CONTINUE_WATCHING" -> state.continueWatching
        "NEXT_UP" -> state.nextUp
        else -> emptyList()
    }

    JellyPlayScreenScaffold(
        title = title,
        onBack = onBack,
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.newsletter_no_items_available),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val adaptiveInfo = LocalAdaptiveInfo.current
            val contentPad = adaptiveInfo.contentPadding(false)
            val gridMin = adaptiveInfo.gridMinSize(false)
            val spacing = adaptiveInfo.itemSpacing(false)

            TvFocusableGrid(
                items = items,
                key = { it.id },
                columns = GridCells.Adaptive(gridMin),
                contentPadding = PaddingValues(
                    start = contentPad,
                    end = contentPad,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentType = { "mediaItem" },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) { _, item, itemModifier ->
                NewsletterGridCard(
                    item = item,
                    imageUrl = viewModel.getImageUrl(item.id),
                    onClick = { onItemClick(item) },
                    modifier = itemModifier,
                )
            }
        }
    }
}

@Composable
private fun NewsletterGridCard(
    item: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ShapeCache.smooth12),
        ) {
            PosterCard(
                item = item,
                imageUrl = imageUrl,
                onClick = onClick,
                modifier = modifier,
                showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                    (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                } else 0f,
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.communityRating?.let { rating ->
                Text(
                    text = "★ ${"%.1f".format(rating)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RatingColors.star,
                )
            }
            if (item.mediaType != MediaType.UNKNOWN) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = item.mediaType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.take(3).joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
