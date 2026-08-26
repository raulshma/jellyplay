package com.raulshma.jellyplay.feature.player.video.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_trickplay_thumbnail

import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrickplayOverlay(
    bitmap: Bitmap?,
    positionMs: Long,
    deltaMs: Long = 0L,
    durationMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val isSeeking = deltaMs != 0L
    val isForward = deltaMs > 0

    Surface(
        modifier = modifier
            .width(240.dp)
            .shadow(20.dp, shape = ShapeCache.smooth20)
            .border(
                width = 0.5.dp,
                color = playerOnScrim().copy(alpha = 0.1f),
                shape = ShapeCache.smooth20,
            ),
        shape = ShapeCache.smooth20,
        color = playerScrimColor().copy(alpha = 0.6f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
                    .background(playerScrimColor()),
                contentAlignment = Alignment.Center,
            ) {
                val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
                val scrimBase = playerScrimColor()
                val thumbnailOverlayGradient = remember(scrimBase) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            scrimBase.copy(alpha = 0.75f),
                        ),
                    )
                }
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(Res.string.player_video_trickplay_thumbnail),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(135.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(thumbnailOverlayGradient),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(135.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        playerOnScrim().copy(alpha = 0.05f),
                                        playerOnScrim().copy(alpha = 0.02f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            color = playerOnScrim().copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.sp,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(playerScrimColor().copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isSeeking) {
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (isForward) Tabler.Outline.PlayerTrackNext else Tabler.Outline.PlayerTrackPrev,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = buildString {
                                    append(if (isForward) "+" else "-")
                                    append(formatTime(abs(deltaMs)))
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                Text(
                    text = formatTime(positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = playerOnScrim().copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                )

                if (durationMs > 0) {
                    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    JellyPlayLinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    // String templates avoid the per-call Formatter allocation — this runs
    // per-frame during trickplay scrubbing.
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
