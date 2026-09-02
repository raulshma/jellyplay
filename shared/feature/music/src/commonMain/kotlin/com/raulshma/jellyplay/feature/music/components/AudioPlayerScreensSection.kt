package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_albums_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_ambient_mode
import com.raulshma.jellyplay.feature.music.generated.resources.music_ambient_mode_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_artists
import com.raulshma.jellyplay.feature.music.generated.resources.music_artists_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_genres
import com.raulshma.jellyplay.feature.music.generated.resources.music_genres_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_now_playing
import com.raulshma.jellyplay.feature.music.generated.resources.music_now_playing_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_player_screens
import com.raulshma.jellyplay.feature.music.generated.resources.music_player_screens_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_playlists
import com.raulshma.jellyplay.feature.music.generated.resources.music_playlists_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks_subtitle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayerScreensSection(
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    onTracksClick: () -> Unit = {},
    onAlbumsClick: () -> Unit = {},
    onArtistsClick: () -> Unit = {},
    onGenresClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    rowModifier: Modifier = Modifier,
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
                    text = stringResource(Res.string.music_player_screens),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.music_player_screens_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .then(rowFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(rowModifier)
                .focusGroup()
                .tvFocusRestorer(),
        ) {
            item {
                NowPlayingExpressiveCard(
                    onClick = onNowPlayingClick,
                    focusRequester = firstFocusRequester,
                )
            }
            item {
                AmbientExpressiveCard(
                    onClick = onAmbientClick,
                )
            }
            item {
                PlayerScreenCard(
                    onClick = onTracksClick,
                    title = stringResource(Res.string.music_tracks),
                    subtitle = stringResource(Res.string.music_tracks_subtitle),
                    icon = Tabler.Outline.Music,
                    startColor = MaterialTheme.colorScheme.secondary,
                    endColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
            item {
                PlayerScreenCard(
                    onClick = onAlbumsClick,
                    title = stringResource(Res.string.music_albums),
                    subtitle = stringResource(Res.string.music_albums_subtitle),
                    icon = Tabler.Outline.Vinyl,
                    startColor = MaterialTheme.colorScheme.primaryContainer,
                    endColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            item {
                PlayerScreenCard(
                    onClick = onArtistsClick,
                    title = stringResource(Res.string.music_artists),
                    subtitle = stringResource(Res.string.music_artists_subtitle),
                    icon = Tabler.Outline.Users,
                    startColor = MaterialTheme.colorScheme.tertiaryContainer,
                    endColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            item {
                PlayerScreenCard(
                    onClick = onGenresClick,
                    title = stringResource(Res.string.music_genres),
                    subtitle = stringResource(Res.string.music_genres_subtitle),
                    icon = Tabler.Outline.Category,
                    startColor = MaterialTheme.colorScheme.inversePrimary,
                    endColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            item {
                PlayerScreenCard(
                    onClick = onPlaylistsClick,
                    title = stringResource(Res.string.music_playlists),
                    subtitle = stringResource(Res.string.music_playlists_subtitle),
                    icon = Tabler.Outline.Playlist,
                    startColor = MaterialTheme.colorScheme.secondaryContainer,
                    endColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "now_playing_scale"
    )

    Column(
        modifier = modifier
            .width(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth24)
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(ShapeCache.smooth20)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Tabler.Outline.Disc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.music_now_playing),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.music_now_playing_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun PlayerScreenCard(
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    icon: ImageVector,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "${title.lowercase().replace(" ", "_")}_scale"
    )

    Column(
        modifier = modifier
            .width(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth24)
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(ShapeCache.smooth20)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(startColor, endColor),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun AmbientExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "ambient_scale"
    )

    Column(
        modifier = modifier
            .width(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth24)
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(ShapeCache.smooth20)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .blur(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f))
                    .align(Alignment.TopStart)
                    .offset(x = 30.dp, y = 20.dp),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .blur(15.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.15f))
                    .align(Alignment.BottomEnd)
                    .offset(x = (-20).dp, y = (-15).dp),
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Tabler.Outline.MoonStars,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.music_ambient_mode),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.music_ambient_mode_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
        }
    }
}
