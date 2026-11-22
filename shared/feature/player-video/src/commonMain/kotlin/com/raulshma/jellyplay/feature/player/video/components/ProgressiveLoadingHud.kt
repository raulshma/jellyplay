package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.LoadPhase
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_buffer_progress
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_connecting
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_failed
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_fetching_metadata
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_initializing_engine
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_preparing
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_priming_buffer
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_resolving_stream
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_stage_resolving_stream_slow
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_warning_slow_connection
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_warning_slow_server
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_load_warning_transcoding
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Progressive loading HUD replacing the uninformative black void during initial
 * media startup (`isInitializing`).
 *
 * Surfaces fine-grained [LoadPhase] stages, live buffer priming progression,
 * slow connection warnings, and an abort action.
 */
@Composable
fun ProgressiveLoadingHud(
    loadPhase: LoadPhase,
    title: String,
    subtitle: String? = null,
    showTelemetry: Boolean = true,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val cancelFocusRequester = remember { FocusRequester() }
    val cancelTvFocusState = rememberTvFocusState()

    LaunchedEffect(isTv) {
        if (isTv) {
            cancelFocusRequester.tryRequestFocus("loading_cancel")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            JellyPlayLoadingIndicator(color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(20.dp))

            val stageTitle = when (loadPhase) {
                is LoadPhase.Loading.Preparing ->
                    stringResource(Res.string.player_video_load_stage_preparing)
                is LoadPhase.Loading.FetchingMetadata ->
                    stringResource(Res.string.player_video_load_stage_fetching_metadata)
                is LoadPhase.Loading.ResolvingStream -> {
                    if (loadPhase.stageElapsedMs > 2500L) {
                        stringResource(Res.string.player_video_load_stage_resolving_stream_slow)
                    } else {
                        stringResource(Res.string.player_video_load_stage_resolving_stream)
                    }
                }
                is LoadPhase.Loading.InitializingEngine ->
                    stringResource(
                        Res.string.player_video_load_stage_initializing_engine,
                        loadPhase.playerType.displayName,
                    )
                is LoadPhase.Loading.PrimingBuffer ->
                    stringResource(Res.string.player_video_load_stage_priming_buffer)
                is LoadPhase.Failed ->
                    stringResource(Res.string.player_video_load_stage_failed, loadPhase.message)
                else -> stringResource(Res.string.player_video_load_stage_connecting)
            }

            Text(
                text = stageTitle,
                style = MaterialTheme.typography.titleSmall,
                color = playerOnScrim(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            if (showTelemetry && loadPhase is LoadPhase.Loading.PrimingBuffer) {
                Spacer(Modifier.height(8.dp))
                val bufferedSec = (loadPhase.bufferedDurationMs / 100.0).roundToInt() / 10.0
                val targetSec = (loadPhase.targetBufferDurationMs / 100.0).roundToInt() / 10.0
                val progressFraction = if (loadPhase.targetBufferDurationMs > 0) {
                    (loadPhase.bufferedDurationMs.toFloat() / loadPhase.targetBufferDurationMs).coerceIn(0f, 1f)
                } else 0f

                if (loadPhase.targetBufferDurationMs > 0) {
                    Text(
                        text = stringResource(
                            Res.string.player_video_load_buffer_progress,
                            "$bufferedSec",
                            "$targetSec",
                            "${(progressFraction * 100).toInt()}%",
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    JellyPlayLinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .width(180.dp)
                            .height(4.dp)
                            .clip(ShapeCache.smoothPill),
                    )
                }
            }

            // Slow-load warnings
            if (showTelemetry && loadPhase is LoadPhase.Loading && loadPhase.stageElapsedMs > 3000L) {
                val slowNotice = when (loadPhase) {
                    is LoadPhase.Loading.ResolvingStream ->
                        stringResource(Res.string.player_video_load_warning_transcoding)
                    is LoadPhase.Loading.PrimingBuffer ->
                        stringResource(Res.string.player_video_load_warning_slow_connection)
                    is LoadPhase.Loading.FetchingMetadata ->
                        stringResource(Res.string.player_video_load_warning_slow_server)
                    else -> null
                }
                if (slowNotice != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = slowNotice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Surface(
                shape = ShapeCache.smoothPill,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .clip(ShapeCache.smoothPill)
                    .ifElse(
                        isTv,
                        Modifier
                            .focusRequester(cancelFocusRequester)
                            .then(cancelTvFocusState.focusModifier)
                            .tvFocusIndicator(cancelTvFocusState, shape = ShapeCache.smoothPill),
                    )
                    .clickable(onClick = onCancel),
            ) {
                Text(
                    text = stringResource(Res.string.player_video_cancel),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}
