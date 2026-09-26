package com.raulshma.jellyplay.feature.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PinLockOverlay
import com.raulshma.jellyplay.feature.player.video.components.SegmentSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.SlideToUnlockOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDelayOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_skipped_segment

// ── Section host: overlay tiers over the video surface ───────────────────
// Owns the center overlays (gesture trickplay, intro/segment skip, skipped
// notice, up-next, HDR badge, buffering spinner), the full-screen lock
// overlays (PIN pad / slide-to-unlock), the seek-scrub trickplay thumbnail,
// the transparent VLC-style subtitle-delay overlay, and the shared trickplay
// thumbnail collector feeding both trickplay surfaces. Moved verbatim from
// VideoPlayerScreen.kt as a composition-only section-host split: declarations
// are unchanged except `private` → `internal` where the root file calls the
// symbol. See the section-host map on VideoPlayerScreen.kt.

/** Trickplay thumbnail offset above the bottom controls. */
private const val TRICKPLAY_THUMB_BOTTOM_CLEARANCE_DP = 120

/** Center/anchored overlays: gesture trickplay, intro/segment skip, skipped notice, up-next, HDR badge, buffering spinner. */
@Composable
internal fun BoxScope.PlayerCenterOverlayTier(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    trickplayOnSeekGesture: Boolean,
    gestureTrickplayVisible: Boolean,
    gestureTrickplayBitmap: PlatformBitmap?,
    gestureSeekPositionMs: Long,
    gestureDeltaMs: Long,
    durationMs: Long,
    isCinemaIntroVisible: Boolean,
    tvCinemaIntroFocusRequester: FocusRequester,
    performConfirmHaptic: () -> Unit,
    activeSegment: MediaSegment?,
    activeSegmentBehavior: SegmentBehavior?,
    shouldShowUpNext: Boolean,
    isInPipMode: Boolean,
    tvSkipSegmentFocusRequester: FocusRequester,
    nextEpisode: MediaItem?,
    nextEpisodeImageUrl: String?,
    isNextEpisodeLoading: Boolean,
    tvNextEpisodeFocusRequester: FocusRequester,
    isPlaying: Boolean,
    isSheetOpen: Boolean,
    isScreenLocked: Boolean,
    playbackIntended: Boolean,
) {
    // Trickplay overlay for seek gestures
    AnimatedVisibility(
        visible = trickplayOnSeekGesture && gestureTrickplayVisible,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        TrickplayOverlay(
            bitmap = gestureTrickplayBitmap,
            positionMs = gestureSeekPositionMs,
            deltaMs = gestureDeltaMs,
            durationMs = durationMs,
        )
    }

    if (isCinemaIntroVisible) {
        IntroSkipOverlay(
            isVisible = true,
            onSkip = {
                viewModel.onEvent(VideoPlayerUiEvent.SkipIntro)
                performConfirmHaptic()
            },
            focusRequester = tvCinemaIntroFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 40.dp),
        )
    }

    if (activeSegment != null && activeSegmentBehavior == SegmentBehavior.SHOW_BUTTON && !isInPipMode) {
        val hideForUpNext = activeSegment.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO && shouldShowUpNext
        if (!hideForUpNext) {
            SegmentSkipOverlay(
                isVisible = true,
                segmentType = activeSegment.type,
                onSkip = {
                    viewModel.onEvent(VideoPlayerUiEvent.SkipSegment(activeSegment))
                    performConfirmHaptic()
                },
                focusRequester = tvSkipSegmentFocusRequester,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 40.dp),
            )
        }
    }

    // "Skipped …" confirmation: raised by the VM when an
    // auto-skip fires or a skip-on-seek clamp lands; the VM owns the
    // ~2.5 s auto-clear, this is a pure AnimatedVisibility consumer.
    // Co-located with the SegmentSkipOverlay placement so the two
    // never overlap it (bottom-center, above the control chrome).
    val skippedNotice = uiState.skippedSegmentNotice
    AnimatedVisibility(
        visible = skippedNotice != null,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(300, easing = AlphaEasing)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 100.dp),
    ) {
        if (skippedNotice != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(ShapeCache.smoothPill)
                    .background(playerScrimColor().copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.player_video_skipped_segment, skippedNotice.segmentType.localizedDisplayName()),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    if (nextEpisode != null) {
        NextEpisodeOverlay(
            isVisible = shouldShowUpNext,
            episodeTitle = nextEpisode.name,
            seriesName = nextEpisode.seriesName,
            seasonNumber = nextEpisode.seasonNumber,
            episodeNumber = nextEpisode.episodeNumber,
            thumbnailUrl = nextEpisodeImageUrl,
            countdownSeconds = uiState.autoplay.autoPlayCountdownSec,
            autoplayEnabled = uiState.autoplay.videoAutoplayNext,
            onPlayNext = { viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode) },
            onCancel = { viewModel.onEvent(VideoPlayerUiEvent.CancelAutoplay) },
            onToggleAutoplay = { viewModel.onEvent(VideoPlayerUiEvent.SetVideoAutoplayNext(!uiState.autoplay.videoAutoplayNext)) },
            isPlaying = isPlaying,
            pauseCountdown = isSheetOpen || isScreenLocked,
            isLoading = isNextEpisodeLoading,
            focusRequester = tvNextEpisodeFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 40.dp, end = 40.dp),
        )
    }

    HdrBadge(
        hdrType = uiState.hdrType,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 16.dp, end = 16.dp),
    )

    if (uiState.isBuffering && uiState.playerError == null && !isPlaying && playbackIntended) {
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            JellyPlayLoadingIndicator(color = playerOnScrim())
        }
    }
}

/** Full-screen lock overlays: PIN pad (when configured) or slide-to-unlock; unlock re-reveals the controls. */
@Composable
internal fun PlayerLockOverlayTier(
    usePinForPlayerLock: Boolean,
    hasPin: Boolean,
    viewModel: VideoPlayerViewModel,
    onUnlock: () -> Unit,
) {
    val usePin = usePinForPlayerLock && hasPin
    if (usePin) {
        PinLockOverlay(
            visible = true,
            onDismiss = { },
            onUnlock = onUnlock,
            verifyPin = { pin -> viewModel.verifyPlayerLockPin(pin) },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        SlideToUnlockOverlay(
            visible = true,
            onDismiss = { },
            onUnlock = onUnlock,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Transparent VLC-style subtitle-delay overlay (empty-space taps pass through to the gesture layer). */
@Composable
internal fun PlayerSubtitleDelayOverlay(
    currentDelayMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    SubtitleDelayOverlay(
        currentDelayMs = currentDelayMs,
        onChange = onChange,
        onDismiss = onDismiss,
    )
}

/** Seek-scrub trickplay thumbnail anchored above the bottom controls (non-TV, controls visible, active scrub). */
@Composable
internal fun BoxScope.PlayerSeekScrubTrickplayOverlay(
    isTv: Boolean,
    trickplayEnabled: Boolean,
    showControls: Boolean,
    isSeeking: Boolean,
    bitmap: PlatformBitmap?,
    positionMs: Long,
    durationMs: Long,
) {
    AnimatedVisibility(
        visible = !isTv && trickplayEnabled && showControls && isSeeking,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TRICKPLAY_THUMB_BOTTOM_CLEARANCE_DP.dp),
    ) {
        TrickplayOverlay(
            bitmap = bitmap,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }
}

/**
 * The shared collector behind BOTH trickplay thumbnail feeds (A6): the
 * seek-bar / D-pad scrub (seek overlay bitmap + the TV mirror, which renders
 * no gesture overlay) and the gesture-swipe preview. The two former inline
 * `LaunchedEffect(Unit)` collectors differed only in the position source, the
 * gating flag and the target write — the combined
 * `Triple(position, gate, info)` snapshot read, the `conflate()` +
 * `distinctUntilChanged()` pipeline that suppresses no-op emissions (neither
 * the position nor the gating flags changed), and the gate check live here
 * exactly once. The state reads stay inside [snapshotFlow]'s block via the
 * getter lambdas, so snapshot tracking is byte-identical to the inline form;
 * each feed's clear-on-end LaunchedEffect stays at its call site (their
 * clear semantics genuinely differ).
 */
internal suspend fun collectTrickplayThumbnails(
    positionMs: () -> Long,
    gate: () -> Boolean,
    trickplayInfo: () -> TrickplayInfo?,
    onFetch: suspend (positionMs: Long, trickplayInfo: TrickplayInfo?) -> Unit,
) {
    snapshotFlow { Triple(positionMs(), gate(), trickplayInfo()) }
        .conflate()
        .distinctUntilChanged()
        .collect { (pos, shouldFetch, info) ->
            if (shouldFetch) {
                onFetch(pos, info)
            }
        }
}
