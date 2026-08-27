package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_restart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.rememberDpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio_only_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_resumed_message
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_a_set
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_active
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_cleared
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ab_repeat_badge_enabled
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_auto
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_font_invalid_format
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_audio_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_subtitle_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_subtitles_off













import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController
import com.raulshma.jellyplay.feature.player.video.engine.styleChangedExcludingDelay
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.AVSyncSheet
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.EpisodePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.SegmentSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.RememberPreferenceToggle
import com.raulshma.jellyplay.feature.player.video.components.PlaybackErrorDialog
import com.raulshma.jellyplay.feature.player.video.components.QualityPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.PlaybackModeSheet
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDelayOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTab
import com.raulshma.jellyplay.feature.player.video.components.SubtitleManagerSheet
import com.raulshma.jellyplay.feature.player.video.components.CastIndicatorOverlay
import com.raulshma.jellyplay.feature.player.video.components.CompanionDashboard
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.PinLockOverlay
import com.raulshma.jellyplay.feature.player.video.components.SlideToUnlockOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlayerControls
import com.raulshma.jellyplay.feature.player.video.components.SpeedPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SleepTimerSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleStyleSheet
import com.raulshma.jellyplay.feature.player.video.components.VideoStatsOverlay
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayPlayerSheet

import com.raulshma.jellyplay.feature.player.video.components.TrackPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import com.raulshma.jellyplay.feature.player.video.components.VideoFilterSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PlayerDarkTheme
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens

// ── Player overlay/animation timing (ms) ─────────────────────────────────
// Named so the tuning is discoverable instead of scattered as bare literals.
/** How long the gesture-seek ripple/indicator lingers after the last seek input. */
private const val GESTURE_SEEK_LINGER_MS = 800L
/** How long the AutoAspectRatio badge is shown before auto-dismissing. */
private const val ASPECT_BADGE_DURATION_MS = 5_000L

/** How long the zoom badge is shown before auto-dismissing. */
private const val ZOOM_BADGE_DURATION_MS = 2_000L
/** How long A/B repeat confirmation badges (point captured, loop active, cleared) are shown. */
private const val AB_REPEAT_BADGE_DURATION_MS = 3_000L
/** How long A/B repeat step-guidance badges (enable hint, A-set hint) linger — they instruct the next action. */
private const val AB_REPEAT_BADGE_HINT_DURATION_MS = 4_000L

/** "Resumed — tap to restart" chip lifetime (3s). */
private const val RESUME_CHIP_DISPLAY_MS = 3_000L

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
/** Hold-speed pill offset above the bottom controls. */
private const val HOLD_SPEED_PILL_BOTTOM_CLEARANCE_DP = 180
/** Trickplay thumbnail offset above the bottom controls. */
private const val TRICKPLAY_THUMB_BOTTOM_CLEARANCE_DP = 120

/**
 * Platform seam (wave 9A): nudging the system media (STREAM_MUSIC) volume for
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
    // Host-window + input seams (wave 9A): the Activity/Context system-surface
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
    val resumedMessage = stringResource(Res.string.player_resumed_message)
    val restartLabel = stringResource(CoreUiRes.string.core_restart)
    // Resume-position marker on the seekbar (§2.2). Mirrors the resume chip:
    // the value comes from viewModel.resumeReminder, collected once per screen.
    // Keyed on itemId so the marker clears when switching media (the VM is
    // Activity-scoped and reused); otherwise the previous item's resume tick
    // persists until the new item emits its own resumeReminder.
    var resumePositionMs by remember(itemId) { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { viewModel.resumeReminder.collect { resumePositionMs = it } }
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
                    viewModel.restartPlayback()
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
    // Platform seam (wave 9A): the Configuration read lives in the androidMain
    // actual; desktop always reports true.
    val hasHardwareKeyboard = rememberHasHardwareKeyboard()

    val tvPlayerFocusRequester = remember { FocusRequester() }
    val tvSkipSegmentFocusRequester = remember { FocusRequester() }
    val tvCinemaIntroFocusRequester = remember { FocusRequester() }
    val tvNextEpisodeFocusRequester = remember { FocusRequester() }
    val keyboardFocusRequester = remember { FocusRequester() }
    var userInteractionCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(showControls) {
        viewModel.setControlsVisible(showControls)
    }

    val isScreenLocked = uiState.isScreenLocked

    // Mirror the screen-lock state to PipController so the host Activity can gate
    // PiP auto-entry while the controls are locked
    LaunchedEffect(isScreenLocked) {
        viewModel.pipController.setControlsLocked(isScreenLocked)
    }

    val localSubtitlePicker = rememberDocumentPicker(
        mimeTypes = arrayOf(
            "application/x-subrip",
            "text/vtt",
            "text/plain",
            "text/x-ssa",
            "application/ttml+xml",
        ),
    ) { uriString: String? ->
        if (uriString != null) {
            val fileName = pickedDocumentDisplayName(uriString) ?: "subtitle.srt"
            viewModel.subtitles.addLocalSubtitle(uriString, fileName)
            currentSheet = PlayerSheet.None
        }
    }

    val fontInvalidFormatMessage = stringResource(Res.string.player_video_font_invalid_format)
    val fontPicker = rememberDocumentPicker(
        mimeTypes = arrayOf("*/*"),
    ) { uriString: String? ->
        if (uriString != null) {
            val name = pickedDocumentDisplayName(uriString)?.lowercase().orEmpty()
            val isFont = name.endsWith(".ttf") || name.endsWith(".otf")
            if (isFont) {
                viewModel.installUserFont(uriString)
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
        if (viewModel.cast.isBackgroundCasting) {
            viewModel.reattachFromBackgroundCast()
        } else {
            viewModel.initialize(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                startPositionTicks = startPositionTicks,
                subtitleStreamIndex = subtitleStreamIndex,
                audioStreamIndex = audioStreamIndex,
            )
        }
    }

    // Observe PiP dismiss as a StateFlow boolean. Using StateFlow (instead of SharedFlow)
    // ensures the dismiss signal survives lifecycle STOPPED→STARTED transitions.
    // The old SharedFlow approach lost the event because LaunchedEffect's coroutine is
    // cancelled during STOPPED and SharedFlow(replay=0) doesn't replay to new subscribers.
    val pipDismissed by viewModel.pipController.pipDismissed.collectAsStateWithLifecycle()
    LaunchedEffect(pipDismissed) {
        if (pipDismissed) {
            viewModel.pipController.clearPipDismissed()
            onBack()
        }
    }
    // Capture the latest onBack via rememberUpdatedState — the collector
    // below keys on Unit, so without this the screen keeps invoking the
    // onBack lambda captured at first composition (a nav lambda that may have
    // been rebuilt by the parent).
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(Unit) {
        viewModel.closePlayer.collect { currentOnBack() }
    }
    // Restore immersive mode when leaving PiP
    LaunchedEffect(isInPipMode) {
        if (!isInPipMode) {
            windowOps.hideSystemBars()
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isWindowFocused = rememberUpdatedState(windowInfo.isWindowFocused)
    LaunchedEffect(windowOps) {
        snapshotFlow { isWindowFocused.value }.distinctUntilChanged().collect { focused ->
            // Skip the immersive re-hide while in PiP (or mid-transition into
            // it): PlayerActivity.onPipModeChanged shows the bars on PiP entry
            // to force the relayout that anchors the gesture-nav handle at the
            // bottom. Without this guard the window-focus flip during the PiP
            // transition re-hides them here, defeating that fix and leaving the
            // handle floating mid-screen. Uses the host's authoritative
            // isInPictureInPictureMode flag (synchronously current, unlike the
            // collected isInPipMode state which lags a frame).
            if (focused && !windowOps.isInPipMode) {
                windowOps.hideSystemBars()
            }
        }
    }

    // External-player handoff is handled centrally by the app-level
    // ActivityResultLauncher in JellyPlayApp's navigateFilter, which reads the
    // external player's returned position and credits watched progress. This
    // screen is never composed for the EXTERNAL case (navigation is intercepted
    // before reaching it), so no local launch logic is needed here.

    // Guard against releasing the engine when the composable is torn down
    // during a PiP transition. The engine must survive until PiP is dismissed.

    DisposableEffect(Unit) {
        windowOps.hideSystemBars()

        onDispose {
            val currentlyInPip = viewModel.pipController.isInPipMode.value
            val isBgCasting = viewModel.cast.isCastConnected && viewModel.cast.castIsPlaying.value &&
                viewModel.cast.backgroundCastingEnabled
            val restoreOrientation = if (isTv)
                PlayerOrientationLock.TV_LANDSCAPE
            else PlayerOrientationLock.UNSPECIFIED
            // restoreOnPlayerExit bundles the host-window teardown the screen
            // used to do inline: unlock orientation, clear FLAG_KEEP_SCREEN_ON,
            // restore OS-default brightness, re-show the system bars and hand
            // the display mode back (all host-alive guarded on Android).
            if (isBgCasting && !currentlyInPip) {
                windowOps.restoreOnPlayerExit(restoreOrientation)
                playerViewRef = null
                viewModel.detachForBackgroundCast()
            } else if (!currentlyInPip) {
                windowOps.restoreOnPlayerExit(restoreOrientation)
                playerViewRef = null
                viewModel.release()
            }
        }
    }

    LaunchedEffect(uiState.isPlaying, uiState.uiPrefs.keepScreenOnDuringVideo) {
        windowOps.setKeepScreenOn(uiState.isPlaying && uiState.uiPrefs.keepScreenOnDuringVideo)
    }



    LaunchedEffect(uiState.gestures.frameRateMatching, uiState.gestures.refreshRateMode, uiState.videoFrameRate) {
        if (uiState.gestures.frameRateMatching && uiState.gestures.refreshRateMode != com.raulshma.jellyplay.core.model.RefreshRateMode.OFF && uiState.videoFrameRate != null) {
            val videoStream = uiState.media.mediaStreams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.VIDEO }
            windowOps.matchFrameRate(
                frameRate = uiState.videoFrameRate,
                targetWidth = videoStream?.width,
                targetHeight = videoStream?.height,
                mode = uiState.gestures.refreshRateMode,
            )
        }
    }

    LaunchedEffect(uiState.gestures.rememberBrightness) {
        // -1f (BRIGHTNESS_OVERRIDE_NONE) is the "user hasn't set a level" sentinel;
        // 0.5f is a legitimate brightness a user can pick, so it must not be used
        // as the guard. Re-applies the saved level on recreate/resume.
        if (uiState.gestures.rememberBrightness && uiState.gestures.brightnessLevel >= 0f) {
            windowOps.applyWindowBrightness(uiState.gestures.brightnessLevel)
        }
    }

    // The system resets window.attributes.screenBrightness to the OS default on
    // ON_PAUSE/ON_STOP (e.g. screen-off, app switch), and the LaunchedEffect above
    // only re-fires when the rememberBrightness *flag* changes — not on plain
    // foregrounding. Re-apply the saved level on every ON_RESUME so the user's
    // chosen brightness survives navigation away and back.
    val brightnessLevel = uiState.gestures.brightnessLevel
    val rememberBrightness = uiState.gestures.rememberBrightness
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(windowOps, rememberBrightness, brightnessLevel, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                rememberBrightness && brightnessLevel >= 0f
            ) {
                windowOps.applyWindowBrightness(brightnessLevel)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    val isCastConnected by viewModel.cast.isConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val isCastConnecting by viewModel.cast.isConnectingFlow.collectAsStateWithLifecycle(initialValue = false)
    val castIsPlaying by viewModel.cast.castIsPlaying.collectAsStateWithLifecycle(initialValue = false)
    val castDuration by viewModel.cast.castDurationMs.collectAsStateWithLifecycle(initialValue = 0L)
    val castVolume by viewModel.cast.castVolumeFlow.collectAsStateWithLifecycle(initialValue = 1f)

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

    val syncPlayIgnoreWait by viewModel.syncPlay.ignoreWait.collectAsStateWithLifecycle()

    LaunchedEffect(isCastConnected, uiState.uiPrefs.defaultOrientation) {
        if (isTv) {
            windowOps.lockOrientation(PlayerOrientationLock.TV_LANDSCAPE)
        } else if (isCastConnected) {
            windowOps.lockOrientation(PlayerOrientationLock.USER)
        } else {
            delay(400)
            windowOps.lockOrientation(
                when (uiState.uiPrefs.defaultOrientation) {
                    OrientationMode.SENSOR_LANDSCAPE -> PlayerOrientationLock.SENSOR_LANDSCAPE
                    OrientationMode.SENSOR_PORTRAIT -> PlayerOrientationLock.SENSOR_PORTRAIT
                    OrientationMode.SENSOR -> PlayerOrientationLock.SENSOR
                    OrientationMode.LOCKED_LANDSCAPE -> PlayerOrientationLock.LOCKED_LANDSCAPE
                    OrientationMode.LOCKED_PORTRAIT -> PlayerOrientationLock.LOCKED_PORTRAIT
                }
            )
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

    LaunchedEffect(aspectRatio, detectedAspectRatio, engine) {
        val effectiveRatio = if (aspectRatio == AspectRatio.AUTO) {
            detectedAspectRatio ?: AspectRatio.FIT
        } else {
            aspectRatio
        }
        // The engine maps the enum to its native mode (media3 resize mode / mpv
        // panscan / VLC aspectRatio) — no media3 constant crosses the seam here.
        engine?.setAspectRatio(effectiveRatio)
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
    val isSkipSegmentVisible = activeSegment != null &&
            activeSegmentBehavior == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON &&
            !isInPipMode &&
            !isCinemaIntroVisible &&
            !(activeSegment.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO && shouldShowUpNext)

    LaunchedEffect(showControls, isTv, isNextEpisodeVisible, isSkipSegmentVisible, isCinemaIntroVisible) {
        if (isTv && !showControls) {
            when {
                isCinemaIntroVisible -> tvCinemaIntroFocusRequester.tryRequestFocus("tv_cinema_intro")
                isNextEpisodeVisible -> tvNextEpisodeFocusRequester.tryRequestFocus("tv_next_episode")
                isSkipSegmentVisible -> tvSkipSegmentFocusRequester.tryRequestFocus("tv_skip_segment")
                else -> tvPlayerFocusRequester.tryRequestFocus("tv_player")
            }
        } else if (!isTv && hasHardwareKeyboard && !showControls) {
            keyboardFocusRequester.tryRequestFocus("keyboard_player")
        }
    }

    // Desktop (wave 14A): the hardware-keyboard layer must OWN focus whenever
    // it composes, not only once the controls have hidden — see
    // [grabsKeyboardFocusWithControlsVisible]. The at-HEAD effect above fires
    // on the showControls→false edge, but the controls START visible
    // (`showControls = true`) and auto-hide only after controlsTimeoutMs (or
    // never, while a sheet/seek/overflow suppresses it), so a desktop key
    // press in that window had no focused node to land on: Compose's
    // null-focus fallback dispatch stops at the topmost key-input node (the
    // desktop shell's scaffold onPreviewKeyEvent Row) — ESC popped, SPACE
    // never reached this screen's handler (wave 13B harness finding).
    // [layerComposed] tracks the keyboard layer's modifier branch above, so
    // the grab re-arms when it re-composes after a sheet closes. The Android
    // actual returns false, so the effect composes nothing on Android (phone
    // and TV unchanged).
    if (!isTv && hasHardwareKeyboard) {
        PlayerKeyboardFocusGrabEffect(
            focusRequester = keyboardFocusRequester,
            layerComposed = currentSheet == PlayerSheet.None,
        )
    }

    val doPlay: () -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        {
            playbackIntended = true
            if (isInSyncPlaySession) viewModel.syncPlay.togglePlayPause()
            else if (isCastConnected) viewModel.cast.castPlay()
            else viewModel.resumePlayback()
        }
    }
    val doPause: () -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        {
            playbackIntended = false
            if (isInSyncPlaySession) viewModel.syncPlay.togglePlayPause()
            else if (isCastConnected) viewModel.cast.castPause()
            else engine?.pause()
        }
    }
    val doSeekTo: (Long) -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        { ms ->
            if (isInSyncPlaySession) viewModel.syncPlay.seekTo(ms)
            else if (isCastConnected) viewModel.cast.castSeekTo(ms)
            else viewModel.seekTo(ms)
        }
    }
    val doSeekBack: () -> Unit = remember(engine, uiState.gestures.seekDurationMs, doSeekTo, isCastConnected) {
        {
            val pos = viewModel.playerEngineRef?.currentPositionMs ?: 0L
            val target = (pos - uiState.gestures.seekDurationMs).coerceAtLeast(0)
            doSeekTo(target)
        }
    }
    val doSeekForward: () -> Unit = remember(engine, uiState.gestures.seekDurationMs, doSeekTo, isCastConnected) {
        {
            val pos = viewModel.playerEngineRef?.currentPositionMs ?: 0L
            val dur = viewModel.playerEngineRef?.durationMs ?: 0L
            // For live streams dur is 0 until resolved, which previously pinned every
            // forward seek to 0. Skip the upper clamp when there is no known duration;
            // the engine clamps on its own at seek time. Mirrors the gesture path.
            val target = if (dur <= 0L) {
                (pos + uiState.gestures.seekDurationMs).coerceAtLeast(0L)
            } else {
                (pos + uiState.gestures.seekDurationMs).coerceAtMost(dur)
            }
            doSeekTo(target)
        }
    }
    val doTogglePlayPause: () -> Unit = remember(isPlaying, doPlay, doPause) {
        { if (isPlaying) doPause() else doPlay() }
    }
    val currentDoSeekBack by rememberUpdatedState(doSeekBack)
    val currentDoSeekForward by rememberUpdatedState(doSeekForward)
    val currentDoTogglePlayPause by rememberUpdatedState(doTogglePlayPause)
    val currentSeekDurationMs by rememberUpdatedState(uiState.gestures.seekDurationMs)
    val seekState = rememberDpadSeekState(
        getBaseStepMs = { currentSeekDurationMs },
        getCurrentPositionMs = { viewModel.playerEngineRef?.currentPositionMs ?: 0L },
        getDurationMs = { viewModel.playerEngineRef?.durationMs ?: 0L },
        onCommit = { doSeekTo(it) },
    )
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
        castVolume,
        doSeekTo,
    ) {
        GestureSeekController(
            scope = scope,
            getEngine = { engine },
            getSwipeSeekMaxMs = { uiState.gestures.swipeSeekMaxMs },
            isCastConnected = { isCastConnected },
            getCastVolume = { castVolume },
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
            saveBrightness = viewModel::saveBrightness,
            setCastVolume = viewModel.cast::setCastVolume,
        )
    }
    val brightnessOverlay by gestureController.brightnessOverlay.collectAsStateWithLifecycle()
    val volumeOverlay by gestureController.volumeOverlay.collectAsStateWithLifecycle()
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

    // Cast-route teardown for the companion dashboard's disconnect action
    // (platform seam: Android also stops the legacy cast transport).
    val disconnectCast = rememberCastDisconnect(viewModel)

    if (isCastConnected) {
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
            castPositionFlow = viewModel.cast.castPositionMs,
            durationMs = duration,
            volume = castVolume,
            isConnecting = isCastConnecting,
            audioTracks = trackState.audioTracks,
            subtitleTracks = trackState.subtitleTracks,
            episodes = uiState.episodes.seasonEpisodes,
            onPlayPause = doTogglePlayPause,
            onSeekBack = doSeekBack,
            onSeekForward = doSeekForward,
            onSeekTo = doSeekTo,
            onVolumeChange = { vol -> viewModel.cast.setCastVolume(vol) },
            onDisconnect = { viewModel.cast.onCastDisconnected(); disconnectCast() },
            onSelectAudioTrack = { viewModel.selectAudioTrack(it) },
            onSelectSubtitleTrack = { viewModel.selectSubtitleTrack(it) },
            onPlayEpisode = { epId -> viewModel.initialize(epId, null, 0L) },
            getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
            onToggleOrientation = toggleOrientation,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(
                    if (isTv && currentSheet == PlayerSheet.None) {
                        Modifier
                            .focusRequester(tvPlayerFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                userInteractionCount++
                                viewModel.onUserInteraction()
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.playerKeyCode == PlayerKeyCodes.KEYCODE_SPACE
                                ) {
                                    doTogglePlayPause()
                                    performConfirmHaptic()
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .onDpadKeyEvent(
                                onRight = { dpadKey ->
                                    if (!showControls) {
                                        if (dpadKey.isKeyDown) {
                                            seekState.seekForward(dpadKey.repeatCount)
                                        } else if (dpadKey.isKeyUp) {
                                            seekState.commitForward()
                                            performConfirmHaptic()
                                        }
                                        true
                                    } else false
                                },
                                onLeft = { dpadKey ->
                                    if (!showControls) {
                                        if (dpadKey.isKeyDown) {
                                            seekState.seekBackward(dpadKey.repeatCount)
                                        } else if (dpadKey.isKeyUp) {
                                            seekState.commitBackward()
                                            performConfirmHaptic()
                                        }
                                        true
                                    } else false
                                },
                                onSelect = {
                                    if (!showControls) {
                                        showControls = true
                                        true
                                    } else false
                                },
                                onUp = {
                                    if (!showControls) {
                                        showControls = true
                                        true
                                    } else false
                                },
                                onDown = {
                                    if (!showControls) {
                                        showControls = true
                                        true
                                    } else false
                                },
                                onBack = {
                                    if (showControls) {
                                        showControls = false
                                        true
                                    } else false
                                },
                                onPlayPause = {
                                    doTogglePlayPause()
                                    performConfirmHaptic()
                                    true
                                },
                                onFastForward = {
                                    doSeekForward()
                                    showControls = true
                                    performConfirmHaptic()
                                    true
                                },
                                onRewind = {
                                    doSeekBack()
                                    showControls = true
                                    performConfirmHaptic()
                                    true
                                },
                            )
                    } else if (!isTv && hasHardwareKeyboard && currentSheet == PlayerSheet.None) {
                        // Hardware-keyboard shortcuts for phones/tablets with a
                        // keyboard (Chromebook, Bluetooth, Samsung DeX). TV keeps
                        // the D-pad scheme above; this branch is non-TV only so the
                        // two never interfere. Keys match common media conventions:
                        // space=play/pause, arrows=seek/volume, F=fullscreen, M=mute,
                        // Esc=back, J/L=seek like YouTube.
                        Modifier
                            .focusRequester(keyboardFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                                val keyCode = keyEvent.playerKeyCode
                                userInteractionCount++
                                viewModel.onUserInteraction()
                                when (keyCode) {
                                    PlayerKeyCodes.KEYCODE_SPACE,
                                    PlayerKeyCodes.KEYCODE_MEDIA_PLAY,
                                    PlayerKeyCodes.KEYCODE_MEDIA_PAUSE,
                                    PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                        doTogglePlayPause()
                                        performConfirmHaptic()
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                                    PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
                                    PlayerKeyCodes.KEYCODE_L -> {
                                        doSeekForward()
                                        performConfirmHaptic()
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                                    PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
                                    PlayerKeyCodes.KEYCODE_J -> {
                                        doSeekBack()
                                        performConfirmHaptic()
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_DPAD_UP,
                                    PlayerKeyCodes.KEYCODE_VOLUME_UP -> {
                                        streamVolumeAdjuster(true)
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_DPAD_DOWN,
                                    PlayerKeyCodes.KEYCODE_VOLUME_DOWN -> {
                                        streamVolumeAdjuster(false)
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_F,
                                    PlayerKeyCodes.KEYCODE_F1, PlayerKeyCodes.KEYCODE_F2,
                                    PlayerKeyCodes.KEYCODE_F3, PlayerKeyCodes.KEYCODE_F4 -> {
                                        toggleOrientation()
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_M -> {
                                        viewModel.toggleMute()
                                        showControls = true
                                        true
                                    }
                                    PlayerKeyCodes.KEYCODE_ESCAPE,
                                    PlayerKeyCodes.KEYCODE_BACK -> {
                                        if (showControls) {
                                            showControls = false
                                            true
                                        } else {
                                            onBack()
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            }
                    } else Modifier
                )
                .pointerInput(uiState.gestures.gesturesEnabled, isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    if (!uiState.gestures.gesturesEnabled) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            viewModel.onUserInteraction()
                            if (uiState.gestures.isHoldSpeedActive) {
                                viewModel.stopHoldSpeed()
                            } else {
                                showControls = !showControls
                            }
                        },
                        onLongPress = {
                            viewModel.onUserInteraction()
                            if (uiState.gestures.holdSpeedEnabled) viewModel.startHoldSpeed()
                        },
                        onDoubleTap = { offset ->
                            viewModel.onUserInteraction()
                            val width = size.width
                            when {
                                offset.x < width * 0.35 -> {
                                    seekState.addOffset(-1, currentSeekDurationMs)
                                    currentDoSeekBack()
                                    performConfirmHaptic()
                                }
                                offset.x > width * 0.65 -> {
                                    seekState.addOffset(1, currentSeekDurationMs)
                                    currentDoSeekForward()
                                    performConfirmHaptic()
                                }
                                else -> {
                                    if (videoZoom > 1f) {
                                        videoZoom = 1f
                                    } else {
                                        currentDoTogglePlayPause()
                                        performConfirmHaptic()
                                    }
                                }
                            }
                        },
                    )
                }
                .pointerInput(uiState.gestures.gesturesEnabled, isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    if (!uiState.gestures.gesturesEnabled) return@pointerInput
                    awaitEachGesture {
                        var prevDistance = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.size >= 2) {
                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val distance = kotlin.math.sqrt(
                                    (p0.x - p1.x) * (p0.x - p1.x) + (p0.y - p1.y) * (p0.y - p1.y)
                                )
                                if (prevDistance > 0f && distance > 0f) {
                                    val zoom = distance / prevDistance
                                    if (zoom != 1f) {
                                        videoZoom = (videoZoom * zoom).coerceIn(1f, 3f)
                                        viewModel.onUserInteraction()
                                    }
                                }
                                prevDistance = distance
                                pointers.forEach { it.consume() }
                            } else {
                                prevDistance = 0f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
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

            // Platform surface seam (wave 9A): Android hosts the engine's
            // SurfaceView (or the empty fallback view for non-View-surface
            // engines — the V2a degrade); desktop hosts the SwingPanel/HWND
            // child window mpv embeds into. Zoom transform + PiP bounds
            // tracking stay with the platform actuals.
            //
            // Wave 14B: composed UNCONDITIONALLY — `engine` is null while the
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
                    viewModel.applySubtitleStyle()
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
                        viewModel.applySubtitleStyle()
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
                        viewModel.updatePipSourceRect(left, top, right, bottom)
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

            GestureOverlay(
                seekDirection = seekState.direction,
                seekOffsetMs = seekState.offsetMs,
                brightnessValue = brightnessOverlay,
                volumeValue = volumeOverlay,
                indicatorSide = uiState.gestures.gestureIndicatorSide,
                gesturesEnabled = uiState.gestures.gesturesEnabled && !isScreenLocked,
                swipeSeekMaxMs = uiState.gestures.swipeSeekMaxMs,
                onSeekGesture = remember(gestureController) { { totalDeltaMs -> gestureController.onSeekGesture(totalDeltaMs) } },
                onBrightnessGesture = remember(gestureController) { { delta -> gestureController.onBrightnessGesture(delta) } },
                onVolumeGesture = remember(gestureController) { { delta -> gestureController.onVolumeGesture(delta) } },
                onClearOverlays = remember(gestureController, seekState) {
                    {
                        gestureController.onClearOverlays()
                        seekState.reset()
                    }
                },
                showControls = showControls,
                onEdgeSwipe = remember(onBack) {
                    {
                        if (!showControls) {
                            showControls = true
                        } else {
                            onBack()
                        }
                    }
                },
                onHapticPulse = remember(windowOps, viewModel) {
                    {
                        if (viewModel.hapticsEnabled) {
                            windowOps.performConfirmHaptic()
                        }
                    }
                },
                onStartGesture = remember(gestureController) { { gestureController.onStartGesture() } },
                onCancelOverlays = remember(gestureController, seekState) {
                    {
                        gestureController.onCancelOverlays()
                        seekState.reset()
                    }
                },
            )

            AnimatedVisibility(
                visible = uiState.gestures.isHoldSpeedActive,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(150)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = HOLD_SPEED_PILL_BOTTOM_CLEARANCE_DP.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(playerScrimColor().copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${uiState.playbackSpeed}x",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }

            // Trickplay overlay for seek gestures
            AnimatedVisibility(
                visible = uiState.uiPrefs.trickplayOnSeekGesture && gestureTrickplayVisible,
                enter = fadeIn(tween(150, easing = AlphaEasing)),
                exit = fadeOut(tween(200, easing = AlphaEasing)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                TrickplayOverlay(
                    bitmap = gestureTrickplayBitmap,
                    positionMs = gestureSeekPositionMs,
                    deltaMs = gestureDeltaMs,
                    durationMs = duration,
                )
            }

            if (cinemaIntroState != null && !isInPipMode) {
                IntroSkipOverlay(
                    isVisible = true,
                    onSkip = {
                        viewModel.skipIntro()
                        performConfirmHaptic()
                    },
                    focusRequester = tvCinemaIntroFocusRequester,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 40.dp),
                )
            }

            if (activeSegment != null && activeSegmentBehavior == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON && !isInPipMode) {
                val hideForUpNext = activeSegment.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO && shouldShowUpNext
                if (!hideForUpNext) {
                    SegmentSkipOverlay(
                        isVisible = true,
                        segmentType = activeSegment.type,
                        onSkip = {
                            viewModel.skipSegment(activeSegment)
                            performConfirmHaptic()
                        },
                        focusRequester = tvSkipSegmentFocusRequester,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 100.dp, end = 40.dp),
                    )
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
                    onPlayNext = { viewModel.playNextEpisode() },
                    onCancel = { viewModel.cancelAutoplay() },
                    onToggleAutoplay = { viewModel.setVideoAutoplayNext(!uiState.autoplay.videoAutoplayNext) },
                    isPlaying = isPlaying,
                    pauseCountdown = currentSheet != PlayerSheet.None || isScreenLocked,
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

            if (isScreenLocked && !isInPipMode) {
                val usePin = uiState.uiPrefs.usePinForPlayerLock && uiState.uiPrefs.hasPin
                if (usePin) {
                    PinLockOverlay(
                        visible = true,
                        onDismiss = { },
                        onUnlock = {
                            viewModel.setScreenLocked(false)
                            showControls = true
                        },
                        verifyPin = { pin -> viewModel.verifyPlayerLockPin(pin) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SlideToUnlockOverlay(
                        visible = true,
                        onDismiss = { },
                        onUnlock = {
                            viewModel.setScreenLocked(false)
                            showControls = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Controller-owned slices — collected here, at their
            // consumption points, instead of flowing through the residual
            // uiState. Each is low-frequency; collecting per-leaf keeps the
            // root scope unaffected.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            val sleepTimer by viewModel.sleepTimer.state.collectAsStateWithLifecycle()
            val abRepeat by viewModel.abRepeat.state.collectAsStateWithLifecycle()
            val syncPlay by viewModel.syncPlay.state.collectAsStateWithLifecycle()

            if (uiState.uiPrefs.showVideoStats) {
                VideoStatsOverlay(
                    statsFlow = viewModel.videoStats,
                    currentPositionFlow = viewModel.currentPositionMs,
                    durationMs = duration,
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
                    decoderMode = effectsState.decoderMode.displayName,
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
            // so they need no keys. The few that capture a value (onSeekEnd,
            // onPassthroughClick) are keyed on exactly that value so they
            // recreate only when it actually changes.
            val onPlayPause by remember(doTogglePlayPause) { mutableStateOf({ doTogglePlayPause() }) }
            val onPreviousEpisode by remember { mutableStateOf({ viewModel.playPreviousEpisode() }) }
            val onNextEpisode by remember { mutableStateOf({ viewModel.playNextEpisode() }) }
            val onSeekEnd by remember(duration, doSeekTo) {
                mutableStateOf({
                    isSeeking = false
                    if (duration > 0) doSeekTo(seekPositionMs)
                })
            }
            val onSeekStart by remember { mutableStateOf({ isSeeking = true }) }
            val onSeekPositionChange by remember { mutableStateOf({ positionMs: Long -> seekPositionMs = positionMs }) }
            val onSpeedClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Speed }) }
            val onAudioClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Audio }) }
            // Primary subtitle button opens the hub on the Tracks tab.
            val onSubtitleClick by remember { mutableStateOf({
                viewModel.subtitles.loadRemoteSubtitles()
                viewModel.subtitles.loadSubtitleCultures()
                viewModel.subtitles.loadConfiguredProviders()
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            // Overflow "Subtitles" entry opens the hub on the Get tab (the
            // former "Get Subtitles" entry point's most useful landing spot).
            val onSubtitleHubClick by remember { mutableStateOf({
                // Reset search/cultures state from any previous item before
                // loading fresh data, so stale results don't leak across items.
                viewModel.subtitles.resetSubtitleManagerState()
                viewModel.subtitles.loadRemoteSubtitles()
                viewModel.subtitles.loadSubtitleCultures()
                viewModel.subtitles.loadConfiguredProviders()
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            val onChapterClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Chapter }) }
            val onInfoClick by remember { mutableStateOf({ currentSheet = PlayerSheet.PlaybackInfo }) }
            val onAspectRatioClick by remember { mutableStateOf({ currentSheet = PlayerSheet.AspectRatio }) }
            val onDialogueBoostClick by remember { mutableStateOf({ viewModel.toggleDialogueBoost() }) }
            val onDialogueBoostStrengthChange by remember { mutableStateOf({ strength: EffectStrength -> viewModel.setDialogueBoostStrength(strength) }) }
            val onNightModeClick by remember { mutableStateOf({ viewModel.effects.toggleNightMode() }) }
            val onNightModeStrengthChange by remember { mutableStateOf({ strength: EffectStrength -> viewModel.effects.setNightModeStrength(strength) }) }
            val onAVSyncClick by remember { mutableStateOf({ currentSheet = PlayerSheet.AVSync }) }
            val onDecoderClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Decoder }) }
            // effectsState is a `by collectAsStateWithLifecycle()` delegate, so
            // `effectsState.audioPassthrough` is read at invocation time — no key
            // needed and the lambda never goes stale.
            val onPassthroughClick by remember { mutableStateOf({ viewModel.effects.setAudioPassthrough(!effectsState.audioPassthrough) }) }
            val onEpisodesClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Episodes }) }
            val onSyncPlayClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SyncPlay }) }
            val onPipClick by remember(onEnterPip) { mutableStateOf({ onEnterPip() }) }
            val onMuteClick by remember { mutableStateOf({ viewModel.toggleMute() }) }
            val onVideoStatsClick by remember { mutableStateOf({ viewModel.toggleVideoStats() }) }
            val onQualityClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Quality }) }
            val onPlaybackModeClick by remember { mutableStateOf({ currentSheet = PlayerSheet.PlaybackMode }) }
            val onAudioNormalizationClick by remember { mutableStateOf({ viewModel.effects.toggleAudioNormalization() }) }
            val onAudioNormalizationModeChange by remember { mutableStateOf({ mode: AudioNormalizationMode -> viewModel.effects.setAudioNormalizationMode(mode) }) }
            val onChannelMixClick by remember { mutableStateOf({ viewModel.effects.toggleChannelMix() }) }
            val onChannelMixModeChange by remember { mutableStateOf({ mode: ChannelMixMode -> viewModel.effects.setChannelMixMode(mode) }) }
            val onSleepTimerClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SleepTimer }) }
            val onVideoFilterClick by remember { mutableStateOf({ currentSheet = PlayerSheet.VideoFilter }) }
            // Capture the current video frame from the engine's surface
            // (platform seam: PixelCopy on Android's SurfaceView surfaces —
            // only PixelCopy, not View.drawToBitmap, can read them). The
            // titleHint seeds the MediaStore filename. Result surfaces as a
            // snackbar with the saved path.
            val onScreenshotClick: () -> Unit = remember {
                {
                    val view = playerViewRef
                    if (view != null) {
                        scope.launch { snackbarHostState.showSnackbar("Capturing frame…", duration = SnackbarDuration.Short) }
                        requestVideoFrameCapture(
                            surfaceView = view,
                            titleHint = uiState.title,
                        ) { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Player not ready", duration = SnackbarDuration.Short)
                        }
                    }
                    Unit
                }
            }
            val onLockClick by remember { mutableStateOf({
                viewModel.setScreenLocked(true)
                showControls = false
            }) }
            val onControlsFocusChange by remember { mutableStateOf({ hasFocus: Boolean -> controlsHasFocus = hasFocus }) }
            val onOverflowMenuChange by remember { mutableStateOf({ open: Boolean -> isOverflowMenuOpen = open }) }

            // Transparent VLC-style subtitle-delay overlay. Sits over the video
            // (below the control chrome) so the user can watch subtitles shift.
            // Passes empty-space taps through to the host gesture layer.
            if (showDelayOverlay && !isInPipMode && !isScreenLocked) {
                SubtitleDelayOverlay(
                    currentDelayMs = uiState.subtitleStyle.offsetMs,
                    onChange = { viewModel.setSubtitleDelay(it) },
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
                isPlaying = isPlaying,
                currentPositionFlow = viewModel.currentPositionMs,
                duration = duration,
                bufferedPositionFlow = viewModel.bufferedPositionMs,
                videoStatsFlow = viewModel.videoStats,
                playbackSpeed = playbackSpeed,
                chapters = uiState.chapters,
                dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                dialogueBoostStrength = uiState.dialogueBoostStrength,
                nightModeEnabled = effectsState.nightModeEnabled,
                nightModeStrength = effectsState.nightModeStrength,
                audioPassthrough = effectsState.audioPassthrough,
                segments = uiState.segmentState.segments,
                resumePositionMs = resumePositionMs,
                playMethod = uiState.media.playMethod,
                isDirectPlayForced = uiState.media.isDirectPlayForced,
                hdrType = uiState.hdrType,
                mediaStreams = uiState.media.mediaStreams,
                audioTracks = trackState.audioTracks,
                isConnectionMetered = uiState.isConnectionMetered,
                subtitleDelayMs = uiState.subtitleStyle.offsetMs,
                onSubtitleDelayClick = { showDelayOverlay = true },
                showPlaybackMetadata = uiState.uiPrefs.showPlaybackMetadata,
                showClock = uiState.uiPrefs.showClock,
                showTimeRemaining = uiState.uiPrefs.showTimeRemaining,
                currentAspectRatio = aspectRatio,
                detectedAspectRatio = detectedAspectRatio,
                isVisible = showControls && !isInPipMode && !isScreenLocked,
                tvSkipSegmentFocusRequester = tvSkipSegmentFocusRequester,
                tvNextEpisodeFocusRequester = tvNextEpisodeFocusRequester,
                isSkipSegmentVisible = isSkipSegmentVisible,
                isNextEpisodeVisible = isNextEpisodeVisible,
                onControlRowScrolled = {
                    userInteractionCount++
                    viewModel.onUserInteraction()
                },
                supportsSubtitleStyle = uiState.engineCapabilities.supportsSubtitleStyle,
                supportsDialogueBoost = uiState.engineCapabilities.supportsDialogueBoost,
                supportsNightMode = uiState.engineCapabilities.supportsNightMode,
                supportsAudioDelay = uiState.engineCapabilities.supportsAudioDelay,
                supportsSubtitleDelay = uiState.engineCapabilities.supportsSubtitleDelay,
                supportsAudioPassthrough = uiState.engineCapabilities.supportsAudioPassthrough,
                hasEpisodes = hasEpisodes,
                episodeBrowserEnabled = episodeBrowserEnabled,
                onPlayPause = onPlayPause,
                hasPreviousEpisode = hasPreviousEpisode,
                hasNextEpisode = hasNextEpisode,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                onSeekStart = onSeekStart,
                onSeekEnd = onSeekEnd,
                onSeekPositionChange = onSeekPositionChange,
                tvTrickplayBitmap = if (isTv) tvTrickplayBitmap else null,
                onToggleOrientation = toggleOrientation,
                onBack = onBack,
                onSpeedClick = onSpeedClick,
                onAudioClick = onAudioClick,
                onSubtitleClick = onSubtitleClick,
                onSubtitleHubClick = onSubtitleHubClick,
                onChapterClick = onChapterClick,
                onInfoClick = onInfoClick,
                onAspectRatioClick = onAspectRatioClick,
                onDialogueBoostClick = onDialogueBoostClick,
                onDialogueBoostStrengthChange = onDialogueBoostStrengthChange,
                onNightModeClick = onNightModeClick,
                onNightModeStrengthChange = onNightModeStrengthChange,
                onAVSyncClick = onAVSyncClick,
                onDecoderClick = onDecoderClick,
                onPassthroughClick = onPassthroughClick,
                onEpisodesClick = onEpisodesClick,
                onSyncPlayClick = onSyncPlayClick,
                onPipClick = onPipClick,
                onMuteClick = onMuteClick,
                isMuted = uiState.isMuted,
                isInSyncPlaySession = isInSyncPlaySession,
                syncPlayGroupName = syncPlay.syncPlayGroupName,
                syncPlayParticipantCount = syncPlay.syncPlayParticipantCount,
                isSyncPlaySynced = syncPlay.isSyncPlaySynced,
                isSyncPlaySyncing = syncPlay.isSyncPlaySyncing,
                showVideoStats = uiState.uiPrefs.showVideoStats,
                onVideoStatsClick = onVideoStatsClick,
                streamingQuality = uiState.uiPrefs.streamingQuality,
                playbackMode = uiState.uiPrefs.playbackMode,
                onQualityClick = onQualityClick,
                onPlaybackModeClick = onPlaybackModeClick,
                audioNormalizationMode = effectsState.audioNormalizationMode,
                audioNormalizationEnabled = effectsState.audioNormalizationEnabled,
                channelMixMode = effectsState.channelMixMode,
                channelMixEnabled = effectsState.channelMixEnabled,
                supportsAudioNormalization = uiState.engineCapabilities.supportsAudioNormalization,
                supportsChannelMixing = uiState.engineCapabilities.supportsChannelMixing,
                supportsLiveQualitySwitch = uiState.engineCapabilities.supportsLiveQualitySwitch,
                onAudioNormalizationClick = onAudioNormalizationClick,
                onAudioNormalizationModeChange = onAudioNormalizationModeChange,
                onChannelMixClick = onChannelMixClick,
                onChannelMixModeChange = onChannelMixModeChange,
                sleepTimerActive = sleepTimer.sleepTimerActive,
                sleepTimerEndOfEpisode = sleepTimer.sleepTimerEndOfEpisode,
                sleepTimerRemainingFlow = viewModel.sleepTimer.remainingMs,
                onSleepTimerClick = onSleepTimerClick,
                supportsVideoFilters = uiState.engineCapabilities.supportsVideoFilters,
                videoFiltersActive = !uiState.videoFx.videoEffects.isNeutral,
                onVideoFilterClick = onVideoFilterClick,
                supportsScreenshot = uiState.engineCapabilities.supportsScreenshot,
                onScreenshotClick = onScreenshotClick,
                abRepeat = abRepeat,
                onAbRepeatToggle = { viewModel.abRepeat.setEnabled(!abRepeat.enabled) },
                onAbRepeatSetA = { viewModel.abRepeat.setPointA() },
                onAbRepeatSetB = { viewModel.abRepeat.setPointB() },
                onAbRepeatClear = { viewModel.abRepeat.clear() },
                audioOnly = uiState.audioOnly,
                onToggleAudioOnly = { viewModel.toggleAudioOnly() },
                onLockClick = onLockClick,
                onControlsFocusChange = onControlsFocusChange,
                onOverflowMenuChange = onOverflowMenuChange,
                castManager = viewModel.platformCastManager,
                modifier = Modifier.fillMaxSize(),
            )
            } // end PlayerDarkTheme (control bars)

            AnimatedVisibility(
                visible = !isTv && uiState.uiPrefs.trickplayEnabled && showControls && isSeeking,
                enter = fadeIn(tween(150, easing = AlphaEasing)),
                exit = fadeOut(tween(200, easing = AlphaEasing)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TRICKPLAY_THUMB_BOTTOM_CLEARANCE_DP.dp),
            ) {
                TrickplayOverlay(
                    bitmap = seekTrickplayBitmap,
                    positionMs = seekPositionMs,
                    durationMs = duration,
                )
            }

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

    LaunchedEffect(Unit) {
        // Combine the position with the gating state into one snapshotFlow and
        // suppress emits where neither the position nor the gating flags changed,
        // avoiding re-deriving seeking-state on no-op emissions.
        snapshotFlow {
            Triple(
                seekPositionMs,
                isSeeking && uiState.uiPrefs.trickplayEnabled && uiState.uiPrefs.trickplayInfo != null,
                uiState.uiPrefs.trickplayInfo,
            )
        }
            .conflate()
            .distinctUntilChanged()
            .collect { (pos, shouldFetch, _) ->
                if (shouldFetch) {
                    val bitmap = viewModel.loadTrickplayThumbnail(pos) as? PlatformBitmap
                    seekTrickplayBitmap = bitmap
                    if (isTv) {
                        tvTrickplayBitmap = bitmap
                    }
                }
            }
    }

    LaunchedEffect(isSeeking) {
        if (!isSeeking) {
            seekTrickplayBitmap = null
            tvTrickplayBitmap = null
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                gestureSeekPositionMs,
                isGestureSeeking && uiState.uiPrefs.trickplayOnSeekGesture,
                uiState.uiPrefs.trickplayInfo,
            )
        }
            .conflate()
            .distinctUntilChanged()
            .collect { (pos, shouldFetch, trickplayInfo) ->
                if (shouldFetch) {
                    gestureTrickplayVisible = true
                    gestureTrickplayBitmap = if (trickplayInfo != null) {
                        viewModel.loadTrickplayThumbnail(pos) as? PlatformBitmap
                    } else {
                        null
                    }
                }
            }
    }

    LaunchedEffect(isGestureSeeking) {
        if (!isGestureSeeking && gestureTrickplayVisible) {
            delay(1000)
            gestureTrickplayVisible = false
            gestureTrickplayBitmap = null
        }
    }

    LaunchedEffect(showControls, controlsHasFocus, isSeeking, currentSheet, isOverflowMenuOpen, userInteractionCount) {
        if (showControls && !isSeeking && currentSheet == PlayerSheet.None && !isOverflowMenuOpen) {
            if (!isTv && controlsHasFocus) {
                return@LaunchedEffect
            }
            val timeout = if (isTv) uiState.uiPrefs.controlsTimeoutMs * 2 else uiState.uiPrefs.controlsTimeoutMs
            delay(timeout)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            // Platform cast-session events (Connected → castToDevice,
            // Disconnected → onCastDisconnected) — inert on desktop.
            launchPlatformCastSessionEvents(viewModel)
            launch {
                viewModel.syncPlay.notifications.collect { message ->
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

    PlayerSheetRouter(
        currentSheet = currentSheet,
        onSheetChange = { sheet -> currentSheet = sheet },
        dismissSheet = dismissSheet,
        uiState = uiState,
        currentPositionFlow = viewModel.currentPositionMs,
        sleepTimerRemainingFlow = viewModel.sleepTimer.remainingMs,
        doSeekTo = doSeekTo,
        viewModel = viewModel,
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
    )

    val playerError = uiState.playerError
    if (uiState.showPlaybackErrorDialog && playerError != null) {
        PlaybackErrorDialog(
            errorMessage = playerError,
            currentPlayerType = uiState.preferredPlayerType,
            retryable = uiState.playerErrorRetryable,
            onRetry = { viewModel.retryPlayback() },
            onRetryWithEngine = { viewModel.retryWithEngine(it) },
            onDismiss = { viewModel.dismissPlaybackError() },
            transcodeReasons = rememberFormattedTranscodeReasons(uiState.media.transcodeReasons),
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheetRouter(
    currentSheet: PlayerSheet,
    onSheetChange: (PlayerSheet) -> Unit,
    dismissSheet: () -> Unit,
    uiState: VideoPlayerUiState,
    currentPositionFlow: StateFlow<Long>,
    sleepTimerRemainingFlow: StateFlow<Long>,
    doSeekTo: (Long) -> Unit,
    viewModel: VideoPlayerViewModel,
    itemId: String,
    syncPlayIgnoreWait: Boolean,
    onLoadLocalSubtitle: () -> Unit,
    onPickFont: () -> Unit,
    onOpenSubtitleTester: () -> Unit,
    onOpenSubtitleDelayOverlay: () -> Unit,
) {
    when (val sheet = currentSheet) {
        is PlayerSheet.Speed -> {
            SpeedPickerSheet(
                currentSpeed = uiState.playbackSpeed,
                onSelect = { viewModel.setPlaybackSpeed(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Audio -> {
            // Track slice: collected inside the branch — only this
            // picker consumes it while the sheet is open.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            TrackPickerSheet(
                title = stringResource(Res.string.player_audio),
                tracks = trackState.audioTracks,
                onSelect = { viewModel.selectAudioTrack(it) },
                onReset = if (trackState.hasAudioOverride) { { viewModel.resetAudioTrack() } } else null,
                onDismiss = dismissSheet,
                footer = if (uiState.media.seriesId != null) {
                    {
                        // Per-series audio-language preference toggle. Saving
                        // remembers the currently-selected track's language for
                        // every episode of this series; toggling off forgets it.
                        RememberPreferenceToggle(
                            label = stringResource(Res.string.player_video_remember_audio_language),
                            checked = trackState.hasSeriesAudioPref,
                            onToggle = { remember ->
                                val lang = if (remember) {
                                    trackState.audioTracks.firstOrNull { it.isSelected && it.index >= 0 }?.language
                                } else {
                                    null
                                }
                                viewModel.setSeriesAudioLanguagePreference(lang)
                            },
                        )
                    }
                } else null,
            )
        }
        is PlayerSheet.SubtitleHub -> {
            LaunchedEffect(Unit) {
                viewModel.subtitles.loadRemoteSubtitles()
                viewModel.subtitles.loadSubtitleCultures()
                viewModel.subtitles.loadConfiguredProviders()
            }
            // Track + subtitle-workflow slices: collected inside the
            // branch — only this hub consumes them while the sheet is open.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            val subtitleState by viewModel.subtitles.state.collectAsStateWithLifecycle()
            SubtitleHubSheet(
                initialTab = com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTab.TRACKS,
                onDismiss = dismissSheet,
                // Tracks tab
                subtitleTracks = trackState.subtitleTracks,
                onSelectSubtitleTrack = { viewModel.selectSubtitleTrack(it) },
                onResetSubtitleTrack = if (trackState.hasSubtitleOverride) {
                    { viewModel.resetSubtitleTrack() }
                } else null,
                tracksFooter = if (uiState.media.seriesId != null) {
                    {
                        // Per-series subtitle preference toggle. With a real track
                        // selected it saves that track's language + role so every
                        // episode restores the right same-language track; with the
                        // "Off" row selected it saves a "subtitles off" intent so
                        // every episode loads with subs off. Toggling off forgets
                        // whichever intent was saved.
                        val selectedOff = trackState.subtitleTracks
                            .firstOrNull { it.isSelected && it.index < 0 } != null
                        val label = if (selectedOff || trackState.hasSeriesSubtitleOffPref) {
                            stringResource(Res.string.player_video_remember_subtitles_off)
                        } else {
                            stringResource(Res.string.player_video_remember_subtitle_language)
                        }
                        RememberPreferenceToggle(
                            label = label,
                            checked = trackState.hasSeriesSubtitlePref,
                            onToggle = { remember ->
                                val sel = trackState.subtitleTracks.firstOrNull { it.isSelected }
                                if (sel != null && sel.index < 0) {
                                    viewModel.setSeriesSubtitleDisabled(remember)
                                } else if (remember) {
                                    viewModel.setSeriesSubtitlePreference(
                                        language = sel?.language,
                                        forced = sel?.badges?.contains(TrackBadge.FORCED)?.takeIf { it },
                                        hearingImpaired = sel?.badges?.contains(TrackBadge.SDH)?.takeIf { it },
                                    )
                                } else {
                                    viewModel.setSeriesSubtitlePreference(language = null)
                                }
                            },
                        )
                    }
                } else null,
                // Style tab
                subtitleStyle = uiState.subtitleStyle,
                onStyleChange = { viewModel.setSubtitleStyle(it) },
                onSubtitleDelayChange = viewModel::setSubtitleDelay,
                onPickFont = onPickFont,
                onOpenTester = onOpenSubtitleTester,
                capabilities = uiState.engineCapabilities,
                // Get tab
                downloadSubtitles = subtitleState.remoteSubtitles,
                isDownloading = subtitleState.isLoadingRemoteSubtitles,
                onDownload = { viewModel.subtitles.downloadSubtitle(it) },
                onLoadLocalFile = onLoadLocalSubtitle,
                searchResults = subtitleState.searchedSubtitles,
                isSearching = subtitleState.isSearchingSubtitles,
                hasSearched = subtitleState.hasSearchedSubtitles,
                searchError = subtitleState.subtitleSearchError,
                cultures = subtitleState.subtitleCultures,
                defaultLanguage = subtitleState.defaultSearchLanguage,
                onSearch = { viewModel.subtitles.searchRemoteSubtitles(it) },
                onDownloadSearched = { viewModel.subtitles.downloadSubtitle(it) },
                providerSearchResults = subtitleState.providerSearchResults,
                providerSearchErrors = subtitleState.providerSearchErrors,
                configuredProviders = subtitleState.configuredSubtitleProviders,
                onSearchAllProviders = { viewModel.subtitles.searchAllProviders(it) },
                onDownloadProviderSubtitle = { viewModel.subtitles.downloadProviderSubtitle(it) },
                downloadingSubtitles = subtitleState.downloadingSubtitles,
                // "Use" affordance: the hub switches to its Tracks tab itself;
                // this callback is a no-op placeholder for the host.
                onUseSubtitle = {},
                isUploading = subtitleState.isUploadingSubtitle,
                onUpload = { uriStr, fileName, language, isForced, isHearingImpaired ->
                    // KMP seam (wave 7C): the sheets hand the picked SAF
                    // document as its string form; SubtitleManager consumes it.
                    viewModel.subtitles.uploadSubtitle(
                        uriStr,
                        fileName,
                        language,
                        isForced,
                        isHearingImpaired,
                    )
                    onSheetChange(PlayerSheet.None)
                },
                // Delay tab
                currentSubtitleDelayMs = uiState.subtitleStyle.offsetMs,
                onOpenDelayOverlay = onOpenSubtitleDelayOverlay,
            )
        }
        is PlayerSheet.Chapter -> {
            // Collect position only while the chapter sheet is open, so
            // the router itself stays a low-frequency scope when no sheet (or
            // a non-chapter sheet) is shown.
            ChapterPickerBinder(
                chapters = uiState.chapters,
                currentPositionFlow = currentPositionFlow,
                onSelect = { positionTicks ->
                    doSeekTo(positionTicks / 10_000)
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackInfo -> {
            // Track + effects slices: collected inside the branch.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            PlayerModalBottomSheet(
                onDismissRequest = dismissSheet,
                sheetState = rememberModalBottomSheetState(),
            ) {
                PlaybackInfoOverlay(
                    mediaSource = uiState.media.currentMediaSource,
                    mediaStreams = uiState.media.mediaStreams,
                    playMethod = uiState.media.playMethod,
                    isConnectionMetered = uiState.isConnectionMetered,
                    hdrType = uiState.hdrType,
                    playerType = uiState.preferredPlayerType.name,
                    decoderMode = effectsState.decoderMode.name,
                    aspectRatio = uiState.videoFx.aspectRatio.name,
                    nightModeEnabled = effectsState.nightModeEnabled,
                    nightModeStrength = effectsState.nightModeStrength,
                    dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                    dialogueBoostStrength = uiState.dialogueBoostStrength,
                    audioPassthrough = effectsState.audioPassthrough,
                    audioTracks = trackState.audioTracks,
                    subtitleTracks = trackState.subtitleTracks,
                    playbackSpeed = uiState.playbackSpeed,
                    audioDelayMs = effectsState.audioDelayMs,
                    subtitleDelayMs = uiState.subtitleStyle.offsetMs,
                    playerError = uiState.playerError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                )
            }
        }
        is PlayerSheet.AspectRatio -> {
            AspectRatioSheet(
                currentRatio = uiState.videoFx.aspectRatio,
                detectedRatio = uiState.videoFx.detectedAspectRatio,
                onSelect = { viewModel.setAspectRatio(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.AVSync -> {
            // Effects slice: collected inside the branch.
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            AVSyncSheet(
                currentAudioDelayMs = effectsState.audioDelayMs,
                onAudioDelayChange = { viewModel.effects.setAudioDelay(it) },
                onDismiss = dismissSheet,
                audioDelaySupported = uiState.engineCapabilities.supportsAudioDelay,
            )
        }
        is PlayerSheet.Decoder -> {
            // Effects slice: collected inside the branch.
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            DecoderPickerSheet(
                currentMode = effectsState.decoderMode,
                onSelect = { viewModel.effects.setDecoderMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Episodes -> {
            EpisodePickerSheet(
                seasons = uiState.episodes.seriesSeasons,
                episodes = uiState.episodes.seasonEpisodes,
                currentSeasonId = uiState.episodes.currentSeasonId,
                currentEpisodeId = itemId,
                isLoading = uiState.episodes.isLoadingEpisodes,
                onSeasonSelect = { viewModel.loadSeasonEpisodes(it) },
                onEpisodeSelect = { episode ->
                    viewModel.playEpisode(episode.id, episode.playbackPositionTicks ?: 0L)
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
                getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
            )
        }
        is PlayerSheet.SyncPlay -> {
            // SyncPlay group-display slice: collected inside the branch.
            val syncPlayState by viewModel.syncPlay.state.collectAsStateWithLifecycle()
            SyncPlayPlayerSheet(
                groupName = syncPlayState.syncPlayGroupName ?: "Group",
                participantCount = syncPlayState.syncPlayParticipantCount,
                isSynced = syncPlayState.isSyncPlaySynced,
                isPlaying = uiState.isPlaying,
                ignoreWait = syncPlayIgnoreWait,
                repeatMode = syncPlayState.syncPlayRepeatMode,
                shuffleMode = syncPlayState.syncPlayShuffleMode,
                onRepeatModeChange = { viewModel.setSyncPlayRepeatMode(it) },
                onShuffleModeChange = { viewModel.setSyncPlayShuffleMode(it) },
                onTogglePlayPause = { viewModel.syncPlay.togglePlayPause() },
                onStop = { viewModel.syncPlay.sendStop() },
                onLeave = {
                    viewModel.syncPlay.leaveGroup()
                    onSheetChange(PlayerSheet.None)
                },
                onIgnoreWaitChange = { viewModel.syncPlay.setIgnoreWait(it) },
                 onDismiss = dismissSheet,
             )
         }
        is PlayerSheet.Quality -> {
            QualityPickerSheet(
                currentQuality = uiState.uiPrefs.streamingQuality,
                adaptiveBitrateEnabled = uiState.uiPrefs.adaptiveBitrateEnabled,
                onToggleAdaptiveBitrate = { viewModel.setAdaptiveBitrateEnabled(it) },
                onSelect = { viewModel.setStreamingQuality(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackMode -> {
            PlaybackModeSheet(
                currentMode = uiState.uiPrefs.playbackMode,
                onSelect = { viewModel.setPlaybackMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SleepTimer -> {
            // Sleep-timer slice: collected inside the branch.
            val sleepTimerState by viewModel.sleepTimer.state.collectAsStateWithLifecycle()
            SleepTimerSheetBinder(
                isActive = sleepTimerState.sleepTimerActive,
                isEndOfEpisodeMode = sleepTimerState.sleepTimerEndOfEpisode,
                lastUsedDurationMs = sleepTimerState.sleepTimerLastUsedDurationMs,
                sleepTimerRemainingFlow = sleepTimerRemainingFlow,
                onSelectDuration = { viewModel.sleepTimer.startSleepTimer(it) },
                onSelectEndOfEpisode = { viewModel.sleepTimer.startSleepTimerEndOfEpisode() },
                onCancel = { viewModel.sleepTimer.cancelSleepTimer() },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.VideoFilter -> {
            VideoFilterSheet(
                currentEffects = uiState.videoFx.videoEffects,
                onEffectsChange = { viewModel.setVideoEffects(it) },
                onDismiss = dismissSheet,
            )
        }
        PlayerSheet.None -> { }
    }
}

/**
 * Narrow binder that subscribes to [currentPositionFlow] only while the
  * chapter picker sheet is open, so the screen root and the sheet router
 * are not invalidated at 4 Hz on every position tick.
 */
@Composable
private fun ChapterPickerBinder(
    chapters: List<com.raulshma.jellyplay.core.model.ChapterInfo>,
    currentPositionFlow: StateFlow<Long>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentPositionMs by currentPositionFlow.collectAsStateWithLifecycle()
    ChapterPickerSheet(
        chapters = chapters,
        currentPositionMs = currentPositionMs,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

/**
 * Narrow binder that subscribes to [sleepTimerRemainingFlow] only while the
 * sleep-timer sheet is open, so the sheet router and screen root are not
 * invalidated on every 5 s tick (or the 100 ms fade-out burst).
 */
@Composable
private fun SleepTimerSheetBinder(
    isActive: Boolean,
    isEndOfEpisodeMode: Boolean,
    lastUsedDurationMs: Long,
    sleepTimerRemainingFlow: StateFlow<Long>,
    onSelectDuration: (Long) -> Unit,
    onSelectEndOfEpisode: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remainingMs by sleepTimerRemainingFlow.collectAsStateWithLifecycle()
    SleepTimerSheet(
        isActive = isActive,
        isEndOfEpisodeMode = isEndOfEpisodeMode,
        remainingMs = remainingMs,
        lastUsedDurationMs = lastUsedDurationMs,
        onSelectDuration = onSelectDuration,
        onSelectEndOfEpisode = onSelectEndOfEpisode,
        onCancel = onCancel,
        onDismiss = onDismiss,
    )
}
