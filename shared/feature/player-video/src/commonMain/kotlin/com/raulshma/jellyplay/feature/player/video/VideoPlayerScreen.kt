package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_restart
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.raulshma.jellyplay.core.ui.model.inputQuickToggleLabels
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.jetbrains.compose.resources.stringResource

import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_capturing_frame
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_not_ready
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio_only_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_resumed_message
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_font_invalid_format
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleFormatCatalog



import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController
import com.raulshma.jellyplay.feature.player.video.state.PlayerActionExecutor
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputPolicy
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputGates
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.WheelAxis
import com.raulshma.jellyplay.feature.player.video.engine.styleChangedExcludingDelay
import com.raulshma.jellyplay.feature.player.video.chrome.controlsAutoHideTimeoutMs
import com.raulshma.jellyplay.feature.player.video.chrome.KEYBOARD_SEEK_COMMIT_DELAY_MS
import com.raulshma.jellyplay.feature.player.video.chrome.shouldScheduleControlsAutoHide
import com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
import com.raulshma.jellyplay.feature.player.video.components.PlaybackErrorDialog
import com.raulshma.jellyplay.feature.player.video.components.CompanionDashboard
import com.raulshma.jellyplay.feature.player.video.components.PlayerControls
import com.raulshma.jellyplay.feature.player.video.components.PlayerOverflowMenuInputToggle
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.feature.player.video.components.PlayerEffectsControls
import com.raulshma.jellyplay.feature.player.video.components.SleepTimerControls
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayIndicator
import com.raulshma.jellyplay.feature.player.video.components.TrackControls
import com.raulshma.jellyplay.feature.player.video.components.TransportControls
import com.raulshma.jellyplay.feature.player.video.components.GestureControls
import com.raulshma.jellyplay.feature.player.video.components.SheetControls
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTab

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.designsystem.theme.PlayerDarkTheme
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim

// ── Section-host layout (composition-only split) ─────────────────────────
// This 15-composable screen is split by overlay family across sibling files
// in this package. Each host owns one family, moved verbatim — signatures
// unchanged, `private` → `internal` only where the root file calls the
// symbol. Navigate by family:
//   - VideoPlayerScreen.kt (this file): the root composition + section wiring
//     (state, key/gesture controller wiring, effect collectors, the sheet
//     router + error-dialog invocation) and the cast companion-dashboard branch.
//   - VideoPlayerScreenGestures.kt: input/gesture tier — the surface Box's
//     TV D-pad + hardware-key input modifier, the tap/double-tap/pinch-zoom
//     modifier, the gesture indicator tier and the hold-speed pill.
//   - VideoPlayerScreenOverlays.kt: overlay tiers over the surface — center
//     overlays (gesture trickplay, intro/segment skip, skipped notice,
//     up-next, HDR badge, buffering), lock overlays (PIN/slide-to-unlock),
//     seek-scrub trickplay, subtitle-delay overlay, shared trickplay collector.
//   - VideoPlayerScreenInfo.kt: status tier — Stats-for-Nerds, the transient
//     badge family, cast indicator, both snackbar hosts.
//   - VideoPlayerScreenSheets.kt: PlayerSheetRouter (all modal sheets) and
//     its flow-subscribing binder helpers.

// ── Player overlay/animation timing (ms) ─────────────────────────────────
// Named so the tuning is discoverable instead of scattered as bare literals.
/** How long the gesture-seek ripple/indicator lingers after the last seek input. */
private const val GESTURE_SEEK_LINGER_MS = 800L

/** "Resumed — tap to restart" chip lifetime (3s). */
private const val RESUME_CHIP_DISPLAY_MS = 3_000L

/**
 * Platform seam: nudging the system media (STREAM_MUSIC) volume for
 * the hardware-keyboard shortcuts (arrows / volume keys on non-TV) needs
 * AudioManager on Android; desktop is a no-op. Mirrors the gesture volume
 * path, which adjusts the stream volume rather than the engine volume so the
 * system volume UI and ringer behaviour stay consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    itemId: String,
    mediaSourceId: String?,
    startPositionTicks: Long,
    subtitleStreamIndex: Int? = null,
    audioStreamIndex: Int? = null,
    onBack: () -> Unit,
    onEnterPip: () -> Unit = {},
    onOpenSubtitleTester: () -> Unit = {},
    viewModel: VideoPlayerViewModel = koinViewModel(),
) {
    // Host-window + input seams: the Activity/Context system-surface
    // work this screen used to do inline lives behind these now — androidMain
    // actuals keep it verbatim, the desktop actuals are no-ops.
    val windowOps = rememberPlayerWindowOps()
    val streamVolumeAdjuster = rememberStreamVolumeAdjuster()
    val snackbarHostState = remember { SnackbarHostState() }
    // Dedicated host for the resume chip. Kept separate from [snackbarHostState]
    // so the chip can anchor under the top bar (TopCenter) while the shared
    // bottom host still serves screenshot / syncplay / pass-out toasts.
    val resumeChipHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Resume-reminder chip: when playback resumes from a saved position, offer a
    // one-tap "Restart" so the user isn't forced to scrub back.
    // Upstream c64d5baf4 removed the seekbar resume-position marker; only the
    // chip remains, so no marker state is collected here.
    val resumedMessage = stringResource(Res.string.player_resumed_message)
    val restartLabel = stringResource(CoreUiRes.string.core_restart)
    LaunchedEffect(viewModel) {
        viewModel.resumeReminder.collect {
            // Specified as a 3s chip. SnackbarDuration has no 3s
            // preset (Short ≈ 1.5s), so show it Indefinite and auto-dismiss
            // after 3s unless the user taps "Restart" first.
            val snackbarJob = scope.launch {
                val result = resumeChipHostState.showSnackbar(
                    message = resumedMessage,
                    actionLabel = restartLabel,
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onEvent(VideoPlayerUiEvent.RestartPlayback)
                }
            }
            scope.launch {
                delay(RESUME_CHIP_DISPLAY_MS)
                resumeChipHostState.currentSnackbarData?.dismiss()
            }
            snackbarJob.join()
        }
    }

    val isInPipMode by viewModel.pipController.isInPipMode.collectAsStateWithLifecycle()

    // Ephemeral UI state. Migrated to `rememberSaveable` so a configuration
    // change (locale switch, rotation outside the player's locked orientation)
    // doesn't reset seek progress, the open sheet, or gesture state mid-stream.
    // References to non-saveable types (View, Bitmap, Job, SubtitleStyle cache)
    // remain on `remember` below — they're either re-derived or non-restorable.
    var showControls by rememberSaveable { mutableStateOf(true) }
    var controlsHasFocus by rememberSaveable { mutableStateOf(false) }
    // Tracks whether playback is intended (the user / app last pressed play, not
    // pause). Used to suppress the full-screen buffering spinner when the engine
    // briefly reports BUFFERING during a user-initiated pause — ExoPlayer passes
    // through BUFFERING on some streams right after pause, which otherwise shows
    // a misleading "loading" spinner over a paused frame.
    var playbackIntended by rememberSaveable { mutableStateOf(true) }
    var currentSheet by rememberSaveable(stateSaver = PlayerSheetSaver) {
        mutableStateOf(PlayerSheet.None)
    }
    // Reset-first intent for the NEXT SubtitleHub open. The overflow
    // "Subtitles" entry opens the hub with a cleared search/cultures slice
    // ("stale results don't leak across items"); the Tracks-tab entry
    // deliberately keeps prior state. The router's LaunchedEffect is the
    // sheet's single load trigger (openSubtitleHub — the double-fetch
    // removal), so the entry's decision rides in this flag from click to
    // composition and is consumed there. Not saveable: a config-change
    // restore of the sheet re-opens with resetFirst = false, the same
    // no-reset re-load the former router effect performed.
    var subtitleHubResetFirst by remember { mutableStateOf(false) }
    // Which tab the NEXT SubtitleHub open lands on. Every entry sets it
    // explicitly (no stale value leaks from the previous entry): the primary
    // Subtitles button and the overflow entry land on Tracks, while the
    // metadata row's mpv-config chip lands on Style — the tab that carries
    // the full ownership notice the chip is the shortcut to. Not saveable:
    // a config-change restore re-opens on Tracks, the same class of
    // behavior the reset-first flag above has.
    var subtitleHubInitialTab by remember { mutableStateOf(SubtitleHubTab.TRACKS) }
    // Transparent subtitle-delay overlay (VLC-style). Not saveable: dismissed on
    // recreation, same as the gesture-driven seek/brightness pills.
    var showDelayOverlay by remember { mutableStateOf(false) }
    var isSeeking by rememberSaveable { mutableStateOf(false) }
    var isOverflowMenuOpen by rememberSaveable { mutableStateOf(false) }
    var seekPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var playerViewRef by remember { mutableStateOf<Any?>(null) }
    var lastAppliedSubtitleStyle by remember { mutableStateOf<SubtitleStyle?>(null) }
    var videoZoom by rememberSaveable { mutableFloatStateOf(1f) }

    val isTv = LocalTvMode.current
    // Hardware-keyboard detection (Chromebooks, Bluetooth keyboards, Samsung
    // DeX). Drives the non-TV keyboard-shortcut handler so phones/tablets with
    // a keyboard get space/arrows/F/M/Esc controls while touch-only devices
    // attach no extra key handler. TV keeps its dedicated D-pad scheme below.
    // Platform seam: the Configuration read lives in the androidMain
    // actual; desktop always reports true.
    val hasHardwareKeyboard = rememberHasHardwareKeyboard()

    val tvPlayerFocusRequester = remember { FocusRequester() }
    val tvSkipSegmentFocusRequester = remember { FocusRequester() }
    val tvCinemaIntroFocusRequester = remember { FocusRequester() }
    val tvNextEpisodeFocusRequester = remember { FocusRequester() }
    val keyboardFocusRequester = remember { FocusRequester() }
    // (desktop only — updated by the jvm-gated onFocusChanged below,
    // so it stays false on Android where the grab seam no-ops anyway): whether
    // ANYTHING under the keyboard layer holds focus; drives the grab seam's
    // re-assert-on-loss against the mpv surface-mount focus drop.
    var keyboardLayerHoldsFocus by remember { mutableStateOf(false) }
    var userInteractionCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(showControls) {
        viewModel.onEvent(VideoPlayerUiEvent.SetControlsVisible(showControls))
    }

    val isScreenLocked = uiState.isScreenLocked

    // Mirror the screen-lock state to PipController so the host Activity can gate
    // PiP auto-entry while the controls are locked
    LaunchedEffect(isScreenLocked) {
        viewModel.pipController.setControlsLocked(isScreenLocked)
    }

    val localSubtitlePicker = rememberDocumentPicker(
        mimeTypes = SubtitleFormatCatalog.pickerMimeTypes,
    ) { uriString: String? ->
        if (uriString != null) {
            val fileName = pickedDocumentDisplayName(uriString) ?: "subtitle.srt"
            viewModel.playbackSession.subtitles.addLocalSubtitle(uriString, fileName)
            currentSheet = PlayerSheet.None
        }
    }

    val fontInvalidFormatMessage = stringResource(Res.string.player_video_font_invalid_format)
    val fontPicker = rememberDocumentPicker(
        mimeTypes = arrayOf("*/*"),
    ) { uriString: String? ->
        if (uriString != null) {
            if (isSupportedUserFontFile(pickedDocumentDisplayName(uriString))) {
                viewModel.onEvent(VideoPlayerUiEvent.InstallUserFont(uriString))
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = fontInvalidFormatMessage,
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    var seekTrickplayBitmap by remember { mutableStateOf<PlatformBitmap?>(null) }
    var gestureTrickplayBitmap by remember { mutableStateOf<PlatformBitmap?>(null) }
    var gestureTrickplayVisible by remember { mutableStateOf(false) }
    var tvTrickplayBitmap by remember { mutableStateOf<PlatformBitmap?>(null) }

    LaunchedEffect(itemId) {
        if (viewModel.playbackSession.cast.isBackgroundCasting) {
            viewModel.onEvent(VideoPlayerUiEvent.ReattachFromBackgroundCast)
        } else {
            viewModel.onEvent(
                VideoPlayerUiEvent.Initialize(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    startPositionTicks = startPositionTicks,
                    subtitleStreamIndex = subtitleStreamIndex,
                    audioStreamIndex = audioStreamIndex,
                )
            )
        }
    }

    // Capture the latest onBack via rememberUpdatedState — the collector
    // below keys on Unit, so without this the screen keeps invoking the
    // onBack lambda captured at first composition (a nav lambda that may have
    // been rebuilt by the parent).
    val currentOnBack by rememberUpdatedState(onBack)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Issue #145: programmatic closes must never run against a STOPPED
    // activity. The old collectors were plain LaunchedEffects, which keep
    // collecting behind the keyguard — an episode ending (background audio,
    // SyncPlay, a lockscreen play) or a late PiP-dismiss signal while the
    // phone was locked called finish() on a hidden window, and unlock revealed
    // the browse UI with the player silently gone. PiP dismiss is now handled
    // solely by the ViewModel (its collector folds it into closePlayer); the
    // screen consumes only closePlayer, parked via repeatOnLifecycle(RESUMED).
    // closePlayer is Channel(BUFFERED), so events raised while stopped are
    // deferred and delivered on the next resume instead of tearing the window
    // down unseen.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.closePlayer.collect { currentOnBack() }
        }
    }
    // The eight window/lifecycle effects (PiP immersive restore, window-focus
    // immersive re-hide, teardown, keep-screen-on, frame-rate match, brightness
    // apply + ON_RESUME re-apply, ON_STOP lock reset) moved verbatim into ONE
    // composable beside PlayerScreenPolicies. This single call sits at the
    // SAME position in composition order the contiguous block occupied, so
    // effect dispatch order — and the race guards documented in its bodies
    // (PiP-transition focus flip, ON_RESUME brightness) — is unchanged.
    PlayerWindowSessionEffects(
        windowOps = windowOps,
        isInPipMode = isInPipMode,
        isTv = isTv,
        // uiState.isPlaying (NOT the cast-aware local `isPlaying`, which is
        // derived further down): the keep-screen-on gate reads the engine's
        // local play state, exactly as the inline effect did.
        isPlaying = uiState.isPlaying,
        keepScreenOnDuringVideo = uiState.uiPrefs.keepScreenOnDuringVideo,
        frameRateMatching = uiState.gestures.frameRateMatching,
        refreshRateMode = uiState.gestures.refreshRateMode,
        videoFrameRate = uiState.videoFrameRate,
        mediaStreams = uiState.media.mediaStreams,
        rememberBrightness = uiState.gestures.rememberBrightness,
        brightnessLevel = uiState.gestures.brightnessLevel,
        isScreenLocked = isScreenLocked,
        lifecycleOwner = lifecycleOwner,
        pipController = viewModel.pipController,
        cast = viewModel.playbackSession.cast,
        releasePlayer = { viewModel.release() },
        detachForBackgroundCast = { viewModel.onEvent(VideoPlayerUiEvent.DetachForBackgroundCast) },
        setScreenLocked = { viewModel.onEvent(VideoPlayerUiEvent.SetScreenLocked(it)) },
        clearPlayerView = { playerViewRef = null },
    )

    // Always-on back interception (the seam's Android actual wires the system
    // back; the desktop actual is a no-op and Esc is the shell's concern).
    JellyPlayBackHandler(enabled = true) {
        if (currentSheet != PlayerSheet.None) {
            currentSheet = PlayerSheet.None
        } else if (isTv && showControls) {
            showControls = false
        } else {
            onBack()
        }
    }

    val engine by viewModel.playerEngineFlow.collectAsStateWithLifecycle()
    val title = uiState.title
    val subtitle = uiState.subtitle
    val isCastConnected by viewModel.playbackSession.cast.isConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val isCastConnecting by viewModel.playbackSession.cast.isConnectingFlow.collectAsStateWithLifecycle(initialValue = false)
    val castIsPlaying by viewModel.playbackSession.cast.castIsPlaying.collectAsStateWithLifecycle(initialValue = false)
    val castDuration by viewModel.playbackSession.cast.castDurationMs.collectAsStateWithLifecycle(initialValue = 0L)

    val isPlaying = if (isCastConnected) castIsPlaying else uiState.isPlaying
    // If playback is actually running, the user intended it — reconcile the
    // playbackIntended flag from the authoritative play state so paths that
    // resume playback outside the screen's doPlay/doPause (PiP remote, SyncPlay,
    // autoplay, sleep-timer cancel) keep the buffering-spinner gate correct.
    LaunchedEffect(isPlaying) {
        if (isPlaying) playbackIntended = true
    }
    // duration is low-frequency (changes only on media load / live updates),
    // so it is safe to collect at the screen root. currentPosition is NOT
    // collected here — it now lives on viewModel.currentPositionMs and is read
    // only inside the leaf composables that render it.
    val engineDuration by viewModel.durationMs.collectAsStateWithLifecycle()
    val duration = if (isCastConnected) castDuration else engineDuration
    val playbackSpeed = uiState.playbackSpeed
    val currentMediaSource = uiState.media.currentMediaSource
    val mediaStreams = uiState.media.mediaStreams
    val aspectRatio = uiState.videoFx.aspectRatio
    val detectedAspectRatio = uiState.videoFx.detectedAspectRatio

    val toggleOrientation: () -> Unit = remember(windowOps, uiState.uiPrefs.defaultOrientation) {
        {
            // Symmetric toggle (portrait ↔ the user's configured default
            // landscape) lives in the platform actual: it reads the host's
            // current orientation to decide the direction.
            windowOps.toggleOrientation(
                preferLockedLandscape = uiState.uiPrefs.defaultOrientation == OrientationMode.LOCKED_LANDSCAPE,
            )
        }
    }

    val syncPlayIgnoreWait by viewModel.playbackSession.syncPlay.ignoreWait.collectAsStateWithLifecycle()

    LaunchedEffect(isCastConnected, uiState.uiPrefs.defaultOrientation) {
        when (val decision = orientationLockDecision(
            isTv = isTv,
            isCastConnected = isCastConnected,
            preference = uiState.uiPrefs.defaultOrientation,
        )) {
            is OrientationLockDecision.Immediate -> windowOps.lockOrientation(decision.lock)
            is OrientationLockDecision.SettleFirst -> {
                delay(400)
                windowOps.lockOrientation(decision.lock)
            }
        }
    }

    // Segment / up-next overlay state is derived on the ViewModel from the
    // high-frequency position flow but only re-emits at segment boundaries, so
    // collecting it here keeps the root a low-frequency recomposition scope.
    val segmentOverlay by viewModel.segmentOverlayState.collectAsStateWithLifecycle()
    val isInIntro = segmentOverlay.isInIntro
    val isInCredits = segmentOverlay.isInCredits
    val shouldShowUpNext = segmentOverlay.shouldShowUpNext
    val activeSegment = segmentOverlay.activeSegment
    val activeSegmentBehavior = segmentOverlay.activeSegmentBehavior
    val cinemaIntroState = uiState.cinemaIntroState
    // Drives the Up Next overlay's in-flight state: play button shows progress
    // and stops accepting clicks until the next-episode load settles (#146).
    val isNextEpisodeLoading by viewModel.isNextEpisodeLoading.collectAsStateWithLifecycle()
    // The "Still watching?" confirm overlay (feature 1.3) — collected at the
    // root like the other controller-owned slices and passed into the tier.
    val stillWatchingPrompt by viewModel.stillWatchingPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(aspectRatio, detectedAspectRatio, engine) {
        // The engine maps the enum to its native mode (media3 resize mode / mpv
        // panscan / VLC aspectRatio) — no media3 constant crosses the seam here.
        engine?.setAspectRatio(effectiveAspectRatio(aspectRatio, detectedAspectRatio))
    }

    val playMethod = uiState.media.playMethod
    val subtitleStyle = uiState.subtitleStyle
    val nextEpisode = uiState.episodes.nextEpisode
    val nextEpisodeImageUrl = remember(nextEpisode) {
        nextEpisode?.let { viewModel.getImageUrl(it.id, 300) }
    }
    val isInSyncPlaySession = uiState.isInSyncPlaySession

    val isNextEpisodeVisible = nextEpisode != null && shouldShowUpNext
    val isCinemaIntroVisible = cinemaIntroState != null && !isInPipMode
    val isSkipSegmentVisible = isSkipSegmentButtonVisible(
        activeSegment = activeSegment,
        segmentBehavior = activeSegmentBehavior,
        isInPipMode = isInPipMode,
        isCinemaIntroVisible = isCinemaIntroVisible,
        shouldShowUpNext = shouldShowUpNext,
    )

    LaunchedEffect(showControls, isTv, isNextEpisodeVisible, isSkipSegmentVisible, isCinemaIntroVisible) {
        if (isTv && !showControls) {
            when {
                isCinemaIntroVisible -> tvCinemaIntroFocusRequester.tryRequestFocus("tv_cinema_intro")
                isNextEpisodeVisible -> tvNextEpisodeFocusRequester.tryRequestFocus("tv_next_episode")
                isSkipSegmentVisible -> tvSkipSegmentFocusRequester.tryRequestFocus("tv_skip_segment")
                else -> tvPlayerFocusRequester.tryRequestFocus("tv_player")
            }
        } else if (!isTv && hasHardwareKeyboard && !showControls) {
            // Same call on every platform (Android behavior untouched); the
            // captured result is a desktop-only harness-gated diag line.
            val autoHideEdgeOk = keyboardFocusRequester.tryRequestFocus("keyboard_player")
            harnessFocusDiag("auto-hide edge regrab ok=$autoHideEdgeOk")
        }
    }

    // Desktop: the hardware-keyboard layer must OWN focus whenever
    // it composes, not only once the controls have hidden — see
    // [grabsKeyboardFocusWithControlsVisible]. The at-HEAD effect above fires
    // on the showControls→false edge, but the controls START visible
    // (`showControls = true`) and auto-hide only after controlsTimeoutMs (or
    // never, while a sheet/seek/overflow suppresses it), so a desktop key
    // press in that window had no focused node to land on: Compose's
    // null-focus fallback dispatch stops at the topmost key-input node (the
    // desktop shell's scaffold onPreviewKeyEvent Row) — ESC popped, SPACE
    // never reached this screen's handler.
    // [layerComposed] tracks the keyboard layer's modifier branch above, so
    // the grab re-arms when it re-composes after a sheet closes. The Android
    // actual returns false, so the effect composes nothing on Android (phone
    // and TV unchanged).
    if (!isTv && hasHardwareKeyboard) {
        PlayerKeyboardFocusGrabEffect(
            focusRequester = keyboardFocusRequester,
            layerComposed = currentSheet == PlayerSheet.None,
            targetHoldsFocus = keyboardLayerHoldsFocus,
        )
    }

    // Play/pause delegate to the VM's routing funnel: the same
    // SyncPlay -> cast -> local order the PiP transport uses, so on-screen
    // and PiP transport presses can never diverge. Only the screen-local
    // `playbackIntended` flag stays here; no routing captures remain, so no
    // remember keys are needed.
    val doPlay: () -> Unit = remember {
        {
            playbackIntended = true
            viewModel.onEvent(VideoPlayerUiEvent.TransportPlay(play = true))
        }
    }
    val doPause: () -> Unit = remember {
        {
            playbackIntended = false
            viewModel.onEvent(VideoPlayerUiEvent.TransportPlay(play = false))
        }
    }
    // Seek delegates to the VM's routing funnel, same as the play/pause
    // lambdas above: the same SyncPlay -> cast -> local order, so the seek
    // bar, gesture commit, D-pad commit, chapter/go-to-time sheet and PiP
    // steps all land on the ONE ladder and can never diverge. No routing
    // captures remain, so no remember keys are needed.
    val doSeekTo: (Long) -> Unit = remember {
        { ms -> viewModel.onEvent(VideoPlayerUiEvent.SeekTo(ms)) }
    }
    // Skip steps route through the VM's single funnel: the clamp math
    // lives in the shared player-contract's stepSeekTargetMs
    // (PlayerChromePolicies — the live player's screen cites the same policy)
    // and the SyncPlay/cast/local routing in VideoPlayerViewModel.seekByStep —
    // this screen and the PiP transport's SKIP actions can no longer diverge.
    // The funnel reads the live gesture step, so no step-duration remember
    // keys are needed.
    val doSeekBack: () -> Unit = remember {
        { viewModel.onEvent(VideoPlayerUiEvent.SeekByStep(-1)) }
    }
    val doSeekForward: () -> Unit = remember {
        { viewModel.onEvent(VideoPlayerUiEvent.SeekByStep(+1)) }
    }
    val doTogglePlayPause: () -> Unit = remember(isPlaying, doPlay, doPause) {
        { if (isPlaying) doPause() else doPlay() }
    }
    val currentDoSeekBack by rememberUpdatedState(doSeekBack)
    val currentDoSeekForward by rememberUpdatedState(doSeekForward)
    val currentDoTogglePlayPause by rememberUpdatedState(doTogglePlayPause)
    val currentSeekDurationMs by rememberUpdatedState(uiState.gestures.seekDurationMs)
    // Gesture-seek / volume / brightness controller. Owns the overlay state and
    // the commit-vs-cancel asymmetry that used to be ~120 lines of inline screen
    // logic with zero test coverage. Android I/O (Window, AudioManager) moves
    // behind lambdas; the pure math lives in GestureSeekMath. Gestures don't
    // survive config changes by design (a half-finished swipe already behaves
    // poorly across rotation), so the controller's StateFlows are in-memory.
    val gestureController = remember(
        scope,
        engine,
        windowOps,
        uiState.gestures.swipeSeekMaxMs,
        isCastConnected,
    ) {
        GestureSeekController(
            scope = scope,
            getEngine = { engine },
            getSwipeSeekMaxMs = { uiState.gestures.swipeSeekMaxMs },
            isCastConnected = { isCastConnected },
            getCastVolume = { viewModel.playbackSession.cast.castVolumeFlow.value },
            readWindowBrightness = { windowOps.readWindowBrightness() },
            writeWindowBrightness = { newBrightness ->
                windowOps.writeWindowBrightness(newBrightness)
            },
            restoreWindowBrightness = { restored ->
                windowOps.restoreWindowBrightness(restored)
            },
            readStreamVolume = { windowOps.readMusicStreamVolume() },
            writeStreamVolume = { newVol ->
                windowOps.setMusicStreamVolume(newVol)
            },
            doSeekTo = doSeekTo,
            saveBrightness = { viewModel.onEvent(VideoPlayerUiEvent.SaveBrightness(it)) },
            setCastVolume = viewModel.playbackSession.cast::setCastVolume,
        )
    }
    val gestureSeekPositionMs by gestureController.seekPositionMs.collectAsStateWithLifecycle()
    val gestureDeltaMs by gestureController.deltaMs.collectAsStateWithLifecycle()
    val isGestureSeeking by gestureController.isSeeking.collectAsStateWithLifecycle()
    val dismissSheet: () -> Unit = remember { { currentSheet = PlayerSheet.None } }

    // Shared confirmation haptic for discrete player actions (seek commit,
    // play/pause toggle, segment skip). Reuses the same host haptic path and
    // hapticsEnabled gate as the gesture-bound haptic below, so a single
    // preference governs all player haptics.
    val performConfirmHaptic: () -> Unit = remember(windowOps, viewModel) {
        {
            if (viewModel.hapticsEnabled) {
                windowOps.performConfirmHaptic()
            }
        }
    }

    // ── Input mapping resolution (issue #171 generalized) ──────────────────
    // Every input tier — touch arms, tap zones, wheel rows, keyboard keys,
    // TV D-pad controls — resolves through the ONE persisted mapping
    // (uiState.gestures.inputMap). [inputGates] snapshots the per-arm
    // booleans for the pointerInput keys; [currentInputMap] is the live read
    // the event-time lambdas consult (the reader-lambda rule above).
    val inputGates = remember(uiState.gestures.inputMap) { PlayerInputGates.of(uiState.gestures.inputMap) }
    val currentInputMap by rememberUpdatedState(uiState.gestures.inputMap)
    // The overflow menu's quick toggles (issue #171): the six high-frequency
    // gesture gates (both edge-swipe halves included), tap-to-flip
    // mid-playback. Labels resolve through the core:ui input-label seam
    // against the CURRENT map — a swapped side pairing relabels in place.
    val quickLabels = inputQuickToggleLabels(uiState.gestures.inputMap)
    val inputQuickToggles = listOf(
        PlayerOverflowMenuInputToggle(
            bindingId = PlayerInputDefaults.ID_SWIPE_BRIGHTNESS,
            labelText = quickLabels.leftSwipe,
            enabled = inputGates.swipeBrightness,
        ),
        PlayerOverflowMenuInputToggle(
            bindingId = PlayerInputDefaults.ID_SWIPE_VOLUME,
            labelText = quickLabels.rightSwipe,
            enabled = inputGates.swipeVolume,
        ),
        PlayerOverflowMenuInputToggle(
            bindingId = PlayerInputDefaults.ID_SWIPE_SEEK,
            labelText = quickLabels.seekSwipe,
            enabled = inputGates.swipeSeek,
        ),
        PlayerOverflowMenuInputToggle(
            bindingId = PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
            labelText = quickLabels.edgeSwipeLeft,
            enabled = inputGates.edgeLeft,
        ),
        PlayerOverflowMenuInputToggle(
            bindingId = PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
            labelText = quickLabels.edgeSwipeRight,
            enabled = inputGates.edgeRight,
        ),
        PlayerOverflowMenuInputToggle(
            bindingId = PlayerInputDefaults.ID_PINCH,
            labelText = quickLabels.pinch,
            enabled = inputGates.pinch,
        ),
    )
    val onInputQuickToggle: (String, Boolean) -> Unit = remember {
        { bindingId, enabled -> viewModel.onEvent(VideoPlayerUiEvent.SetInputBindingEnabled(bindingId, enabled)) }
    }
    val resolvePatternAction: (InputPattern) -> PlayerAction? = remember {
        { pattern -> PlayerInputPolicy.resolveAction(currentInputMap, pattern) }
    }

    // Screenshot capture path, hoisted above [actionExecutor] so the
    // SCREENSHOT arm rides the SAME funnel the overflow-menu button and the
    // remote "TakeScreenshot" receiver drive (no separate code path to
    // drift). Platform seam: PixelCopy on Android's SurfaceView surfaces
    // (only PixelCopy, not View.drawToBitmap, can read them); mpv's
    // screenshot command on desktop, routed through the engine. titleHint
    // seeds the MediaStore / save filename. Result surfaces
    // as a snackbar with the saved path. The guard passes when EITHER a
    // platform surface or an engine exists — the desktop software-render
    // surface publishes no view object at all, so the engine alone carries
    // the desktop capture (the Android actual still requires the view and
    // fails gracefully in the surface-less window). `engine` is a
    // collectAsState delegate read at invocation time, like the
    // effectsState lambdas above.
    val capturingFrameMessage = stringResource(Res.string.player_capturing_frame)
    val playerNotReadyMessage = stringResource(Res.string.player_not_ready)
    val onScreenshotClick: () -> Unit = remember(capturingFrameMessage, playerNotReadyMessage) {
        {
            val view = playerViewRef
            if (view != null || engine != null) {
                scope.launch { snackbarHostState.showSnackbar(capturingFrameMessage, duration = SnackbarDuration.Short) }
                requestVideoFrameCapture(
                    surfaceView = view,
                    engine = engine,
                    titleHint = uiState.title,
                ) { message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                    }
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(playerNotReadyMessage, duration = SnackbarDuration.Short)
                }
            }
            Unit
        }
    }

    // ── Input effects: the shared discrete-action executor ──────────────────
    // The former inline `executePlayerAction` + `handleMediaKeyDown` lambdas
    // and the `keyCodeToInputKey` table, extracted verbatim into
    // [PlayerActionExecutor] — the [GestureSeekController] template: a plain
    // class remembered here, reader lambdas for every value that changes per
    // recomposition, injected ports for every effect, zero uiState in its
    // interface (pinned by ControllerOwnershipTest + PlayerActionExecutorTest).
    // The screen keeps the lambdas that touch screen-local state
    // (`playbackIntended`, the screenshot capture) outside; the D-pad seek
    // chip moved in with the keyboard fold that shares it, and the `seekState`
    // alias below keeps every detector arm and collector reading the same name.
    // Construction sits in the input-wiring cluster rather than beside the
    // gestureController remember because its ports (performConfirmHaptic,
    // onScreenshotClick) are declared above; rebuilding whenever the gesture
    // controller rebuilds keeps the brightness-arm pairing exact.
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentToggleOrientation by rememberUpdatedState(toggleOrientation)
    val currentStreamVolumeAdjuster by rememberUpdatedState(streamVolumeAdjuster)
    val currentOnScreenshotClick by rememberUpdatedState(onScreenshotClick)
    val onKeyUserInteraction: () -> Unit = remember {
        { userInteractionCount++; viewModel.onEvent(VideoPlayerUiEvent.UserInteraction) }
    }
    val actionExecutor = remember(gestureController) {
        PlayerActionExecutor(
            getInputMap = { currentInputMap },
            getShowControls = { showControls },
            getIsPlaying = { currentIsPlaying },
            getHideOsdOnPause = { uiState.uiPrefs.hideOsdOnPause },
            getPlaybackSpeed = { uiState.playbackSpeed },
            getSubtitleOffsetMs = { uiState.subtitleStyle.offsetMs },
            getSupportsAudioDelay = { uiState.engineCapabilities.supportsAudioDelay },
            getAudioDelayMs = { viewModel.playbackSession.effects.state.value.audioDelayMs },
            getSeekStepMs = { currentSeekDurationMs },
            getCurrentPositionMs = { viewModel.playerEngineRef?.currentPositionMs ?: 0L },
            getDurationMs = { viewModel.playerEngineRef?.durationMs ?: 0L },
            onSeekCommit = { doSeekTo(it) },
            doTogglePlayPause = { currentDoTogglePlayPause() },
            doPlay = doPlay,
            doPause = doPause,
            toggleOrientation = { currentToggleOrientation() },
            streamVolumeAdjuster = { up -> currentStreamVolumeAdjuster(up) },
            setAudioDelay = { ms -> viewModel.playbackSession.effects.setAudioDelay(ms) },
            onUiEvent = { viewModel.onEvent(it) },
            doSeekForward = doSeekForward,
            doSeekBack = doSeekBack,
            setShowControls = { showControls = it },
            setShowDelayOverlay = { showDelayOverlay = it },
            onBack = { currentOnBack() },
            onScreenshotClick = { currentOnScreenshotClick() },
            performConfirmHaptic = performConfirmHaptic,
            onUserInteraction = onKeyUserInteraction,
            gestureController = gestureController,
        )
    }
    val seekState = actionExecutor.seekState

    // Live-read wrappers for the pointerInput tiers (tap-and-zoom, gesture
    // overlay): a pointerInput block freezes whatever lambda it captured at
    // launch until one of its keys changes, so the tiers must read the
    // executor/resolver THROUGH these delegates (the file's reader-lambda
    // rule) — the captured values inside them (isPlaying, uiState fields)
    // would otherwise go stale mid-session.
    val currentResolvePatternAction by rememberUpdatedState(resolvePatternAction)
    val currentExecutePlayerAction by rememberUpdatedState(actionExecutor::executeAction)

    //  deterministic desktop delivery (desktop only — the seam is
    // Android-inert, see [grabsKeyboardFocusWithControlsVisible]): publish the
    // handler above to the shell's bridge while this screen composes. The
    // sink declines (returns false) when the normal focused dispatch chain
    // owns the key ([keyboardLayerHoldsFocus]) or a sheet is open
    // ([currentSheet] — the keyboard layer's modifier branch, grab and sink
    // all disarm together), so the shell's forward can never double-handle a
    // key. Disposed with the screen (or the TV/desktop branch swap).
    if (!isTv && hasHardwareKeyboard && grabsKeyboardFocusWithControlsVisible()) {
        val latestKeySink by rememberUpdatedState<(KeyEvent) -> Boolean> { keyEvent ->
            when {
                currentSheet != PlayerSheet.None -> false
                keyboardLayerHoldsFocus -> false
                keyEvent.type != KeyEventType.KeyDown -> false
                else -> {
                    harnessFocusDiag("player-keyboard-box sink: key=${keyEvent.key}")
                    actionExecutor.handleMediaKeyDown(keyEvent)
                }
            }
        }
        DisposableEffect(isTv, hasHardwareKeyboard) {
            val sink: (KeyEvent) -> Boolean = { keyEvent -> latestKeySink(keyEvent) }
            installPlayerKeySink(sink)
            harnessFocusDiag("player-key sink installed")
            onDispose {
                // Identity-guarded: if a stacked player installed a newer
                // sink, popping this screen must not disarm it (latent — no
                // current route stacks players).
                uninstallPlayerKeySink(sink)
                // Reset the focus flag explicitly: onFocusChanged never fires
                // for a composition that simply left, and the cast branch has
                // no keyboard layer to update it.
                keyboardLayerHoldsFocus = false
                harnessFocusDiag("player-key sink uninstalled")
            }
        }
    }

    // Cast-route teardown for the companion dashboard's disconnect action
    // (platform seam: Android also stops the legacy cast transport).
    val disconnectCast = rememberCastDisconnect(viewModel)

    if (isCastConnected) {
        CastCompanionDashboardBranch(
            viewModel = viewModel,
            title = title,
            subtitle = subtitle,
            uiState = uiState,
            isPlaying = isPlaying,
            isCastConnecting = isCastConnecting,
            durationMs = duration,
            doTogglePlayPause = doTogglePlayPause,
            doSeekBack = doSeekBack,
            doSeekForward = doSeekForward,
            doSeekTo = doSeekTo,
            onToggleOrientation = toggleOrientation,
            disconnectCast = disconnectCast,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(
                    playerBoxKeyInputModifier(
                        isTv = isTv,
                        hasHardwareKeyboard = hasHardwareKeyboard,
                        isSheetOpen = currentSheet != PlayerSheet.None,
                        showControls = showControls,
                        isPlaying = isPlaying,
                        hideOsdOnPause = uiState.uiPrefs.hideOsdOnPause,
                        onShowControlsChange = { showControls = it },
                        tvPlayerFocusRequester = tvPlayerFocusRequester,
                        keyboardFocusRequester = keyboardFocusRequester,
                        onUserInteraction = onKeyUserInteraction,
                        onKeyboardLayerFocusChange = { keyboardLayerHoldsFocus = it },
                        seekState = seekState,
                        doTogglePlayPause = doTogglePlayPause,
                        doSeekBack = doSeekBack,
                        doSeekForward = doSeekForward,
                        performConfirmHaptic = performConfirmHaptic,
                        handleMediaKeyDown = actionExecutor::handleMediaKeyDown,
                        resolveAction = resolvePatternAction,
                        executePlayerAction = actionExecutor::executeAction,
                    )
                )
                // Wheel tier: the vertical-wheel row = volume, the Shift+wheel
                // / horizontal row = seek, resolved through the input mapping.
                // Placed BEFORE the tap-and-zoom modifier so it never re-orders
                // ahead of it — the wheel handler consumes Scroll events only,
                // so drag/tap/pinch streams pass through untouched.
                .then(
                    Modifier.playerWheelGestures(
                        isWheelEnabled = { !isScreenLocked && currentSheet == PlayerSheet.None },
                        resolveWheelAction = { axis, isShiftPressed ->
                            PlayerInputPolicy.resolveAction(
                                currentInputMap,
                                PlayerInputPolicy.wheelCandidates(axis, isShiftPressed),
                            )
                        },
                        onVolumeNotch = { direction ->
                            gestureController.onVolumeWheelNotch(direction)
                        },
                        onSeekNotch = { direction ->
                            // The double-tap commit pattern (addOffset + immediate
                            // step seek): wheel seek gets the same "+Ns" chip and
                            // per-notch commit, and the chip auto-resets via the
                            // shared GESTURE_SEEK_LINGER collector.
                            if (direction > 0) {
                                seekState.addOffset(1, currentSeekDurationMs)
                                currentDoSeekForward()
                            } else {
                                seekState.addOffset(-1, currentSeekDurationMs)
                                currentDoSeekBack()
                            }
                            performConfirmHaptic()
                        },
                    )
                )
                .then(
                    Modifier.playerTapAndZoomGestures(
                        gates = inputGates,
                        isScreenLocked = isScreenLocked,
                        doubleTapHoldSeekEnabled = uiState.gestures.doubleTapHoldSeekEnabled,
                        resolveAction = { currentResolvePatternAction(it) },
                        executePlayerAction = { currentExecutePlayerAction(it) },
                        holdRepeatScope = scope,
                        onUserInteraction = { viewModel.onEvent(VideoPlayerUiEvent.UserInteraction) },
                        isHoldSpeedActive = { uiState.gestures.isHoldSpeedActive },
                        holdSpeedEnabled = { uiState.gestures.holdSpeedEnabled },
                        stopHoldSpeed = { viewModel.onEvent(VideoPlayerUiEvent.StopHoldSpeed) },
                        startHoldSpeed = { viewModel.onEvent(VideoPlayerUiEvent.StartHoldSpeed) },
                        toggleControls = { showControls = !showControls },
                        onDoubleTapSeekBack = {
                            seekState.addOffset(-1, currentSeekDurationMs)
                            currentDoSeekBack()
                            performConfirmHaptic()
                        },
                        onDoubleTapSeekForward = {
                            seekState.addOffset(1, currentSeekDurationMs)
                            currentDoSeekForward()
                            performConfirmHaptic()
                        },
                        onDoubleTapCenter = {
                            if (videoZoom > 1f) {
                                videoZoom = 1f
                            } else {
                                currentDoTogglePlayPause()
                                performConfirmHaptic()
                            }
                        },
                        applyZoomDelta = { delta ->
                            videoZoom = (videoZoom * delta).coerceIn(1f, 3f)
                            viewModel.onEvent(VideoPlayerUiEvent.UserInteraction)
                        },
                    )
                ),
        ) {
            // Effective zoom = pinch zoom × TV baseline zoom. Computed once
            // so the video graphicsLayer and the zoom-gated subtitle logic
            // share the exact same value (no drift). > 1 means the video is
            // scaled/cropped, which is when subtitles would move off-screen.
            val tvBaselineZoom = if (isTv && uiState.videoFx.tvZoomModePercent != 0f) {
                1f + (uiState.videoFx.tvZoomModePercent / 100f)
            } else 1f
            // Suppress zoom while in PiP: the pinch-zoomed crop has no meaning in
            // the floating window, and restoring on exit is automatic since the
            // underlying videoZoom state is untouched.
            val effectiveZoom = if (isInPipMode) 1f else videoZoom * tvBaselineZoom
            val zoomed = effectiveZoom > 1f

            // Platform surface seam: Android hosts the engine's
            // SurfaceView (or the empty fallback view for non-View-surface
            // engines — the V2a degrade); desktop hosts the SwingPanel/HWND
            // child window mpv embeds into. Zoom transform + PiP bounds
            // tracking stay with the platform actuals.
            //
            // Composed UNCONDITIONALLY — `engine` is null while the
            // session is still creating one, and the desktop actual mounts its
            // SwingPanel host exactly then: mpv's `wid` captures the embed
            // target at engine construction, so the surface must exist BEFORE
            // the engine factory's bounded wait for its HWND. Inside the old
            // `engine != null` guard the surface and the engine waited on each
            // other (surface composed only once an engine existed; the factory
            // created an engine only once a surface published) and every
            // desktop session fell through to the software-render surface.
            // Per-engine behavior is preserved inside the actuals: Android
            // renders nothing while null (what the former guard did) and keys
            // its view per engine instance; desktop keeps ONE Canvas across
            // the null → engine transition — the remembered embed target must
            // never be swapped under a playing engine.
            EngineVideoSurface(
                engine = engine,
                effectiveZoom = effectiveZoom,
                onSurfaceCreated = { surface ->
                    lastAppliedSubtitleStyle = uiState.subtitleStyle
                    viewModel.onEvent(VideoPlayerUiEvent.ApplySubtitleStyle)
                    playerViewRef = surface
                },
                onSurfaceUpdate = {
                    val currentStyle = uiState.subtitleStyle
                    // Only call applySubtitleStyle for visual style
                    // changes (font/color/margins). Delay changes are
                    // applied live via the engine config path
                    // (setSpuDelay), not through the style-reload path.
                    val lastStyle = lastAppliedSubtitleStyle
                    if (lastStyle == null || styleChangedExcludingDelay(lastStyle, currentStyle)) {
                        lastAppliedSubtitleStyle = currentStyle
                        viewModel.onEvent(VideoPlayerUiEvent.ApplySubtitleStyle)
                    } else if (lastStyle != currentStyle) {
                        // Delay-only change: update the snapshot but
                        // don't trigger the style reload path.
                        lastAppliedSubtitleStyle = currentStyle
                    }
                },
                onBoundsChanged = { left, top, right, bottom ->
                    // Stop tracking once in PiP (the system renders the
                    // window then) — the PiP source-rect hint is only
                    // needed for the pre-PiP layout.
                    if (!isInPipMode) {
                        viewModel.onEvent(VideoPlayerUiEvent.UpdatePipSourceRect(left, top, right, bottom))
                    }
                },
            )

            val currentEngine = engine
            if (currentEngine != null) {
                key(currentEngine) {
                    // Audio-only: keep the surface mounted (playback
                    // uninterrupted) but cover it with a black panel + label.
                    if (uiState.audioOnly) {
                        Surface(
                            color = Color.Black,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.player_audio_only_on),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    // ── Zoom/crop-safe subtitles ──
                    //
                    // The engine declares its zoom-safe subtitle strategy via
                    // [MediaEngine.zoomSafeSubtitleStrategy]; the screen dispatches
                    // on that single value instead of reverse-engineering the
                    // strategy from a pair of capability booleans. Both paths keep
                    // captions pinned to the screen (outside the graphicsLayer
                    // above) so they no longer scale or translate off-screen when
                    // the user pinch-zooms or crops. Both are siblings of the
                    // zoomed video, not children, so they never inherit the
                    // transform. DISABLED (libVLC/External) renders nothing here.
                    when (currentEngine.zoomSafeSubtitleStrategy) {
                        ZoomSafeSubtitleStrategy.NATIVE_PINNED -> {
                            // ExoPlayer: the engine reparents its native
                            // SubtitleView / AssSubtitleView into a sibling host
                            // (platform seam). Full styling/fidelity is preserved
                            // (native rendering, just relocated). Lifetime follows
                            // key(currentEngine): the host detaches before the
                            // engine releases, so no subtitle view orphans in a
                            // host the engine no longer feeds.
                            NativePinnedSubtitleHost(engine = currentEngine)
                        }

                        ZoomSafeSubtitleStrategy.COMPOSE_CUE -> {
                            // mpv: libass composites into the GPU video surface and
                            // can't be reparented, so while zoomed we hide the
                            // native subs and render the live cue line
                            // (engine.liveSubtitleCue) in a Compose overlay. At
                            // zoom == 1 the native libass path renders with full
                            // fidelity.
                            LaunchedEffect(zoomed) {
                                currentEngine.setNativeSubtitlesVisible(!zoomed)
                            }
                            val liveCue by currentEngine.liveSubtitleCue
                                .collectAsStateWithLifecycle()
                            if (zoomed) {
                                ZoomedSubtitleOverlayHost(
                                    cue = liveCue,
                                    style = uiState.subtitleStyle,
                                    viewModel = viewModel,
                                )
                            }
                        }

                        ZoomSafeSubtitleStrategy.DISABLED -> { /* no zoom-safe path */ }
                    }
                }
            }

            PlayerGestureOverlayTier(
                seekState = seekState,
                gestureController = gestureController,
                gestureIndicatorSide = uiState.gestures.gestureIndicatorSide,
                gates = inputGates,
                isScreenLocked = isScreenLocked,
                swipeSeekMaxMs = uiState.gestures.swipeSeekMaxMs,
                showControls = showControls,
                onShowControlsChange = { showControls = it },
                viewModel = viewModel,
                windowOps = windowOps,
                onBack = onBack,
                isHoldSpeedActive = uiState.gestures.isHoldSpeedActive,
                playbackSpeed = uiState.playbackSpeed,
                resolveAction = { currentResolvePatternAction(it) },
                executePlayerAction = { currentExecutePlayerAction(it) },
            )

            // ONE input bundle per tier (the effectsControls idiom): each is
            // remembered on exactly the values its tier reads, so the bundle
            // instance stays equal across recompositions and the tier keeps
            // skipping until a value it actually renders flips.
            val centerOverlayInputs = remember(
                uiState.uiPrefs.trickplayOnSeekGesture,
                gestureTrickplayVisible,
                gestureTrickplayBitmap,
                gestureSeekPositionMs,
                gestureDeltaMs,
                duration,
                isCinemaIntroVisible,
                tvCinemaIntroFocusRequester,
                activeSegment,
                activeSegmentBehavior,
                shouldShowUpNext,
                isInPipMode,
                tvSkipSegmentFocusRequester,
                nextEpisode,
                nextEpisodeImageUrl,
                isNextEpisodeLoading,
                tvNextEpisodeFocusRequester,
                isPlaying,
                currentSheet != PlayerSheet.None,
                isScreenLocked,
                playbackIntended,
                stillWatchingPrompt,
            ) {
                CenterOverlayInputs(
                    trickplayOnSeekGesture = uiState.uiPrefs.trickplayOnSeekGesture,
                    gestureTrickplayVisible = gestureTrickplayVisible,
                    gestureTrickplayBitmap = gestureTrickplayBitmap,
                    gestureSeekPositionMs = gestureSeekPositionMs,
                    gestureDeltaMs = gestureDeltaMs,
                    durationMs = duration,
                    isCinemaIntroVisible = isCinemaIntroVisible,
                    tvCinemaIntroFocusRequester = tvCinemaIntroFocusRequester,
                    activeSegment = activeSegment,
                    activeSegmentBehavior = activeSegmentBehavior,
                    shouldShowUpNext = shouldShowUpNext,
                    isInPipMode = isInPipMode,
                    tvSkipSegmentFocusRequester = tvSkipSegmentFocusRequester,
                    nextEpisode = nextEpisode,
                    nextEpisodeImageUrl = nextEpisodeImageUrl,
                    isNextEpisodeLoading = isNextEpisodeLoading,
                    tvNextEpisodeFocusRequester = tvNextEpisodeFocusRequester,
                    isPlaying = isPlaying,
                    isSheetOpen = currentSheet != PlayerSheet.None,
                    isScreenLocked = isScreenLocked,
                    playbackIntended = playbackIntended,
                    stillWatchingPrompt = stillWatchingPrompt,
                )
            }

            PlayerCenterOverlayTier(
                viewModel = viewModel,
                uiState = uiState,
                inputs = centerOverlayInputs,
                performConfirmHaptic = performConfirmHaptic,
            )

            if (isScreenLocked && !isInPipMode) {
                PlayerLockOverlayTier(
                    usePinForPlayerLock = uiState.uiPrefs.usePinForPlayerLock,
                    hasPin = uiState.uiPrefs.hasPin,
                    viewModel = viewModel,
                    onUnlock = {
                        viewModel.onEvent(VideoPlayerUiEvent.SetScreenLocked(false))
                        showControls = true
                    },
                )
            }

            // Controller-owned slices — collected here, at their
            // consumption points, instead of flowing through the residual
            // uiState. Each is low-frequency; collecting per-leaf keeps the
            // root scope unaffected.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            val effectsState by viewModel.playbackSession.effects.state.collectAsStateWithLifecycle()
            val sleepTimer by viewModel.playbackSession.sleepTimer.state.collectAsStateWithLifecycle()
            val abRepeat by viewModel.playbackSession.abRepeat.state.collectAsStateWithLifecycle()
            val syncPlay by viewModel.playbackSession.syncPlay.state.collectAsStateWithLifecycle()

            val statusOverlayInputs = remember(
                duration,
                playbackSpeed,
                isPlaying,
                effectsState.decoderMode,
                engine,
                isCastConnected,
                isCastConnecting,
                snackbarHostState,
                resumeChipHostState,
                detectedAspectRatio,
                aspectRatio,
                videoZoom,
            ) {
                StatusOverlayInputs(
                    durationMs = duration,
                    playbackSpeed = playbackSpeed,
                    isPlaying = isPlaying,
                    decoderMode = effectsState.decoderMode,
                    engine = engine,
                    isCastConnected = isCastConnected,
                    isCastConnecting = isCastConnecting,
                    snackbarHostState = snackbarHostState,
                    resumeChipHostState = resumeChipHostState,
                    detectedAspectRatio = detectedAspectRatio,
                    aspectRatio = aspectRatio,
                    videoZoom = videoZoom,
                )
            }

            PlayerStatusOverlayTier(
                viewModel = viewModel,
                uiState = uiState,
                inputs = statusOverlayInputs,
            )

            val hasEpisodes = uiState.episodes.seriesSeasons.isNotEmpty() && uiState.episodes.seasonEpisodes.isNotEmpty()
            val episodeBrowserEnabled = uiState.episodes.videoEpisodeBrowserEnabled
            // Previous/Next center-button availability. Derived from the
            // adjacency snapshot fetchAdjacentEpisodes writes alongside
            // nextEpisode, so these stay consistent with the up-next overlay.
            val hasPreviousEpisode = uiState.episodes.previousEpisode != null
            val hasNextEpisode = uiState.episodes.nextEpisode != null

            // Hoist PlayerControls callbacks into remembered lambdas.
            // Each fresh `{ ... }` passed inline below allocated a new lambda
            // per recomposition, defeating PlayerControls' skippability and
            // forcing the 1500-line controls tree to recompose on every
            // position tick. The lambdas below capture only stable handles
            // — viewModel (same Hilt instance for the screen's lifetime) and
            // the rememberSaveable property delegates (currentSheet,
            // showControls, isSeeking, controlsHasFocus, isOverflowMenuOpen)
            // whose MutableState references are stable across recomposition —
            // so they need no keys. The few that capture a value (onSeekEnd)
            // are keyed on exactly that value so they recreate only when it
            // actually changes.
            //
            // The ~13 controls that do nothing but open a sheet collapse
            // into the ONE remembered opener below: PlayerControls
            // takes it as its single openSheet param, so a new sheet entry
            // is a PlayerSheet arm — not another remembered lambda here AND
            // another no-arg param there (the skippability rationale above
            // is why the opener must stay remembered, not inline).
            val openSheet by remember { mutableStateOf({ sheet: PlayerSheet -> currentSheet = sheet }) }
            val onPlayPause by remember(doTogglePlayPause) { mutableStateOf({ doTogglePlayPause() }) }
            val onPreviousEpisode by remember { mutableStateOf({ viewModel.onEvent(VideoPlayerUiEvent.PlayPreviousEpisode) }) }
            val onNextEpisode by remember { mutableStateOf({ viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode) }) }
            val onSeekEnd by remember(duration) {
                mutableStateOf({
                    isSeeking = false
                    if (duration > 0) doSeekTo(seekPositionMs)
                })
            }
            val onSeekStart by remember { mutableStateOf({ isSeeking = true }) }
            val onSeekPositionChange by remember { mutableStateOf({ positionMs: Long -> seekPositionMs = positionMs }) }
            // Primary subtitle button opens the hub on the Tracks tab. No
            // reset (deliberate — reopening on the same item keeps prior
            // search state) and no loads here: the router's LaunchedEffect
            // is the sheet's single openSubtitleHub trigger, so the hub no
            // longer double-fetches the server-default list (fetch starts at
            // composition rather than at click; the hub's loading spinner
            // already covers the in-flight window).
            val onSubtitleClick by remember { mutableStateOf({
                subtitleHubResetFirst = false
                subtitleHubInitialTab = SubtitleHubTab.TRACKS
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            // Overflow "Subtitles" entry: opens the hub with a cleared search /
            // cultures slice (the former "Get Subtitles" entry point), landing
            // on the same Tracks tab as the primary button — only the reset
            // intent differs.
            val onSubtitleHubClick by remember { mutableStateOf({
                // Reset search/cultures state from any previous item before
                // loading fresh data, so stale results don't leak across
                // items. The reset rides to the router's single load trigger
                // as the flag below — no loads at click (see onSubtitleClick).
                subtitleHubResetFirst = true
                subtitleHubInitialTab = SubtitleHubTab.TRACKS
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            // Metadata row's mpv-config chip: the ownership snapshot is live
            // (user-owned sub-* keys), and the hub's Style tab carries the
            // full case-by-case notice — land there directly.
            val onMpvConfigNoticeClick by remember { mutableStateOf({
                subtitleHubResetFirst = false
                subtitleHubInitialTab = SubtitleHubTab.STYLE
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            // ONE effects bundle replaces the nine remembered effect lambdas
            // and PlayerControls' eighteen flat effects params. Keys are
            // exactly the bundle's value fields — the callbacks capture only
            // viewModel and the effectsState delegate (read at invocation
            // time, so those lambdas never go stale), so the bundle instance
            // is equal across recompositions and PlayerControls keeps
            // skipping until an effects value actually flips.
            val effectsControls = remember(
                uiState.dialogueBoostEnabled,
                uiState.dialogueBoostStrength,
                effectsState.nightModeEnabled,
                effectsState.nightModeStrength,
                effectsState.audioPassthrough,
                effectsState.audioNormalizationMode,
                effectsState.audioNormalizationEnabled,
                effectsState.channelMixMode,
                effectsState.channelMixEnabled,
            ) {
                PlayerEffectsControls(
                    dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                    dialogueBoostStrength = uiState.dialogueBoostStrength,
                    nightModeEnabled = effectsState.nightModeEnabled,
                    nightModeStrength = effectsState.nightModeStrength,
                    audioPassthrough = effectsState.audioPassthrough,
                    audioNormalizationMode = effectsState.audioNormalizationMode,
                    audioNormalizationEnabled = effectsState.audioNormalizationEnabled,
                    channelMixMode = effectsState.channelMixMode,
                    channelMixEnabled = effectsState.channelMixEnabled,
                    onDialogueBoostClick = { viewModel.onEvent(VideoPlayerUiEvent.ToggleDialogueBoost) },
                    onDialogueBoostStrengthChange = { strength -> viewModel.onEvent(VideoPlayerUiEvent.SetDialogueBoostStrength(strength)) },
                    onNightModeClick = { viewModel.playbackSession.effects.toggleNightMode() },
                    onNightModeStrengthChange = { strength -> viewModel.playbackSession.effects.setNightModeStrength(strength) },
                    onPassthroughClick = { viewModel.playbackSession.effects.setAudioPassthrough(!effectsState.audioPassthrough) },
                    onAudioNormalizationClick = { viewModel.playbackSession.effects.toggleAudioNormalization() },
                    onAudioNormalizationModeChange = { mode -> viewModel.playbackSession.effects.setAudioNormalizationMode(mode) },
                    onChannelMixClick = { viewModel.playbackSession.effects.toggleChannelMix() },
                    onChannelMixModeChange = { mode -> viewModel.playbackSession.effects.setChannelMixMode(mode) },
                )
            }
            val onPipClick by remember(onEnterPip) { mutableStateOf({ onEnterPip() }) }
            val onMuteClick by remember { mutableStateOf({ viewModel.onEvent(VideoPlayerUiEvent.ToggleMute) }) }
            val onVideoStatsClick by remember { mutableStateOf({ viewModel.onEvent(VideoPlayerUiEvent.ToggleVideoStats) }) }
            // The capture lambda itself lives above [actionExecutor] —
            // the SCREENSHOT arm rides the same funnel (see the hoisted
            // onScreenshotClick).
            val onLockClick by remember { mutableStateOf({
                viewModel.onEvent(VideoPlayerUiEvent.SetScreenLocked(true))
                showControls = false
            }) }
            val onControlsFocusChange by remember { mutableStateOf({ hasFocus: Boolean -> controlsHasFocus = hasFocus }) }
            val onOverflowMenuChange by remember { mutableStateOf({ open: Boolean -> isOverflowMenuOpen = open }) }

            // Remote "TakeScreenshot": the receiver emits through the
            // active-engine registry while this screen's engine is bound;
            // each request drives the SAME capture path as the overflow-menu
            // screenshot button above (no separate remote code path to
            // drift). Keyed on the remembered action so a recomposition that
            // rebuilds the lambda re-subscribes with the current captures.
            androidx.compose.runtime.LaunchedEffect(onScreenshotClick) {
                viewModel.remoteScreenshotRequests.collect { onScreenshotClick() }
            }

            // Transparent VLC-style subtitle-delay overlay. Sits over the video
            // (below the control chrome) so the user can watch subtitles shift.
            // Passes empty-space taps through to the host gesture layer.
            if (showDelayOverlay && !isInPipMode && !isScreenLocked) {
                PlayerSubtitleDelayOverlay(
                    currentDelayMs = uiState.subtitleStyle.offsetMs,
                    onChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSubtitleDelay(it)) },
                    onDismiss = { showDelayOverlay = false },
                )
            }

            // Control bars always render as a dark "chrome zone" (dark scrim + light text/icons)
            // because they float over arbitrary video content, regardless of the app theme.
            // Drawers and other surfaces outside this wrapper still respect the ambient theme.
            PlayerDarkTheme {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                currentPositionFlow = viewModel.currentPositionMs,
                duration = duration,
                bufferedRangesFlow = viewModel.bufferedRanges,
                videoStatsFlow = viewModel.videoStats,
                playbackSpeed = playbackSpeed,
                chapters = uiState.chapters,
                effectsControls = effectsControls,
                segments = uiState.segmentState.segments,
                transport = TransportControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onSeekStart = onSeekStart,
                    onSeekEnd = onSeekEnd,
                    onSeekPositionChange = onSeekPositionChange,
                    hasPreviousEpisode = hasPreviousEpisode,
                    hasNextEpisode = hasNextEpisode,
                    onPreviousEpisode = onPreviousEpisode,
                    onNextEpisode = onNextEpisode,
                    tvTrickplayBitmap = if (isTv) tvTrickplayBitmap else null,
                    isMuted = uiState.isMuted,
                    onMuteClick = onMuteClick,
                ),
                gestures = GestureControls(
                    onBack = onBack,
                    onLockClick = onLockClick,
                    onPipClick = onPipClick,
                    onToggleOrientation = toggleOrientation,
                    onControlRowScrolled = {
                        userInteractionCount++
                        viewModel.onEvent(VideoPlayerUiEvent.UserInteraction)
                    },
                    onControlsFocusChange = onControlsFocusChange,
                    onOverflowMenuChange = onOverflowMenuChange,
                ),
                sheets = SheetControls(
                    openSheet = openSheet,
                    onSubtitleClick = onSubtitleClick,
                    onSubtitleHubClick = onSubtitleHubClick,
                    onMpvConfigNoticeClick = onMpvConfigNoticeClick,
                    onSubtitleToggle = { viewModel.onEvent(VideoPlayerUiEvent.ToggleSubtitles) },
                    onSubtitleDelayClick = { showDelayOverlay = true },
                    hasEpisodes = hasEpisodes,
                    episodeBrowserEnabled = episodeBrowserEnabled,
                    showVideoStats = uiState.uiPrefs.showVideoStats,
                    onVideoStatsClick = onVideoStatsClick,
                    videoFiltersActive = !uiState.videoFx.videoEffects.isNeutral,
                    onScreenshotClick = onScreenshotClick,
                    onAbRepeatToggle = { viewModel.playbackSession.abRepeat.setEnabled(!abRepeat.enabled) },
                    onAbRepeatSetA = { viewModel.playbackSession.abRepeat.setPointA() },
                    onAbRepeatSetB = { viewModel.playbackSession.abRepeat.setPointB() },
                    onAbRepeatClear = { viewModel.playbackSession.abRepeat.clear() },
                    audioOnly = uiState.audioOnly,
                    onToggleAudioOnly = { viewModel.onEvent(VideoPlayerUiEvent.ToggleAudioOnly) },
                    incognitoModeEnabled = viewModel.incognitoModeEnabled,
                    onMarkWatchedAndSkip = { viewModel.onEvent(VideoPlayerUiEvent.MarkWatchedAndSkip) },
                    onMarkUnwatchedAndQuit = { viewModel.onEvent(VideoPlayerUiEvent.MarkUnwatchedAndQuit) },
                    // the Rendering sheet is an mpv surface (shader packs /
                    // tone mapping / quality); the deinterlace item gates on
                    // the engine capability matrix.
                    supportsRenderPanel = uiState.preferredPlayerType == PlayerType.MPV,
                    onRenderClick = { openSheet(PlayerSheet.Render) },
                    supportsDeinterlace = uiState.engineCapabilities.supportsDeinterlace,
                    deinterlaceMode = if (uiState.engineCapabilities.supportsDeinterlace) {
                        viewModel.playbackSession.sessionRender.deinterlace
                    } else {
                        null
                    },
                    onDeinterlaceCycle = { viewModel.onEvent(VideoPlayerUiEvent.CycleDeinterlace) },
                    onVersionClick = { openSheet(PlayerSheet.Version) },
                ),
                tracks = TrackControls(
                    streamingQuality = uiState.uiPrefs.streamingQuality,
                    playbackMode = uiState.uiPrefs.playbackMode,
                    playMethod = uiState.media.playMethod,
                    isDirectPlayForced = uiState.media.isDirectPlayForced,
                    hdrType = uiState.hdrType,
                    mediaStreams = uiState.media.mediaStreams,
                    audioTracks = trackState.audioTracks,
                    isConnectionMetered = uiState.isConnectionMetered,
                    subtitleDelayMs = uiState.subtitleStyle.offsetMs,
                    showPlaybackMetadata = uiState.uiPrefs.showPlaybackMetadata,
                    hasMultipleVersions = uiState.media.mediaSources.size > 1,
                    mpvConfigNoticeActive = engine?.subtitleStyleOwnership?.isActive == true,
                ),
                currentAspectRatio = aspectRatio,
                detectedAspectRatio = detectedAspectRatio,
                isVisible = showControls && !isInPipMode && !isScreenLocked,
                capabilities = uiState.engineCapabilities,
                syncPlay = SyncPlayIndicator(
                    inSession = isInSyncPlaySession,
                    groupName = syncPlay.syncPlayGroupName,
                    participantCount = syncPlay.syncPlayParticipantCount,
                    isSynced = syncPlay.isSyncPlaySynced,
                    isSyncing = syncPlay.isSyncPlaySyncing,
                ),
                sleepTimer = SleepTimerControls(
                    active = sleepTimer.sleepTimerActive,
                    endOfEpisode = sleepTimer.sleepTimerEndOfEpisode,
                    remainingFlow = viewModel.playbackSession.sleepTimer.remainingMs,
                ),
                abRepeat = abRepeat,
                castManager = viewModel.platformCastManager,
                inputQuickToggles = inputQuickToggles,
                onInputQuickToggle = onInputQuickToggle,
                showClock = uiState.uiPrefs.showClock,
                showTimeRemaining = uiState.uiPrefs.showTimeRemaining,
                tvSkipSegmentFocusRequester = tvSkipSegmentFocusRequester,
                tvNextEpisodeFocusRequester = tvNextEpisodeFocusRequester,
                isSkipSegmentVisible = isSkipSegmentVisible,
                isNextEpisodeVisible = isNextEpisodeVisible,
                modifier = Modifier.fillMaxSize(),
            )
            } // end PlayerDarkTheme (control bars)

            PlayerSeekScrubTrickplayOverlay(
                isTv = isTv,
                trickplayEnabled = uiState.uiPrefs.trickplayEnabled,
                showControls = showControls,
                isSeeking = isSeeking,
                bitmap = seekTrickplayBitmap,
                positionMs = seekPositionMs,
                durationMs = duration,
            )

            // Full-screen loading overlay during the initial media load. Covers
            // the surface + controls so the seek bar never paints a transient 0
            // fraction — its first paint (once this lifts) is the correct resume
            // fraction. Declared last → drawn on top of every sibling. Lifts
            // when isInitializing flips false (position & duration seeded).
            if (uiState.isInitializing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    JellyPlayLoadingIndicator(color = playerOnScrim())
                }
            }
        }
    }

    // Single long-lived collector replaces a fresh LaunchedEffect keyed on
    // seekState.timestamp (which changes per D-pad seek → coroutine create/cancel
    // per event, thrashing during hold-and-repeat seeking). The reset side-effect
    // body is unchanged; only the dispatch mechanism changes.
    LaunchedEffect(Unit) {
        snapshotFlow { seekState.timestamp to seekState.direction }
            .filter { (_, direction) -> direction != 0 }
            .collectLatest {
                delay(GESTURE_SEEK_LINGER_MS)
                seekState.reset()
            }
    }

    // Keyboard seek chip commit: the debounced twin of the D-pad's
    // commit-on-key-up. Every seek key-down bumps the executor's
    // [PlayerActionExecutor.keyboardSeekStamp], and collectLatest restarts
    // this delay, so the commit fires exactly once —
    // KEYBOARD_SEEK_COMMIT_DELAY_MS after the LAST key-down (key-up is not
    // observable on the desktop sink bridge, which delivers KeyDown only).
    // Commits route through the same seekState → doSeekTo funnel the D-pad
    // uses, then the chip lingers out via the reset effect above. One
    // long-lived collector, not a per-press LaunchedEffect — the same
    // dispatch-shape reason the D-pad linger effect above cites.
    LaunchedEffect(Unit) {
        snapshotFlow { actionExecutor.keyboardSeekStamp }
            .filter { it > 0 }
            .collectLatest {
                delay(KEYBOARD_SEEK_COMMIT_DELAY_MS)
                // Stand down if the chip moved on after the last keyboard
                // contribution (drag/swipe/double-tap seek inside the commit
                // window) — a commit from the keyboard's stale base position
                // would yank playback back over the newer seek.
                if (seekState.timestamp != actionExecutor.lastKeyboardSeekChipTimestamp) {
                    return@collectLatest
                }
                when (seekState.direction) {
                    1 -> {
                        seekState.commitForward()
                        performConfirmHaptic()
                    }
                    -1 -> {
                        seekState.commitBackward()
                        performConfirmHaptic()
                    }
                }
            }
    }

    LaunchedEffect(Unit) {
        // Seek-bar / D-pad scrub feed: the seek overlay bitmap plus the TV
        // mirror (TV renders no gesture overlay, so the seek-bar thumbnail
        // IS its scrub preview).
        collectTrickplayThumbnails(
            positionMs = { seekPositionMs },
            gate = { isSeeking && uiState.uiPrefs.trickplayEnabled && uiState.uiPrefs.trickplayInfo != null },
            trickplayInfo = { uiState.uiPrefs.trickplayInfo },
            onFetch = { pos, _ ->
                val bitmap = viewModel.loadTrickplayThumbnail(pos) as? PlatformBitmap
                seekTrickplayBitmap = bitmap
                if (isTv) {
                    tvTrickplayBitmap = bitmap
                }
            },
        )
    }

    LaunchedEffect(isSeeking) {
        if (!isSeeking) {
            seekTrickplayBitmap = null
            tvTrickplayBitmap = null
        }
    }

    LaunchedEffect(Unit) {
        // Gesture-swipe feed: unlike the seek feed, its gate does NOT include
        // trickplayInfo != null — a null info with the gesture active still
        // flips the overlay visible (with a null bitmap) so the swipe preview
        // placeholder shows; the onFetch body encodes that divergence.
        collectTrickplayThumbnails(
            positionMs = { gestureSeekPositionMs },
            gate = { isGestureSeeking && uiState.uiPrefs.trickplayOnSeekGesture },
            trickplayInfo = { uiState.uiPrefs.trickplayInfo },
            onFetch = { pos, trickplayInfo ->
                gestureTrickplayVisible = true
                gestureTrickplayBitmap = if (trickplayInfo != null) {
                    viewModel.loadTrickplayThumbnail(pos) as? PlatformBitmap
                } else {
                    null
                }
            },
        )
    }

    LaunchedEffect(isGestureSeeking) {
        if (!isGestureSeeking && gestureTrickplayVisible) {
            delay(1000)
            gestureTrickplayVisible = false
            gestureTrickplayBitmap = null
        }
    }

    LaunchedEffect(showControls, controlsHasFocus, isSeeking, currentSheet, isOverflowMenuOpen, userInteractionCount) {
        if (
            shouldScheduleControlsAutoHide(
                showControls = showControls,
                isSeeking = isSeeking,
                isSheetOpen = currentSheet != PlayerSheet.None,
                isOverflowMenuOpen = isOverflowMenuOpen,
                isTv = isTv,
                controlsHasFocus = controlsHasFocus,
            )
        ) {
            delay(controlsAutoHideTimeoutMs(uiState.uiPrefs.controlsTimeoutMs, isTv))
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            // Platform cast-session events (Connected → castToDevice,
            // Disconnected → onCastDisconnected) — inert on desktop.
            launchPlatformCastSessionEvents(viewModel)
            launch {
                viewModel.playbackSession.syncPlay.notifications.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                }
            }
            launch {
                viewModel.passOutEvents.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    val sheetRouterInputs = remember(
        itemId,
        syncPlayIgnoreWait,
        onOpenSubtitleTester,
        subtitleHubResetFirst,
        subtitleHubInitialTab,
    ) {
        SheetRouterInputs(
            onSheetChange = { sheet -> currentSheet = sheet },
            dismissSheet = dismissSheet,
            currentPositionFlow = viewModel.currentPositionMs,
            sleepTimerRemainingFlow = viewModel.playbackSession.sleepTimer.remainingMs,
            doSeekTo = doSeekTo,
            itemId = itemId,
            syncPlayIgnoreWait = syncPlayIgnoreWait,
            onLoadLocalSubtitle = {
                localSubtitlePicker()
            },
            onPickFont = {
                fontPicker()
            },
            onOpenSubtitleTester = onOpenSubtitleTester,
            onOpenSubtitleDelayOverlay = {
                currentSheet = PlayerSheet.None
                showDelayOverlay = true
            },
            subtitleHubResetFirst = subtitleHubResetFirst,
            onSubtitleHubResetConsumed = { subtitleHubResetFirst = false },
            subtitleHubInitialTab = subtitleHubInitialTab,
        )
    }

    PlayerSheetRouter(
        currentSheet = currentSheet,
        uiState = uiState,
        inputs = sheetRouterInputs,
        viewModel = viewModel,
    )

    val playerError = uiState.playerError
    if (uiState.showPlaybackErrorDialog && playerError != null) {
        PlaybackErrorDialog(
            errorMessage = playerError,
            currentPlayerType = uiState.preferredPlayerType,
            retryable = uiState.playerErrorRetryable,
            onRetry = { viewModel.onEvent(VideoPlayerUiEvent.RetryPlayback) },
            onRetryWithEngine = { viewModel.onEvent(VideoPlayerUiEvent.RetryWithEngine(it)) },
            onDismiss = { viewModel.onEvent(VideoPlayerUiEvent.DismissPlaybackError) },
            transcodeReasons = rememberFormattedTranscodeReasons(uiState.media.transcodeReasons),
        )
    }
}

/** Cast-connected branch: the companion dashboard replaces the video surface entirely (track slice collected branch-locally). */
@Composable
private fun CastCompanionDashboardBranch(
    viewModel: VideoPlayerViewModel,
    title: String,
    subtitle: String,
    uiState: VideoPlayerUiState,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    durationMs: Long,
    doTogglePlayPause: () -> Unit,
    doSeekBack: () -> Unit,
    doSeekForward: () -> Unit,
    doSeekTo: (Long) -> Unit,
    onToggleOrientation: () -> Unit,
    disconnectCast: () -> Unit,
) {
    // Track slice — collected here (the cast dashboard is the
    // only consumer on this branch) rather than through the residual uiState.
    val trackState by viewModel.trackState.collectAsStateWithLifecycle()
    CompanionDashboard(
        title = title,
        subtitle = subtitle,
        overview = uiState.media.overview,
        people = uiState.media.people,
        lyricsLines = uiState.media.lyricsLines,
        artworkUrl = uiState.media.artworkUrl,
        isPlaying = isPlaying,
        castPositionFlow = viewModel.playbackSession.cast.castPositionMs,
        castVolumeFlow = viewModel.playbackSession.cast.castVolumeFlow,
        durationMs = durationMs,
        isConnecting = isCastConnecting,
        audioTracks = trackState.audioTracks,
        subtitleTracks = trackState.subtitleTracks,
        episodes = uiState.episodes.seasonEpisodes,
        onPlayPause = doTogglePlayPause,
        onSeekBack = doSeekBack,
        onSeekForward = doSeekForward,
        onSeekTo = doSeekTo,
        onVolumeChange = { vol -> viewModel.playbackSession.cast.setCastVolume(vol) },
        onDisconnect = { viewModel.playbackSession.cast.onCastDisconnected(); disconnectCast() },
        onSelectAudioTrack = { viewModel.onEvent(VideoPlayerUiEvent.SelectAudioTrack(it)) },
        onSelectSubtitleTrack = { viewModel.onEvent(VideoPlayerUiEvent.SelectSubtitleTrack(it)) },
        onPlayEpisode = { epId -> viewModel.onEvent(VideoPlayerUiEvent.PlayEpisode(epId)) },
        getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
        onToggleOrientation = onToggleOrientation,
        modifier = Modifier.fillMaxSize()
    )
}
