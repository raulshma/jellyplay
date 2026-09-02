package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_fresh_music
import com.raulshma.jellyplay.feature.music.generated.resources.music_new_releases
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NewReleasesSection(
    albums: List<MediaItem>,
    onAlbumClick: (String) -> Unit,
    onAlbumPlayClick: (String) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    imageUrlBuilder: (String) -> String,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.music_new_releases),
    subtitle: String = stringResource(Res.string.music_fresh_music),
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
            items = albums,
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
        ) { _, album, itemModifier ->
            CloverShapeAlbumCard(
                album = album,
                onClick = { onAlbumClick(album.id) },
                onPlayClick = { onAlbumPlayClick(album.id) },
                imageUrl = imageUrlBuilder(album.id),
                modifier = itemModifier,
            )
        }
    }
}
