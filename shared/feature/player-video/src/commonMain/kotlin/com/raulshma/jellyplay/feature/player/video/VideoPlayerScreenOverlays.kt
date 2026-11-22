package com.raulshma.jellyplay.feature.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_exit_confirm_confirm
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_exit_confirm_message
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_exit_confirm_stay
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_exit_confirm_title
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.BufferingVeil
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.TrickplayMode
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromeController
import com.raulshma.jellyplay.feature.player.video.state.PlayerBackFocusLadder
import com.raulshma.jellyplay.feature.player.video.components.BufferingDiagnosticsPill
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PinLockOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackRecoveryBanner
import com.raulshma.jellyplay.feature.player.video.components.ProgressiveLoadingHud
import com.raulshma.jellyplay.feature.player.video.components.SegmentSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.SlideToUnlockOverlay
import com.raulshma.jellyplay.feature.player.video.components.StillWatchingOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDelayOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayFilmstripOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayFullscreenOverlay
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import com.raulshma.jellyplay.feature.player.video.LoadPhase
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_skipped_segment
import com.raulshma.jellyplay.feature.player.video.session.DiagnosticsSessionSlice

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
    /**
     * The recovery banner's WHOLE ladder wiring — the SAME holder the back
     * ladder and the TV focus-ownership gates read: [RecoveryBannerLadder.isVisible]
     * is the effective render decision (diagnostics pref + a distinct
     * offer-presence flip, see [rememberRecoveryBannerLadder]) so the banner,
     * the back ladder and the focus gates can never disagree about whether an
     * offer is on screen, and [RecoveryBannerLadder.focusRequester] is the
     * shared focus anchor (the banner's initial grab and the ladder's
     * hide-edge handoff both target it). The proposal VALUE itself stays
     * leaf-collected by the render site below.
     */
    val recoveryBanner: RecoveryBannerLadder,
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
                showTimestamp = uiState.uiPrefs.seekPreviewTime,
            )
            is TrickplayPresentation.Filmstrip -> TrickplayFilmstripOverlay(
                tileIndices = presentation.tileIndices,
                intervalMs = inputs.trickplayInfo?.interval ?: 0,
                tileAspect = trickplayTileAspect(inputs.trickplayInfo),
                positionMs = gestureSeekPositionMs,
                loadTile = { position ->
                    viewModel.loadTrickplayThumbnail(position) as? PlatformBitmap
                },
                showTimestamp = uiState.uiPrefs.seekPreviewTime,
            )
            TrickplayPresentation.Fullscreen -> TrickplayFullscreenOverlay(
                bitmap = gestureBitmap,
                positionMs = gestureSeekPositionMs,
                durationMs = inputs.durationMs,
                showTimestamp = uiState.uiPrefs.seekPreviewTime,
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
    // The display RULE is the buffering-veil pref's fold: DEFAULT keeps the
    // playback-intended gate, ALWAYS shows on any stall, HIDDEN never shows.
    val bufferingVisible = uiState.uiPrefs.bufferingVeil != BufferingVeil.HIDDEN &&
        uiState.isBuffering &&
        uiState.playbackError == null &&
        !inputs.isPlaying &&
        (uiState.uiPrefs.bufferingVeil == BufferingVeil.ALWAYS || inputs.playbackIntended)
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            JellyPlayLoadingIndicator(color = playerOnScrim())
            val diagnostics = viewModel.sessionSlices.diagnostics
            if (uiState.uiPrefs.playbackDiagnostics && diagnostics != null) {
                val bufferHealth by diagnostics.bufferHealth.collectAsStateWithLifecycle()
                val throughput by diagnostics.throughput.collectAsStateWithLifecycle()
                val stallCause by diagnostics.stallCause.collectAsStateWithLifecycle()
                Spacer(Modifier.height(10.dp))
                BufferingDiagnosticsPill(
                    isBuffering = uiState.isBuffering,
                    bufferHealth = bufferHealth,
                    throughput = throughput,
                    stallCause = stallCause,
                    isTranscoding = uiState.media.playMethod.contains("Transcode", ignoreCase = true),
                )
            }
        }
    }

    // Actionable slow-playback recovery banner. The RENDER decision is the
    // root's folded banner visibility the BACK arm and the TV focus gates
    // also read (inputs.recoveryBanner.isVisible), so they can never disagree;
    // this leaf keeps collecting the full proposal for the value it renders.
    val diagnostics = viewModel.sessionSlices.diagnostics
    if (inputs.recoveryBanner.isVisible && diagnostics != null) {
        val activeRecovery by diagnostics.activeRecovery.collectAsStateWithLifecycle()
        activeRecovery?.let { recovery ->
            PlaybackRecoveryBanner(
                recovery = recovery,
                // The shared requester (the back ladder holds the same one): the
                // banner's initial TV grab and the ladder's focus handoff land
                // on the SAME node.
                focusRequester = inputs.recoveryBanner.focusRequester,
                onApplyAction = { action -> diagnostics.applyRecovery(action) },
                onDismiss = { diagnostics.dismissRecovery() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
            )
        }
    }
}

/**
 * Progressive Loading HUD during initial media startup (replaces the black void).
 *
 * [onCancel] is the nav exit (`currentOnBack`): both hosts route it to a route
 * pop (PlayerActivity.finish / guardedNavigator.goBack), whose composition
 * dispose runs the FULL release (viewModel.release → performRelease): the
 * position ledger's teardown bracket snapshots the Stop-report inputs BEFORE
 * the state clears, and releaseInternalsSessionPart cancels the owning LOAD
 * job (sessionTasks.cancel(LOAD)) — a suspended media-detail resolution cannot
 * resume into the released session, and the final Stop report still issues on
 * the release scope. The diagnostics cancelLoad() path is a strict subset of
 * this teardown (same bracket, minus the post-internals PiP/cast/engine half)
 * — never routed to instead.
 */
@Composable
internal fun PlayerInitializingOverlay(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    onCancel: () -> Unit,
) {
    val diagnostics = viewModel.sessionSlices.diagnostics
    val loadPhase = diagnostics?.loadPhase?.collectAsStateWithLifecycle()?.value
        ?: LoadPhase.Loading.Preparing()
    ProgressiveLoadingHud(
        loadPhase = loadPhase,
        title = uiState.title,
        subtitle = uiState.subtitle,
        showTelemetry = uiState.uiPrefs.playbackDiagnostics,
        onCancel = onCancel,
    )
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
    /** The scrub preview's target timestamp (the seek-preview-time pref). */
    seekPreviewTime: Boolean = true,
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
                showTimestamp = seekPreviewTime,
            )
            is TrickplayPresentation.Filmstrip -> TrickplayFilmstripOverlay(
                tileIndices = presentation.tileIndices,
                intervalMs = trickplayInfo?.interval ?: 0,
                tileAspect = trickplayTileAspect(trickplayInfo),
                positionMs = positionMs,
                loadTile = loadTile,
                showTimestamp = seekPreviewTime,
            )
            TrickplayPresentation.Fullscreen -> TrickplayFullscreenOverlay(
                bitmap = bitmap,
                positionMs = positionMs,
                durationMs = durationMs,
                showTimestamp = seekPreviewTime,
            )
        }
    }
}

/**
 * The shared collector behind BOTH trickplay thumbnail feeds: the
 * seek-bar / D-pad scrub (seek overlay bitmap + the TV mirror, which renders
 * no gesture overlay) and the gesture-swipe preview. The two former inline
 * `LaunchedEffect(Unit)` collectors differed only in the position source, the
 * gating flag and the target write — the combined `Triple(position, gate, info)`
 * read, the conflation that keeps only the latest drag frame, and the gate
 * check live here exactly once. The state reads stay inside [snapshotFlow]'s
 * block via the getter lambdas, so snapshot tracking is byte-identical to the
 * inline form; each feed's clear-on-end LaunchedEffect stays at its call site
 * (their clear semantics genuinely differ).
 *
 * `snapshotFlow` observes only SNAPSHOT state (Compose `MutableState`) — a
 * `StateFlow.value` read inside the block registers no dependency, so a feed
 * whose position/gate arrive as StateFlows (the gesture feed) would run the
 * block exactly once and never again. Such inputs must be announced through
 * [changePulses] instead: every pulse re-reads the Triple and re-runs the gate
 * check (deduped to the same distinctUntilChanged semantics the emitted form
 * had). The gesture feed pulses with its drag flows; the seek-bar feed (pure
 * Compose state) keeps the default empty pulse stream.
 */
internal suspend fun collectTrickplayThumbnails(
    positionMs: () -> Long,
    gate: () -> Boolean,
    trickplayInfo: () -> TrickplayInfo?,
    onFetch: suspend (positionMs: Long, trickplayInfo: TrickplayInfo?) -> Unit,
    changePulses: Flow<Unit> = emptyFlow(),
) {
    var lastInputs: Triple<Long, Boolean, TrickplayInfo?>? = null
    merge(
        snapshotFlow { Triple(positionMs(), gate(), trickplayInfo()) }.map { },
        changePulses,
    )
        .conflate()
        .collect {
            val inputs = Triple(positionMs(), gate(), trickplayInfo())
            if (inputs == lastInputs) return@collect
            lastInputs = inputs
            val (pos, shouldFetch, info) = inputs
            if (shouldFetch) {
                onFetch(pos, info)
            }
        }
}
// ── Confirm-exit (the video_confirm_exit pref) ──────────────────────────────

/**
 * The back ladder's exit interposer: the first exit-intent BACK raises the
 * overlay, a second BACK — or the Close button — completes the exit. The
 * controller owns only the visible state + the two arms; the overlay render
 * is [ExitConfirmOverlay]. Deliberately NOT a Dialog window: the overlay
 * lives in the surface Box so a second BACK still reaches the SCREEN's back
 * handler (a Dialog would swallow the press into its own dismiss-on-back) —
 * the pref's "second BACK exits" semantics stay exact.
 *
 * Not saveable: a config-change restore while the confirm is up simply
 * stands it down (the safe direction — no stale exit intent survives).
 */
internal class PlayerExitConfirmController(
    private val confirmEnabled: () -> Boolean,
    private val onExit: () -> Unit,
) {
    var visible by mutableStateOf(false)
        private set

    /** The screen back handler's Exit-verdict arm. */
    fun onBackPress() {
        if (confirmEnabled() && !visible) visible = true else onExit()
    }

    /** The overlay's Stay arm (and the scrim tap): stands the confirm down. */
    fun stay() {
        visible = false
    }

    /** The overlay's Close arm: completes the exit the ladder armed. */
    fun close() {
        visible = false
        onExit()
    }
}

/** Remember the [PlayerExitConfirmController] (reader-lambda rule: stable handles only). */
@Composable
internal fun rememberExitConfirmController(
    confirmEnabled: () -> Boolean,
    onExit: () -> Unit,
): PlayerExitConfirmController = remember { PlayerExitConfirmController(confirmEnabled, onExit) }

/**
 * The recovery banner's back-ladder wiring, remembered in ONE holder (the
 * screen-ceiling's wiring-cluster idiom): the shared [FocusRequester] the
 * banner's initial TV grab AND [rememberPlayerBackLadder]'s hide-edge handoff
 * both target, the banner's visibility arm (the ladder's BACK-dismiss +
 * focus-handoff gate), and the dismissal the ladder's BACK arm runs.
 *
 * [isVisible] is the banner's EFFECTIVE render decision, folded once here and
 * reused by every consumer — the overlay tier renders from it, the BACK ladder
 * dismisses on it, and the TV focus-ownership/grab gates read it — so an offer
 * that is not rendered cannot intercept BACK or suppress the player's focus
 * reassertion. A null diagnostics slice (no diagnostics telemetry) simply
 * means never visible.
 */
internal class RecoveryBannerLadder(
    val focusRequester: FocusRequester,
    val isVisible: Boolean,
    val onDismiss: () -> Unit,
)

/**
 * Remembers the [RecoveryBannerLadder] over the session's diagnostics slice.
 *
 * The root observes ONLY a distinct banner-visibility projection — the full
 * `activeRecovery` proposal stays with the overlay leaf that renders it — so
 * a proposal change that keeps the same visibility (the stall deepening from
 * one verdict to the next) never invalidates the screen root. Visibility is
 * additionally gated on the playback-diagnostics pref (the banner render's
 * first gate), folding the render condition and the BACK/focus consumers into
 * one decision.
 */
@Composable
internal fun rememberRecoveryBannerLadder(
    diagnostics: DiagnosticsSessionSlice?,
    /** The playback-diagnostics pref — the banner render's first gate. */
    diagnosticsEnabled: Boolean,
): RecoveryBannerLadder {
    val offerPresent = if (diagnostics == null) {
        false
    } else {
        remember(diagnostics) {
            diagnostics.activeRecovery
                .map { it != null }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = diagnostics.activeRecovery.value != null).value
    }
    return RecoveryBannerLadder(
        focusRequester = remember { FocusRequester() },
        isVisible = diagnosticsEnabled && offerPresent,
        onDismiss = { diagnostics?.dismissRecovery() },
    )
}

/**
 * The BACK hide-or-exit ladder + the focus-grab handoff edge — the wiring
 * half of the cluster `VideoPlayerScreen`'s root carried inline (hoisted
 * beside the ladder's exit interposer, the [rememberTrickplayFeeds]
 * placement precedent). Owns the always-on back interception
 * ([JellyPlayBackHandler] — the seam's Android actual wires the system back;
 * the desktop actual is a no-op and Esc is the shell's concern) and the
 * hide-edge focus handoff effect, in that order, and returns the exit
 * interposer the root renders as [ExitConfirmOverlay].
 *
 * The back handler IS the screen's single BACK decision-maker, and it runs
 * exactly once per press on every pipeline pairing: the TV D-pad arm set
 * deliberately carries no BACK_OR_HIDE arm (the arm acting on the KeyDown
 * AND the predictive-back callback replaying the same press on the KeyUp's
 * Pre-IME stage double-acted: one press hid the controls AND exited), and
 * PlayerActivity.onBackPressed suppresses the legacy key path on API 33+
 * whenever a back callback IS registered — on API <33 (and in any
 * no-callback window) the legacy path is the ONLY dispatch, so there is
 * nothing to suppress. The verdict is the chrome module's
 * (`PlayerChromeController.backVerdict` — sheet → close, TV-visible → hide,
 * else exit, the hide arm TV-only per the touch-exits ladder); the
 * verdict→action dispatch and the handoff priority ladder are the state
 * module's [PlayerBackFocusLadder]. The gate reads arrive as values (the
 * handoff effect's restart keys) — exactly the former
 * `LaunchedEffect(chrome.visible, isTv, …)` key set.
 */
@Composable
internal fun rememberPlayerBackLadder(
    isTv: Boolean,
    hasHardwareKeyboard: Boolean,
    chrome: PlayerChromeController,
    controlsVisible: Boolean,
    cinemaIntroVisible: Boolean,
    recoveryBanner: RecoveryBannerLadder? = null,
    nextEpisodeVisible: Boolean,
    skipSegmentVisible: Boolean,
    closeSheet: () -> Unit,
    confirmExit: () -> Boolean,
    onExit: () -> Unit,
    tvCinemaIntroFocusRequester: FocusRequester,
    tvNextEpisodeFocusRequester: FocusRequester,
    tvSkipSegmentFocusRequester: FocusRequester,
    keyboardFocusRequester: FocusRequester,
): PlayerExitConfirmController {
    val recoveryBannerVisible = recoveryBanner?.isVisible == true
    // Composition-tracked sheet term (the chrome's reader lambda reads the
    // screen's `currentSheet` snapshot state): the hide-edge handoff's guard —
    // while a sheet is open its modal surface owns focus, and no
    // behind-the-sheet overlay grab (the recovery banner's included) may fire.
    val sheetOpen = chrome.sheetOpen
    val exitConfirm = rememberExitConfirmController(confirmExit, onExit)
    JellyPlayBackHandler(enabled = true) {
        when (
            PlayerBackFocusLadder.backAction(
                verdict = chrome.backVerdict(hideArm = PlayerBackFocusLadder.systemBackHideArm(isTv)),
                recoveryBannerVisible = recoveryBannerVisible,
            )
        ) {
            PlayerBackFocusLadder.BackAction.CloseSheet -> closeSheet()
            PlayerBackFocusLadder.BackAction.DismissRecoveryBanner -> recoveryBanner?.onDismiss?.invoke()
            PlayerBackFocusLadder.BackAction.HideControls -> chrome.hide()
            PlayerBackFocusLadder.BackAction.None -> {}
            PlayerBackFocusLadder.BackAction.ExitIntent -> exitConfirm.onBackPress()
        }
    }
    LaunchedEffect(controlsVisible, isTv, nextEpisodeVisible, skipSegmentVisible, cinemaIntroVisible, recoveryBannerVisible, sheetOpen) {
        when (
            PlayerBackFocusLadder.focusHandoff(
                isTv = isTv,
                hasHardwareKeyboard = hasHardwareKeyboard,
                controlsVisible = controlsVisible,
                cinemaIntroVisible = cinemaIntroVisible,
                recoveryBannerVisible = recoveryBannerVisible,
                nextEpisodeVisible = nextEpisodeVisible,
                skipSegmentVisible = skipSegmentVisible,
                sheetOpen = sheetOpen,
            )
        ) {
            PlayerBackFocusLadder.FocusHandoff.CinemaIntro ->
                tvCinemaIntroFocusRequester.tryRequestFocus("tv_cinema_intro")
            PlayerBackFocusLadder.FocusHandoff.RecoveryBanner ->
                recoveryBanner?.focusRequester?.tryRequestFocus("tv_recovery_banner")
            PlayerBackFocusLadder.FocusHandoff.NextEpisode ->
                tvNextEpisodeFocusRequester.tryRequestFocus("tv_next_episode")
            PlayerBackFocusLadder.FocusHandoff.SkipSegment ->
                tvSkipSegmentFocusRequester.tryRequestFocus("tv_skip_segment")
            PlayerBackFocusLadder.FocusHandoff.DeferToGrabEffect ->
                harnessFocusDiag(
                    "hide-edge handoff: box case deferred to the grab effect (showControls=false)",
                )
            PlayerBackFocusLadder.FocusHandoff.KeyboardRegrab -> {
                // Same call on every platform (Android behavior untouched); the
                // captured result is a desktop-only harness-gated diag line.
                val autoHideEdgeOk = keyboardFocusRequester.tryRequestFocus("keyboard_player")
                harnessFocusDiag("auto-hide edge regrab ok=$autoHideEdgeOk")
            }
            PlayerBackFocusLadder.FocusHandoff.None -> {}
        }
    }
    return exitConfirm
}

/**
 * The in-player exit confirmation: a scrim (tap = stay) with a centered card
 * carrying the title, the hint line and the Stay / Close pill pair.
 */
@Composable
internal fun BoxScope.ExitConfirmOverlay(controller: PlayerExitConfirmController) {
    if (!controller.visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controller.stay() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = ShapeCache.smooth28,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.player_video_exit_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    stringResource(Res.string.player_video_exit_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.player_video_exit_confirm_stay),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { controller.stay() },
                    )
                    Text(
                        stringResource(Res.string.player_video_exit_confirm_confirm),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { controller.close() },
                    )
                }
            }
        }
    }
}
