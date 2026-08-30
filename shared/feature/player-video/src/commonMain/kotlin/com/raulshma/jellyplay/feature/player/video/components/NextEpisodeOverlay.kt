package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_next_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_play_next
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_turn_on_autoplay




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
import org.jetbrains.compose.resources.stringResource
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
    autoplayEnabled: Boolean = true,
    onPlayNext: () -> Unit,
    onCancel: () -> Unit,
    onToggleAutoplay: (() -> Unit)? = null,
    isPlaying: Boolean,
    // Pauses the auto-play countdown while a settings sheet is open or the player
    // is locked, so the user isn't rushed out of a menu (or surprised by an
    // auto-advance they couldn't cancel on a locked screen).
    pauseCountdown: Boolean = false,
    // True while the triggered next-episode load is in flight and unsettled:
    // the play button shows progress and stops accepting clicks, so rapid
    // re-taps can't stack duplicate loads (#146).
    isLoading: Boolean = false,
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

    LaunchedEffect(isVisible, countdown, dismissed, isPlaying, autoplayEnabled, pauseCountdown, isLoading) {
        if (isVisible && !dismissed && isPlaying && autoplayEnabled && !pauseCountdown && !isLoading) {
            if (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            } else {
                onPlayNext()
            }
        }
    }

    val show = isVisible && !dismissed
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current
    val slideEnter: androidx.compose.animation.EnterTransition =
        if (reducedMotion) androidx.compose.animation.EnterTransition.None
        else slideInVertically(animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(), initialOffsetY = { it })
    val slideExit: androidx.compose.animation.ExitTransition =
        if (reducedMotion) androidx.compose.animation.ExitTransition.None
        else slideOutVertically(animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(), targetOffsetY = { it })

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideEnter,
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideExit,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = ShapeCache.smooth24,
            color = playerScrimColor().copy(alpha = 0.8f),
            border = BorderStroke(1.dp, playerOnScrim().copy(alpha = 0.1f))
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
                                            playerScrimColor().copy(alpha = 0.4f),
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
                                .clickable(
                                    enabled = !isLoading,
                                    onClick = {
                                        // Dismiss before invoking so the countdown
                                        // LaunchedEffect stops (it would otherwise
                                        // re-fire onPlayNext) and the card gives
                                        // instant visual feedback. Mirrors the cancel
                                        // button below.
                                        dismissed = true
                                        onPlayNext()
                                    },
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = playerOnScrim(),
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Tabler.Outline.PlayerPlay,
                                    contentDescription = stringResource(Res.string.player_video_play_next),
                                    tint = playerOnScrim(),
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }

                        val closeFocusState = rememberTvFocusState(focusedScale = 1.2f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(playerOnScrim().copy(alpha = 0.12f))
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
                                contentDescription = stringResource(Res.string.player_video_cancel),
                                tint = playerOnScrim(),
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
                            text = stringResource(Res.string.player_video_next_episode),
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
                        color = playerOnScrim(),
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
                            color = playerOnScrim().copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (show && autoplayEnabled && countdown > 0) {
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
                                color = playerOnScrim().copy(alpha = 0.85f),
                            )
                        }
                    }

                    if (show && !autoplayEnabled && onToggleAutoplay != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val toggleFocusState = rememberTvFocusState()
                        androidx.compose.material3.FilledTonalButton(
                            onClick = onToggleAutoplay,
                            modifier = Modifier
                                .then(toggleFocusState.focusModifier)
                                .tvFocusIndicator(toggleFocusState, ShapeCache.smoothPill),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.player_video_turn_on_autoplay),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
