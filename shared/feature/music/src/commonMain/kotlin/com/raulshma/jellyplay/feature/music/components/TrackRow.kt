package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_add_to_queue
import com.raulshma.jellyplay.feature.music.generated.resources.music_favorite
import com.raulshma.jellyplay.feature.music.generated.resources.music_go_to_album
import com.raulshma.jellyplay.feature.music.generated.resources.music_go_to_artist
import com.raulshma.jellyplay.feature.music.generated.resources.music_more_options
import com.raulshma.jellyplay.feature.music.generated.resources.music_now_playing
import com.raulshma.jellyplay.feature.music.generated.resources.music_unfavorite
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*

@Composable
fun TrackRow(
    name: String,
    artist: String?,
    album: String?,
    duration: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
    isNowPlaying: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Expressive spring-based scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "itemScale",
    )

    // Shape morphing animation for album art
    val artMorphScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "artMorph"
    )

    var showMenu by remember { mutableStateOf(false) }
    val rowFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val favoriteFocusState = rememberTvFocusState(focusedScale = 1.1f)
    val moreOptionsFocusState = rememberTvFocusState(focusedScale = 1.1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(rowFocusState.focusModifier)
            .tvFocusIndicator(rowFocusState, ShapeCache.smooth8)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Subtle rotation on press for expressive feel
                rotationZ = if (isPressed) -0.5f else 0f
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    if (onAddToQueue != null) {
                        showMenu = true
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art with shape morphing
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    // Shape morphing effect
                    scaleX = artMorphScale
                    scaleY = artMorphScale
                }
                .clip(ShapeCache.smooth8)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = name,
                    blurHash = blurHash,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(ShapeCache.smooth8),
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expressive brightness overlay
            if (isPressed) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
            }
        }

        // Track info with expressive typography
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isNowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isNowPlaying) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (artist != null || album != null) {
                Text(
                    text = listOfNotNull(artist, album).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isNowPlaying) {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(Res.string.music_now_playing),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(4.dp))
        }

        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .then(favoriteFocusState.focusModifier)
                    .tvFocusIndicator(favoriteFocusState, CircleShape),
            ) {
                Icon(
                    if (isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    contentDescription = if (isFavorite) stringResource(Res.string.music_unfavorite) else stringResource(Res.string.music_favorite),
                    modifier = Modifier.size(18.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // More options button with expressive animation
        if (onAddToQueue != null) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .then(moreOptionsFocusState.focusModifier)
                    .tvFocusIndicator(moreOptionsFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.DotsVertical,
                    contentDescription = stringResource(Res.string.music_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.music_add_to_queue)) },
                    onClick = {
                        onAddToQueue()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Tabler.Outline.Playlist,
                            contentDescription = null,
                        )
                    },
                )
                if (onGoToAlbum != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.music_go_to_album)) },
                        onClick = {
                            onGoToAlbum()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Tabler.Outline.Disc,
                                contentDescription = null,
                            )
                        },
                    )
                }
                if (onGoToArtist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.music_go_to_artist)) },
                        onClick = {
                            onGoToArtist()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Tabler.Outline.User,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        // Duration with expressive styling
        if (duration != null) {
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
