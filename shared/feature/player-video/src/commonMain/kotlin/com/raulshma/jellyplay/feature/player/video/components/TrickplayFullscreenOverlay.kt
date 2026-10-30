package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.feature.player.video.PlatformBitmap
import com.raulshma.jellyplay.feature.player.video.asPlatformImageBitmap

/**
 * The FULLSCREEN trickplay surface (#7): the current sprite tile scaled over
 * the whole player surface ("fill"), over a dimmed background, with the
 * position/duration caption at the bottom. The tile is the same
 * `TrickplayManager` lookup the bubble renders — only the presentation
 * differs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrickplayFullscreenOverlay(
    bitmap: PlatformBitmap?,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
    ) {
        val imageBitmap = remember(bitmap) { bitmap?.asPlatformImageBitmap() }
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = formatDurationMs(positionMs, paddedMinutes = true),
                color = playerOnScrim().copy(alpha = 0.7f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(playerScrimColor().copy(alpha = 0.35f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDurationMs(positionMs, paddedMinutes = true),
                    style = MaterialTheme.typography.titleMedium,
                    color = playerOnScrim(),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                if (durationMs > 0) {
                    Text(
                        text = " / " + formatDurationMs(durationMs, paddedMinutes = true),
                        style = MaterialTheme.typography.titleSmall,
                        color = playerOnScrim().copy(alpha = 0.65f),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (durationMs > 0) {
                val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                JellyPlayLinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
