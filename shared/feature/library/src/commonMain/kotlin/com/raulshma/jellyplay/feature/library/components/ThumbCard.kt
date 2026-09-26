package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.clickModifier
import com.raulshma.jellyplay.core.ui.components.displayTitle
import com.raulshma.jellyplay.core.ui.components.jellyFocusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberCardChrome
import com.raulshma.jellyplay.core.ui.components.rememberSharedElementModifier
import com.raulshma.jellyplay.core.ui.image.MediaImage

// Constant inputs, so a single shared instance serves every card in the grid.
private val ThumbScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
)

/**
 * A 16:9 landscape card for the library "Thumb" view mode — used for
 * libraries whose collection type suggests landscape artwork (music videos,
 * home videos, trailers). A chrome-layer specialization (like [com.raulshma.jellyplay.core.ui.components.WideMediaCard]
 * is of the scaffold): the card chrome (focus, press, click) seats on
 * [rememberCardChrome] — the exact implementation [com.raulshma.jellyplay.core.ui.components.MediaCardScaffold]
 * consumes — while this file keeps its own shape: a backdrop image with the
 * title and year overlaid at the bottom under a full-bleed scrim (its scrim,
 * overlaid title, and 3dp Material progress bar are layout-shaped and stay
 * local). No long-press / peek, and the indication stays hard-null in every
 * motion mode, exactly as before.
 */
@Composable
fun ThumbCard(
    item: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    progressPercent: Float = 0f,
    blurHash: String? = null,
    sharedElementKey: String? = null,
    fallbackUrls: List<String> = emptyList(),
) {
    val cardShape = ShapeCache.smooth12
    // Press feedback mirrors PosterCard: 0.95 scale under the motion scheme
    // (AnimationTokens.CardPressScale). The former hand copy stacked an
    // unanimated 0.95 graphicsLayer on top of the animated one — the chrome
    // keeps only the animated value. Focus tracking sits above the clickable
    // (the scaffold's proven pattern) instead of the legacy focusIndicator +
    // redundant .focusable() pair.
    val chrome = rememberCardChrome(pressScaleValue = 0.95f)

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(chrome.focus.modifier)
                .then(chrome.pressScale)
                .jellyFocusIndicator(chrome.focus, cardShape)
                .then(chrome.clickModifier(onClick = onClick, useReducedMotionIndication = false)),
            shape = cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(cardShape),
            ) {
                MediaImage(
                    url = imageUrl,
                    fallbackUrls = fallbackUrls,
                    contentDescription = item.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .then(rememberSharedElementModifier(sharedElementKey)),
                    blurHash = blurHash,
                    // Decode at a landscape ratio; ~160dp-wide card at 2× density.
                    size = CoilSize(320, 180),
                )
                // Bottom scrim for legibility of the overlaid title.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(ThumbScrimBrush),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = item.displayTitle(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.year != null) {
                        Text(
                            text = item.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                }
                if (showProgress) {
                    LinearProgressIndicator(
                        progress = { progressPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }
            }
        }
    }
}
