package com.raulshma.jellyplay.feature.music.musichome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_play
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MusicHomeSpotlight(
    item: MediaItem,
    eyebrow: String,
    imageUrl: String,
    isExpanded: Boolean,
    isTv: Boolean,
    playFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardHeight = when {
        isExpanded -> 284.dp
        isTv -> 252.dp
        else -> 224.dp
    }
    val titleColor = MaterialTheme.colorScheme.onPrimaryContainer

    BoxWithConstraints(
        modifier = modifier
            .height(cardHeight)
            .clip(ShapeCache.smooth28)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
    ) {
        val artworkSize = when {
            maxWidth < 380.dp -> 104.dp
            maxWidth < 700.dp -> 152.dp
            else -> 224.dp
        }
        val discSize = artworkSize + 34.dp
        val textMaxWidth = minOf(
            maxWidth * if (isExpanded) 0.62f else 0.66f,
            maxWidth - discSize - 44.dp,
        ).coerceAtLeast(120.dp)

        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 52.dp, y = (-52).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.055f))
                .align(Alignment.TopEnd),
        )

        Box(
            modifier = Modifier
                .size(discSize)
                .align(Alignment.CenterEnd)
                .padding(end = if (isExpanded) 30.dp else 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Box(
                modifier = Modifier
                    .size(discSize * 0.74f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(discSize * 0.52f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .align(Alignment.CenterStart)
                    .offset(x = (-12).dp)
                    .clip(ShapeCache.smooth20)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl.isNotEmpty()) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = null,
                        blurHash = item.blurHashes.primary,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Tabler.Outline.Music,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = textMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, top = 22.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
                color = titleColor.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.name,
                style = if (isExpanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isExpanded) 38.sp else 29.sp,
            )
            val artist = item.albumArtist
                ?: item.artistItems.firstOrNull()?.name
                ?: item.album
            if (!artist.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .focusRequester(playFocusRequester)
                    .focusProperties {
                        down = downFocusRequester
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.music_play))
            }
        }
    }
}
