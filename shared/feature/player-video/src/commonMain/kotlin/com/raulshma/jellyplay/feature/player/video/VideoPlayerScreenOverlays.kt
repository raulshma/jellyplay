package com.raulshma.jellyplay.feature.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.TrickplayMode
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PinLockOverlay
import com.raulshma.jellyplay.feature.player.video.components.SegmentSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.SlideToUnlockOverlay
import com.raulshma.jellyplay.feature.player.video.components.StillWatchingOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDelayOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayFilmstripOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayFullscreenOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_skipped_segment

// ── Section host: overlay tiers over the video surface ───────────────────
// Owns the center overlays (gesture trickplay, intro/segment skip, skipped
// notice, up-next, buffering spinner), the full-screen lock
// overlays (PIN pad / slide-to-unlock), the seek-scrub trickplay thumbnail,
// the transparent VLC-style subtitle-delay overlay, and the shared trickplay
// thumbnail collector feeding both trickplay surfaces. Moved verbatim from
// VideoPlayerScreen.kt as a composition-only section-host split: declarations
// are unchanged except `private` → `internal` where the root file calls the
// symbol. See the section-host map on VideoPlayerScreen.kt.

/** Trickplay thumbnail offset above the bottom controls (the BUBBLE anchor). */
private const val TRICKPLAY_THUMB_BOTTOM_CLEARANCE_DP = 120

/** The FILMSTRIP's anchor: at the seek-bar line, below the bubble's float. */
private const val TRICKPLAY_FILMSTRIP_BOTTOM_CLEARANCE_DP = 88

/**
 * Input bundle for [PlayerCenterOverlayTier] (the [com.raulshma.jellyplay.feature.player.video.components.PlayerEffectsControls]
 * idiom): what the center overlays read — the gesture-trickplay and
 * seek feeds, the intro/segment-skip/up-next family with their TV focus
 * anchors, and the playback-context flags — riding one `@Immutable` carrier.
 * The call wall remembers it on exactly these fields, so the bundle instance
 * is equal across recompositions and the tier keeps skipping until a value it
 * actually renders flips.
 */
@Immutable
internal data class CenterOverlayInputs(
    val trickplayOnSeekGesture: Boolean,
    /** The trickplay presentation mode (#7) the gesture preview renders. */
    val trickplayMode: TrickplayMode,
    /** The sprite manifest the filmstrip/fullscreen surfaces need (null degrades to the bubble). */
    val trickplayInfo: TrickplayInfo?,
    val gestureTrickplayVisible: Boolean,
    val gestureTrickplayBitmap: PlatformBitmap?,
    /**
     * The gesture-seek drag feeds — FLOWS, not values: they emit per pointer
     * frame, so the call wall's remember must not key on them, and only the
     * trickplay leaf below collects them (the ANR class PlayerControls'
     * placement comment warns about).
     */
    val gestureSeekPositionMs: StateFlow<Long>,
    val gestureDeltaMs: StateFlow<Long>,
    val durationMs: Long,
    val isCinemaIntroVisible: Boolean,
    val tvCinemaIntroFocusRequester: FocusRequester,
    val activeSegment: MediaSegment?,
    val isSkipSegmentVisible: Boolean,
    val skipPromptProgress: State<Float>?,
    val shouldShowUpNext: Boolean,
    val tvSkipSegmentFocusRequester: FocusRequester,
    /**
     * The Up Next card — the Up Next controller's projection, already resolved
     * with the ADVANCE's precedence (queue head beats the episode sibling, null
     * under SyncPlay). The overlay never re-decides precedence from raw state.
     */
    val upNextCard: UpNextCard?,
    /** The card's artwork: the queue row's image, or the episode item's resolved url. */
    val upNextImageUrl: String?,
    val isNextEpisodeLoading: Boolean,
    val tvNextEpisodeFocusRequester: FocusRequester,
    val isPlaying: Boolean,
    val isSheetOpen: Boolean,
    val isScreenLocked: Boolean,
    val playbackIntended: Boolean,
    /** The "Still watching?" confirm state; null = hidden (feature 1.3). */
    val stillWatchingPrompt: StillWatchingPromptState?,
)

/** Center/anchored overlays: gesture trickplay, intro/segment skip, skipped notice, up-next, buffering spinner. */
@Composable
internal fun BoxScope.PlayerCenterOverlayTier(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    inputs: CenterOverlayInputs,
    performConfirmHaptic: () -> Unit,
) {
    // Trickplay overlay for seek gestures. Drag-seek shows the preview in
    // EVERY presentation mode (#7) — the mode only changes WHAT renders, the
    // center anchor stays except the full-surface mode filling the box.
    AnimatedVisibility(
        visible = inputs.trickplayOnSeekGesture && inputs.gestureTrickplayVisible,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = when (
            trickplayAnchor(inputs.trickplayMode, inputs.trickplayInfo != null)
        ) {
            TrickplayAnchor.FULL_SURFACE ->
                Modifier.align(Alignment.Center).fillMaxSize()
            else -> Modifier.align(Alignment.Center)
        },
    ) {
        // Collected HERE — inside the visible overlay's own recompose scope,
        // not the tier's: the feeds emit per drag frame, and only the
        // trickplay surface renders them.
        val gestureSeekPositionMs by inputs.gestureSeekPositionMs.collectAsStateWithLifecycle()
        val gestureDeltaMs by inputs.gestureDeltaMs.collectAsStateWithLifecycle()
        val gestureBitmap = inputs.gestureTrickplayBitmap
        when (
            val presentation = trickplayPresentation(
                mode = inputs.trickplayMode,
                positionMs = gestureSeekPositionMs,
                intervalMs = inputs.trickplayInfo?.interval ?: 0,
                thumbnailCount = inputs.trickplayInfo?.thumbnailCount ?: 0,
            )
        ) {
            TrickplayPresentation.Bubble -> TrickplayOverlay(
                bitmap = gestureBitmap,
                positionMs = gestureSeekPositionMs,
                deltaMs = gestureDeltaMs,
                durationMs = inputs.durationMs,
            )
            is TrickplayPresentation.Filmstrip -> TrickplayFilmstripOverlay(
                tileIndices = presentation.tileIndices,
                intervalMs = inputs.trickplayInfo?.interval ?: 0,
                tileAspect = trickplayTileAspect(inputs.trickplayInfo),
                positionMs = gestureSeekPositionMs,
                loadTile = { position ->
                    viewModel.loadTrickplayThumbnail(position) as? PlatformBitmap
                },
            )
            TrickplayPresentation.Fullscreen -> TrickplayFullscreenOverlay(
                bitmap = gestureBitmap,
                positionMs = gestureSeekPositionMs,
                durationMs = inputs.durationMs,
            )
        }
    }

    IntroSkipOverlay(
        isVisible = inputs.isCinemaIntroVisible,
        initialPromptProgress = inputs.skipPromptProgress,
        onSkip = {
            viewModel.onEvent(VideoPlayerUiEvent.SkipIntro)
            performConfirmHaptic()
        },
        focusRequester = inputs.tvCinemaIntroFocusRequester,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 100.dp, end = 40.dp),
    )

    if (inputs.activeSegment != null) {
        SegmentSkipOverlay(
            isVisible = inputs.isSkipSegmentVisible,
            initialPromptProgress = inputs.skipPromptProgress,
            segmentType = inputs.activeSegment.type,
            onSkip = {
                viewModel.onEvent(VideoPlayerUiEvent.SkipSegment(inputs.activeSegment))
                performConfirmHaptic()
            },
            focusRequester = inputs.tvSkipSegmentFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 40.dp),
        )
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

    // The Up Next card: the controller's projection (queue head beats the
    // episode sibling — the SAME precedence the advance applies). The confirm
    // overlay suppresses the card while visible — the answer decides the
    // advance, the card must not race it.
    //
    // #17 binge controls: a reached autoplay-episode cap renders the card
    // MANUAL — no countdown (autoplayEnabled false), no autoplay toggle
    // (turning autoplay on would not lift the cap), "Watch now / Dismiss"
    // only. The countdown's expiry is the AUTO arm (the streak grows); the
    // buttons stay the manual event (the streak resets).
    val autoplayLimitReached by viewModel.sessionSlices.overlay.isAutoplayLimitReached.collectAsStateWithLifecycle()
    inputs.upNextCard?.let { card ->
        NextEpisodeOverlay(
            isVisible = inputs.shouldShowUpNext && inputs.stillWatchingPrompt == null,
            episodeTitle = card.title,
            seriesName = card.subtitle,
            seasonNumber = card.seasonNumber,
            episodeNumber = card.episodeNumber,
            thumbnailUrl = inputs.upNextImageUrl,
            countdownSeconds = uiState.autoplay.autoPlayCountdownSec,
            autoplayEnabled = uiState.autoplay.videoAutoplayNext && !autoplayLimitReached,
            onPlayNext = { viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode) },
            onCancel = { viewModel.onEvent(VideoPlayerUiEvent.CancelAutoplay) },
            onCountdownFinish = { viewModel.sessionSlices.overlay.onUpNextCountdownFinished() },
            onToggleAutoplay = if (autoplayLimitReached) {
                null
            } else {
                { viewModel.onEvent(VideoPlayerUiEvent.SetVideoAutoplayNext(!uiState.autoplay.videoAutoplayNext)) }
            },
            isPlaying = inputs.isPlaying,
            pauseCountdown = inputs.isSheetOpen || inputs.isScreenLocked,
            isLoading = inputs.isNextEpisodeLoading,
            focusRequester = inputs.tvNextEpisodeFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 40.dp, end = 40.dp),
        )
    }

    // "Still watching?" confirm (feature 1.3), centered beside the up-next
    // card; the countdown reuses the card's pauseCountdown hook so a sheet or
    // the screen lock doesn't rush the answer, and expiry lands as Stop.
    StillWatchingOverlay(
        state = inputs.stillWatchingPrompt,
        onContinue = { viewModel.onEvent(VideoPlayerUiEvent.StillWatchingContinue) },
        onStop = { viewModel.onEvent(VideoPlayerUiEvent.StillWatchingStop) },
        onTick = { viewModel.onEvent(VideoPlayerUiEvent.StillWatchingTick) },
        pauseCountdown = inputs.isSheetOpen || inputs.isScreenLocked,
        modifier = Modifier.align(Alignment.Center),
    )

    // Buffering spinner fades/scales in and out — an abrupt pop mid-playback
    // reads as a glitch, especially at seek boundaries where it toggles fast.
    val bufferingVisible = uiState.isBuffering && uiState.playerError == null && !inputs.isPlaying && inputs.playbackIntended
    val bufferingFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val bufferingScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedVisibility(
        visible = bufferingVisible,
        enter = fadeIn(animationSpec = bufferingFade) +
            scaleIn(initialScale = 0.8f, animationSpec = bufferingScaleSpec),
        exit = fadeOut(animationSpec = bufferingFade),
        modifier = Modifier.align(Alignment.Center),
        label = "bufferingIndicator",
    ) {
        JellyPlayLoadingIndicator(color = playerOnScrim())
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

/**
 * Seek-scrub trickplay anchored over the bottom controls (non-TV, controls
 * visible, active scrub). The presentation mode (#7) decides the surface and
 * its anchor: BUBBLE floats above the controls (the legacy look — regression
 * bar), FILMSTRIP sits at the seek-bar line, FULLSCREEN fills the surface.
 * A missing manifest degrades every mode to the bubble through the same
 * [trickplayPresentation] fold the gesture site renders through.
 */
@Composable
internal fun BoxScope.PlayerSeekScrubTrickplayOverlay(
    isTv: Boolean,
    trickplayEnabled: Boolean,
    showControls: Boolean,
    isSeeking: Boolean,
    trickplayMode: TrickplayMode,
    trickplayInfo: TrickplayInfo?,
    bitmap: PlatformBitmap?,
    positionMs: Long,
    durationMs: Long,
    loadTile: suspend (positionMs: Long) -> PlatformBitmap?,
) {
    val anchor = trickplayAnchor(trickplayMode, trickplayInfo != null)
    AnimatedVisibility(
        visible = !isTv && trickplayEnabled && showControls && isSeeking,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = when (anchor) {
            TrickplayAnchor.FLOATING_BUBBLE ->
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = TRICKPLAY_THUMB_BOTTOM_CLEARANCE_DP.dp)
            TrickplayAnchor.SEEK_BAR_LINE ->
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = TRICKPLAY_FILMSTRIP_BOTTOM_CLEARANCE_DP.dp)
            TrickplayAnchor.FULL_SURFACE ->
                Modifier.align(Alignment.Center).fillMaxSize()
        },
    ) {
        when (
            val presentation = trickplayPresentation(
                mode = trickplayMode,
                positionMs = positionMs,
                intervalMs = trickplayInfo?.interval ?: 0,
                thumbnailCount = trickplayInfo?.thumbnailCount ?: 0,
            )
        ) {
            TrickplayPresentation.Bubble -> TrickplayOverlay(
                bitmap = bitmap,
                positionMs = positionMs,
                durationMs = durationMs,
            )
            is TrickplayPresentation.Filmstrip -> TrickplayFilmstripOverlay(
                tileIndices = presentation.tileIndices,
                intervalMs = trickplayInfo?.interval ?: 0,
                tileAspect = trickplayTileAspect(trickplayInfo),
                positionMs = positionMs,
                loadTile = loadTile,
            )
            TrickplayPresentation.Fullscreen -> TrickplayFullscreenOverlay(
                bitmap = bitmap,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }
}

/**
 * The shared collector behind BOTH trickplay thumbnail feeds: the
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
