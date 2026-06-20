package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.image.MediaImage
import coil3.size.Size as CoilSize

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    focusRequester: FocusRequester? = null,
) {
    var countdown by remember(isVisible) { mutableIntStateOf(countdownSeconds) }
    var dismissed by remember(isVisible) { mutableStateOf(false) }
    val isTv = LocalTvMode.current
    val localFocusRequester = remember { FocusRequester() }
    val tvPlayNextFocusRequester = focusRequester ?: localFocusRequester

    LaunchedEffect(isVisible) {
        dismissed = false
        countdown = countdownSeconds
    }

    LaunchedEffect(isVisible, isTv) {
        if (isVisible && isTv) {
            kotlinx.coroutines.delay(300)
            tvPlayNextFocusRequester.tryRequestFocus("tv_next_episode")
        }
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
        enter = fadeIn(tween(150, easing = AlphaEasing)) + slideInVertically(animationSpec = tween(400, easing = FancyTransitionEasing), initialOffsetY = { it }),
        exit = fadeOut(tween(200, easing = AlphaEasing)) + slideOutVertically(animationSpec = tween(400, easing = FancyTransitionEasing), targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = ShapeCache.smooth24,
            color = Color.Black.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column {
                if (!thumbnailUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaImage(
                            url = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            size = CoilSize(480, 270),
                            placeholderIcon = Tabler.Outline.Photo,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(ShapeCache.smooth24),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.4f),
                                        )
                                    )
                                )
                        )

                        val playFocusState = rememberTvFocusState(focusedScale = 1.12f)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                .ifElse(isTv, Modifier.focusRequester(tvPlayNextFocusRequester))
                                .then(playFocusState.focusModifier)
                                .tvFocusIndicator(playFocusState, CircleShape)
                                .clickable(onClick = onPlayNext),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.PlayerPlay,
                                contentDescription = "Play Next",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }

                        val closeFocusState = rememberTvFocusState(focusedScale = 1.2f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .then(closeFocusState.focusModifier)
                                .tvFocusIndicator(closeFocusState, CircleShape)
                                .clickable(onClick = {
                                    dismissed = true
                                    onCancel()
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.X,
                                contentDescription = "Cancel",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = "Next Episode",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = episodeTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!seriesName.isNullOrBlank() || (seasonNumber != null && episodeNumber != null)) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                if (seriesName != null) append(seriesName)
                                if (seasonNumber != null && episodeNumber != null) {
                                    if (isNotEmpty()) append(" \u00B7 ")
                                    append("S${seasonNumber}E${episodeNumber}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (show && countdown > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            JellyPlayLinearProgressIndicator(
                                progress = { 1f - (countdown.toFloat() / countdownSeconds.toFloat()) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${countdown}s",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
