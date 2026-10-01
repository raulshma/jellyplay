package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/*
 * The section-renderer vocabulary genuinely shared by the two detail
 * families — the media-detail screen ([MediaDetailBody.kt], jvmShared) and
 * the Seerr detail screen ([SeerrDetailSections.kt], commonMain) — promoted
 * to commonMain so the duplicate renderers collapse onto one implementation.
 *
 * Scope discipline: the DUMB shells only. The per-family decision tables
 * stay put — which rows exist, their headings/semantics, the row
 * scaffolding (TvFocusableItemRow vs FocusRestoringItemRow), and the
 * Seerr-request dialog hosting are family concerns; only pieces with
 * pixel-identical bodies move here. The cast sections, despite the
 * same-sounding name, are NOT collapsed: the media family renders
 * [PersonItem] over Jellyfin [com.raulshma.jellyplay.core.model.PersonInfo]
 * (server id navigation, "see all" split, OfflinePersonItem branch for
 * local origins) while Seerr renders TMDB profile avatars over
 * [SeerrCastMember] with different geometry (100dp column, circle avatar) —
 * collapsing them would change one family's UI.
 */
/**
 * The YouTube video card both families' related-videos rows render: 240dp
 * 16:9 thumbnail (via [youTubeThumbnailUrl]), bottom scrim, one-line name
 * and the center play glyph — collapsed verbatim from the two copies that
 * differed only in card border (media family draws the theme detail-card
 * border; Seerr is borderless) and the null-name fallback label.
 *
 * TV focus handling lives inside (the shared [rememberTvFocusState] at the
 * families' common 1.05f scale); [modifier] is the outer slot each row's
 * scaffolding passes its per-item focus modifier through (empty for the
 * Seerr row, which carries none).
 */
@Composable
internal fun YouTubeVideoCard(
    video: SeerrRelatedVideo,
    fallbackLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    val thumbnailUrl = remember(video.site, video.key) {
        youTubeThumbnailUrl(video.site, video.key)
    }

    val videoCardFocusState = rememberTvFocusState(focusedScale = 1.05f)

    // The bottom scrim gradient is identical across every card in the same
    // theme state, so compute it once per card instead of allocating a Brush
    // per recomposition (the HomeMediaRows pattern); the border stroke is
    // resolved by the caller (theme-dependent, family-specific look).
    val surfaceColor = MaterialTheme.colorScheme.surface
    val videoScrimBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.85f),
            ),
            startY = 100f,
        )
    }

    Card(
        modifier = modifier
            .width(240.dp)
            .aspectRatio(16f / 9f)
            .then(videoCardFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(videoCardFocusState, ShapeCache.smooth8))
            .clickable { onClick() },
        shape = ShapeCache.smooth8,
        border = border,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailUrl != null) {
                MediaImage(
                    url = thumbnailUrl,
                    contentDescription = video.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.PlayerPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(videoScrimBrush),
            )

            Text(
                text = video.name ?: fallbackLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}
