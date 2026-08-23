package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_play
import com.raulshma.jellyplay.feature.music.generated.resources.music_recently_played
import com.raulshma.jellyplay.feature.music.generated.resources.music_recently_played_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_unknown_artist
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun RecentlyPlayedSection(
    tracks: List<MediaItem>,
    onTrackClick: (String) -> Unit,
    onTrackPlayClick: (Int) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    imageUrlBuilder: (String) -> String,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.music_recently_played),
    subtitle: String = stringResource(Res.string.music_recently_played_subtitle),
    headerFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PlayShuffleSplitButton(
                onPlayClick = onPlayAllClick,
                onShuffleClick = onShuffleClick,
                playFocusRequester = headerFocusRequester,
                upFocusRequester = upFocusRequester,
                downFocusRequester = rowFocusRequester,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TvFocusableItemRow(
            items = tracks,
            key = { it.id },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            focusRequester = rowFocusRequester,
            modifier = Modifier.focusProperties {
                @Suppress("DEPRECATION")
                exit = { direction ->
                    when (direction) {
                        FocusDirection.Up -> headerFocusRequester ?: FocusRequester.Default
                        FocusDirection.Down -> downFocusRequester ?: FocusRequester.Default
                        else -> FocusRequester.Default
                    }
                }
            }
        ) { index, track, itemModifier ->
            RecentTrackCard(
                track = track,
                onClick = { onTrackClick(track.id) },
                onPlayClick = { onTrackPlayClick(index) },
                imageUrl = imageUrlBuilder(track.id),
                modifier = itemModifier,
            )
        }
    }
}

@Composable
private fun RecentTrackCard(
    track: MediaItem,
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
        label = "track_card_scale"
    )

    Column(
        modifier = modifier
            .width(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth16)
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(ShapeCache.smooth12)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (imageUrl.isNotEmpty()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = track.name,
                    blurHash = track.blurHashes.primary,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(ShapeCache.smooth12),
                )
            }

            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        onClick = onPlayClick
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = stringResource(Res.string.music_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.albumArtist ?: track.album ?: stringResource(Res.string.music_unknown_artist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
