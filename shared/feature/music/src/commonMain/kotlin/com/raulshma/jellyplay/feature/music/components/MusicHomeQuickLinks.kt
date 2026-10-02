package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Category
import com.composables.icons.tabler.outline.MoonStars
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Vinyl
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_albums
import com.raulshma.jellyplay.feature.music.generated.resources.music_ambient_mode
import com.raulshma.jellyplay.feature.music.generated.resources.music_artists
import com.raulshma.jellyplay.feature.music.generated.resources.music_browse_music
import com.raulshma.jellyplay.feature.music.generated.resources.music_genres
import com.raulshma.jellyplay.feature.music.generated.resources.music_now_playing
import com.raulshma.jellyplay.feature.music.generated.resources.music_playlists
import com.raulshma.jellyplay.feature.music.generated.resources.music_tracks
import org.jetbrains.compose.resources.stringResource

private data class MusicHomeAction(
    val title: String,
    val icon: ImageVector,
    val iconContainer: Color,
    val iconTint: Color,
    val onClick: () -> Unit,
)

@Composable
fun MusicHomeQuickLinks(
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
    val colors = MaterialTheme.colorScheme
    val actions = listOf(
        MusicHomeAction(
            title = stringResource(Res.string.music_now_playing),
            icon = Tabler.Outline.PlayerPlay,
            iconContainer = colors.primaryContainer,
            iconTint = colors.onPrimaryContainer,
            onClick = onNowPlayingClick,
        ),
        MusicHomeAction(
            title = stringResource(Res.string.music_tracks),
            icon = Tabler.Outline.Music,
            iconContainer = colors.secondaryContainer,
            iconTint = colors.onSecondaryContainer,
            onClick = onTracksClick,
        ),
        MusicHomeAction(
            title = stringResource(Res.string.music_albums),
            icon = Tabler.Outline.Vinyl,
            iconContainer = colors.tertiaryContainer,
            iconTint = colors.onTertiaryContainer,
            onClick = onAlbumsClick,
        ),
        MusicHomeAction(
            title = stringResource(Res.string.music_artists),
            icon = Tabler.Outline.Users,
            iconContainer = colors.secondaryContainer,
            iconTint = colors.onSecondaryContainer,
            onClick = onArtistsClick,
        ),
        MusicHomeAction(
            title = stringResource(Res.string.music_genres),
            icon = Tabler.Outline.Category,
            iconContainer = colors.tertiaryContainer,
            iconTint = colors.onTertiaryContainer,
            onClick = onGenresClick,
        ),
        MusicHomeAction(
            title = stringResource(Res.string.music_playlists),
            icon = Tabler.Outline.Playlist,
            iconContainer = colors.primaryContainer,
            iconTint = colors.onPrimaryContainer,
            onClick = onPlaylistsClick,
        ),
        MusicHomeAction(
            title = stringResource(Res.string.music_ambient_mode),
            icon = Tabler.Outline.MoonStars,
            iconContainer = colors.secondaryContainer,
            iconTint = colors.onSecondaryContainer,
            onClick = onAmbientClick,
        ),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.music_browse_music),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        TvFocusableItemRow(
            items = actions,
            key = { it.title },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            focusRequester = rowFocusRequester,
            modifier = rowModifier,
        ) { index, action, itemModifier ->
            MusicHomeActionCard(
                action = action,
                modifier = itemModifier.then(
                    if (index == 0 && firstFocusRequester != null) {
                        Modifier.focusRequester(firstFocusRequester)
                    } else {
                        Modifier
                    },
                ),
            )
        }
    }
}

@Composable
private fun MusicHomeActionCard(
    action: MusicHomeAction,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "music_home_action_scale",
    )

    Row(
        modifier = modifier
            .width(156.dp)
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth20)
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = action.onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(ShapeCache.smooth14)
                .background(action.iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = action.iconTint,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
