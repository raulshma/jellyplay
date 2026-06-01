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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewReleasesSection(
    albums: List<MediaItem>,
    onAlbumClick: (String) -> Unit,
    onAlbumPlayClick: (String) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    imageUrlBuilder: (String) -> String,
    modifier: Modifier = Modifier,
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
                    text = "New Releases",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fresh music just for you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PlayShuffleSplitButton(
                onPlayClick = onPlayAllClick,
                onShuffleClick = onShuffleClick,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                CloverShapeAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album.id) },
                    onPlayClick = { onAlbumPlayClick(album.id) },
                    imageUrl = imageUrlBuilder(album.id),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayShuffleSplitButton(
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shuffleChecked by remember { mutableStateOf(false) }

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onPlayClick,
                modifier = modifier.height(40.dp),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(40.dp),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = "Play",
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(40.dp)),
                )
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(40.dp)))
                Text(
                    text = "Play",
                    style = ButtonDefaults.textStyleFor(40.dp),
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = shuffleChecked,
                onCheckedChange = {
                    shuffleChecked = it
                    if (it) onShuffleClick()
                },
                modifier = modifier.height(40.dp),
                shapes = SplitButtonDefaults.trailingButtonShapesFor(40.dp),
                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowsShuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(40.dp)),
                )
            }
        },
    )
}
