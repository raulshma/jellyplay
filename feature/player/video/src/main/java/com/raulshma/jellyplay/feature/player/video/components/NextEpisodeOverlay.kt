package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun NextEpisodeOverlay(
    isVisible: Boolean,
    episodeTitle: String,
    seriesName: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    thumbnailUrl: String?,
    countdownSeconds: Int = 10,
    onPlayNext: () -> Unit,
    onCancel: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var countdown by remember(isVisible) { mutableIntStateOf(countdownSeconds) }
    var dismissed by remember(isVisible) { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        dismissed = false
        countdown = countdownSeconds
    }

    LaunchedEffect(isVisible, countdown, dismissed, isPlaying) {
        if (isVisible && !dismissed && isPlaying) {
            if (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            } else {
                onPlayNext()
            }
        }
    }

    val show = isVisible && !dismissed

    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.width(280.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Column {
                if (!thumbnailUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        
                        // Play button overlay
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable(onClick = onPlayNext),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Next",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        // Close button in top-right
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable(onClick = {
                                    dismissed = true
                                    onCancel()
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Next Episode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = episodeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!seriesName.isNullOrBlank() || (seasonNumber != null && episodeNumber != null)) {
                        Text(
                            text = buildString {
                                if (seriesName != null) append(seriesName)
                                if (seasonNumber != null && episodeNumber != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append("S${seasonNumber}E${episodeNumber}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (show && countdown > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { 1f - (countdown.toFloat() / countdownSeconds.toFloat()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.1f),
                        )
                    }
                }
            }
        }
    }
}
