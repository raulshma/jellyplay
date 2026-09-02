package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_play
import com.raulshma.jellyplay.core.ui.image.MediaImage
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

@Composable
fun HeartShapeArtistCard(
    artist: MediaItem,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "artist_card_scale"
    )

    Column(
        modifier = modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth20)
            .clip(ShapeCache.smooth20)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Heart-shaped image
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(HeartShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl.isNotEmpty()) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = artist.name,
                        blurHash = artist.blurHashes.primary,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(HeartShape()),
                    )
                }
            }

            // Play button overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-8).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onPlayClick
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = stringResource(Res.string.music_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
    }
}
