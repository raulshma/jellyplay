package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_artists
import com.raulshma.jellyplay.feature.music.generated.resources.music_artists_section_subtitle
import com.raulshma.jellyplay.feature.music.generated.resources.music_view_all
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ArtistsSection(
    artists: List<MediaItem>,
    onArtistClick: (String) -> Unit,
    onArtistPlayClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    imageUrlBuilder: (String) -> String,
    modifier: Modifier = Modifier,
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
                    text = stringResource(Res.string.music_artists),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.music_artists_section_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ViewAllButton(
                onClick = onViewAllClick,
                focusRequester = headerFocusRequester,
                upFocusRequester = upFocusRequester,
                downFocusRequester = rowFocusRequester,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TvFocusableItemRow(
            items = artists,
            key = { it.id },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        ) { _, artist, itemModifier ->
            HeartShapeArtistCard(
                artist = artist,
                onClick = { onArtistClick(artist.id) },
                onPlayClick = { onArtistPlayClick(artist.id) },
                imageUrl = imageUrlBuilder(artist.id),
                modifier = itemModifier,
            )
        }
    }
}

@Composable
private fun ViewAllButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "view_all_scale"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                up = upFocusRequester ?: FocusRequester.Default
                down = downFocusRequester ?: FocusRequester.Default
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smoothPill)
            .clip(ShapeCache.smoothPill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.music_view_all),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Tabler.Outline.ArrowRight,
            contentDescription = stringResource(Res.string.music_view_all),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
