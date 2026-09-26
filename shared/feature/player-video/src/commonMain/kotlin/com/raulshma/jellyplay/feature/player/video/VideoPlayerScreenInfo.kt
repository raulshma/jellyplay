package com.raulshma.jellyplay.feature.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.feature.player.video.components.CastIndicatorOverlay
import com.raulshma.jellyplay.feature.player.video.components.VideoStatsOverlay
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_a_set
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_active
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_cleared
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_enabled
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_auto

// ── Section host: status / info tier ─────────────────────────────────────
// Owns the "Stats for Nerds" overlay, the transient badge family (auto
// aspect-ratio, pinch-zoom, A/B repeat — all anchored top-center), the cast
// indicator, and both snackbar hosts (bottom toast host + the top resume
// chip). Moved verbatim from VideoPlayerScreen.kt as a composition-only
// section-host split: declarations are unchanged except `private` →
// `internal` where the root file calls the symbol. See the section-host map
// on VideoPlayerScreen.kt.

/** How long the AutoAspectRatio badge is shown before auto-dismissing. */
private const val ASPECT_BADGE_DURATION_MS = 5_000L

/** How long the zoom badge is shown before auto-dismissing. */
private const val ZOOM_BADGE_DURATION_MS = 2_000L
/** How long A/B repeat confirmation badges (point captured, loop active, cleared) are shown. */
private const val AB_REPEAT_BADGE_DURATION_MS = 3_000L
/** How long A/B repeat step-guidance badges (enable hint, A-set hint) linger — they instruct the next action. */
private const val AB_REPEAT_BADGE_HINT_DURATION_MS = 4_000L

// ── Bottom-control clearances for overlays anchored above the controls ───
/** Snackbar offset above the bottom controls (landscape/TV layout). */
private const val SNACKBAR_BOTTOM_CLEARANCE_DP = 200
/**
 * Resume chip offset below the top bar. The host is additionally offset by
 * `WindowInsets.statusBars` (see the call site), so this covers only the top
 * bar's own height — the 40dp back-button row + 8dp vertical scrim padding
 * (top+bottom) — plus a small gap so the chip clears the title row.
 */
private const val RESUME_CHIP_TOP_CLEARANCE_DP = 60

/** Stats/badges/snackbar tier: "Stats for Nerds", aspect/AB-repeat/zoom badges, cast indicator, both snackbar hosts. */
@Composable
internal fun BoxScope.PlayerStatusOverlayTier(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    durationMs: Long,
    playbackSpeed: Float,
    isPlaying: Boolean,
    decoderMode: DecoderMode,
    engine: MediaEngine?,
    isCastConnected: Boolean,
    isCastConnecting: Boolean,
    snackbarHostState: SnackbarHostState,
    resumeChipHostState: SnackbarHostState,
    detectedAspectRatio: AspectRatio?,
    aspectRatio: AspectRatio,
    videoZoom: Float,
) {
    if (uiState.uiPrefs.showVideoStats) {
        VideoStatsOverlay(
            statsFlow = viewModel.videoStats,
            currentPositionFlow = viewModel.currentPositionMs,
            // the ranges readout row's source, collected at
            // this leaf like statsFlow.
            bufferedRangesFlow = viewModel.bufferedRanges,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            isPlaying = isPlaying,
            playbackState = when {
                uiState.playerError != null -> "Error"
                !isPlaying -> "Paused"
                else -> "Playing"
            },
            playMethod = uiState.media.playMethod,
            streamingQuality = uiState.preferredPlayerType.name,
            playerType = uiState.preferredPlayerType.name,
            decoderMode = decoderMode.displayName,
            transcodeReasons = rememberFormattedTranscodeReasons(uiState.media.transcodeReasons),
            // Engines expose a real audio session id (ExoPlayer: live
            // session; mpv: generated id; VLC: 0 — capabilities gate the
            // row). Read the collected engine so a swap refreshes it.
            audioSessionId = engine?.audioSessionId ?: 0,
            // Drop below the CastIndicator when both are visible so
            // they don't stack on the same (60dp, 16dp) anchor.
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = if (isCastConnected || isCastConnecting) 92.dp else 60.dp)
                .width(280.dp),
        )
    }

    AutoAspectRatioBadge(
        detectedAspectRatio = detectedAspectRatio,
        aspectRatio = aspectRatio,
    )
    AbRepeatBadge(events = viewModel.abRepeat.events)

    ZoomBadge(videoZoom = videoZoom)

    if (isCastConnected || isCastConnecting) {
        CastIndicatorOverlay(
            isConnecting = isCastConnecting,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 60.dp, start = 16.dp),
        )
    }

    com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = SNACKBAR_BOTTOM_CLEARANCE_DP.dp),
    )

    // "Resumed from where you left off" chip — anchored under the top bar.
    com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
        hostState = resumeChipHostState,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = RESUME_CHIP_TOP_CLEARANCE_DP.dp),
    )
}

/**
 * Transient top-center pill badge that fades in when [show] turns true. The
 * show/hide timing is owned by each caller's `LaunchedEffect` (see
 * [AutoAspectRatioBadge] / [ZoomBadge]); this composable only renders the pill.
 * Shared by the Auto-aspect-ratio and zoom badges, which were previously two
 * ~40-line near-identical composables.
 */
@Composable
private fun BoxScope.PlayerBadge(
    show: Boolean,
    text: String,
    topPadding: Dp,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topPadding),
    ) {
        Surface(
            shape = ShapeCache.smoothPill,
            color = playerOnScrim().copy(alpha = 0.12f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

/**
 * Transient badge shown when the player auto-selects a detected aspect ratio
 * (e.g. cropping letterboxed content to fill the screen). Auto-dismisses after
 * [ASPECT_BADGE_DURATION_MS].
 */
@Composable
private fun BoxScope.AutoAspectRatioBadge(
    detectedAspectRatio: AspectRatio?,
    aspectRatio: AspectRatio,
) {
    var showBadge by remember { mutableStateOf(false) }
    LaunchedEffect(detectedAspectRatio, aspectRatio) {
        if (detectedAspectRatio != null && detectedAspectRatio != AspectRatio.FIT && aspectRatio == AspectRatio.AUTO) {
            showBadge = true
            delay(ASPECT_BADGE_DURATION_MS)
            showBadge = false
        } else {
            showBadge = false
        }
    }

    PlayerBadge(
        show = showBadge,
        text = stringResource(Res.string.player_video_aspect_auto, detectedAspectRatio?.displayName ?: ""),
        topPadding = 60.dp,
    )
}

/**
 * Transient badge shown after a pinch-to-zoom gesture. Surfaces the current zoom level
 * (which is otherwise invisible) and — at the default 1× — hints that double-tapping the
 * centre resets it. Auto-dismisses like [AutoAspectRatioBadge].
 */
@Composable
private fun BoxScope.ZoomBadge(videoZoom: Float) {
    var showBadge by remember { mutableStateOf(false) }
    LaunchedEffect(videoZoom) {
        if (videoZoom != 1f) {
            showBadge = true
            delay(ZOOM_BADGE_DURATION_MS)
            showBadge = false
        } else {
            showBadge = false
        }
    }

    // Format once per distinct zoom value rather than per badge recompose.
    val zoomText = remember(videoZoom) { "${formatFixed(videoZoom.toDouble(), 1)}×" }

    PlayerBadge(
        show = showBadge,
        text = zoomText,
        topPadding = 100.dp,
    )
}

/**
 * Transient badge walking the user through the A/B repeat workflow, driven by
 * the controller's one-shot events: enabling hints at the next step ("seek,
 * then Set A Point"), each captured point confirms with its timestamp, and the
 * completed loop announces its window. Auto-dismisses like
 * [AutoAspectRatioBadge]; step-guidance messages linger a beat longer.
 */
@Composable
private fun BoxScope.AbRepeatBadge(events: SharedFlow<AbRepeatEvent>) {
    var event by remember { mutableStateOf<AbRepeatEvent?>(null) }
    var showBadge by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        events.collect { e ->
            event = e
            showBadge = true
            delay(
                if (e is AbRepeatEvent.Enabled || e is AbRepeatEvent.PointASet) {
                    AB_REPEAT_BADGE_HINT_DURATION_MS
                } else {
                    AB_REPEAT_BADGE_DURATION_MS
                }
            )
            showBadge = false
        }
    }

    val text = when (val e = event) {
        AbRepeatEvent.Enabled -> stringResource(Res.string.player_video_ab_repeat_badge_enabled)
        is AbRepeatEvent.PointASet -> stringResource(
            Res.string.player_video_ab_repeat_badge_a_set,
            formatDuration(e.aMs),
        )
        is AbRepeatEvent.PointBSet -> stringResource(
            Res.string.player_video_ab_repeat_badge_active,
            formatDuration(e.aMs),
            formatDuration(e.bMs),
        )
        AbRepeatEvent.Cleared -> stringResource(Res.string.player_video_ab_repeat_badge_cleared)
        null -> null
    }

    if (text != null) {
        PlayerBadge(
            show = showBadge,
            text = text,
            topPadding = 60.dp,
        )
    }
}
