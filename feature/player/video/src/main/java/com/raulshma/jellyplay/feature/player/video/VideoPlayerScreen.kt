package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.data.playback.FrameRateMatcher
import com.raulshma.jellyplay.core.data.cast.CastSessionEvent
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
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.state.GestureSeekController
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.AVSyncSheet
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.EpisodePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import com.raulshma.jellyplay.feature.player.video.engine.ZoomSafeSubtitleStrategy
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.MpvSubtitleOverlay
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
import com.raulshma.jellyplay.core.ui.player.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PlayerDarkTheme
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.media3.ui.AspectRatioFrameLayout

// ── Player overlay/animation timing (ms) ─────────────────────────────────
// Named so the tuning is discoverable instead of scattered as bare literals.
/** How long the gesture-seek ripple/indicator lingers after the last seek input. */
private const val GESTURE_SEEK_LINGER_MS = 800L
/** How long the AutoAspectRatio / Zoom badge is shown before auto-dismissing. */
private const val ASPECT_BADGE_DURATION_MS = 5_000L
/** How long the zoom badge is shown before auto-dismissing. */
private const val ZOOM_BADGE_DURATION_MS = 2_000L

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
 * Nudge the system media (STREAM_MUSIC) volume one step up or down for the
 * hardware-keyboard shortcuts (arrows / volume keys on non-TV). Mirrors the
 * gesture volume path, which adjusts the stream volume rather than the engine
 * volume so the system volume UI and ringer behaviour stay consistent.
 */
private fun adjustStreamMusicVolume(context: Context, up: Boolean) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
    val direction = if (up) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
    am.adjustStreamVolume(
        android.media.AudioManager.STREAM_MUSIC,
        direction,
        android.media.AudioManager.FLAG_SHOW_UI,
    )
}

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
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    // Dedicated host for the resume chip. Kept separate from [snackbarHostState]
    // so the chip can anchor under the top bar (TopCenter) while the shared
    // bottom host still serves screenshot / syncplay / pass-out toasts.
    val resumeChipHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Resume-reminder chip: when playback resumes from a saved position, offer a
    // one-tap "Restart" so the user isn't forced to scrub back.
    val resumedMessage = stringResource(R.string.player_resumed_message)
    val restartLabel = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_restart)
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
    var playerViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var lastAppliedSubtitleStyle by remember { mutableStateOf<SubtitleStyle?>(null) }
    var videoZoom by rememberSaveable { mutableFloatStateOf(1f) }

    val isTv = LocalTvMode.current
    // Hardware-keyboard detection (Chromebooks, Bluetooth keyboards, Samsung
    // DeX). Drives the non-TV keyboard-shortcut handler so phones/tablets with
    // a keyboard get space/arrows/F/M/Esc controls while touch-only devices
    // attach no extra key handler. TV keeps its dedicated D-pad scheme below.
    val hasHardwareKeyboard = remember(context) {
        context.resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
            context.resources.configuration.hardKeyboardHidden != android.content.res.Configuration.HARDKEYBOARDHIDDEN_YES
    }

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

    val localSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
            viewModel.addLocalSubtitle(uri, fileName)
            currentSheet = PlayerSheet.None
        }
    }

    val fontInvalidFormatMessage = stringResource(R.string.player_video_font_invalid_format)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val isFont = name.endsWith(".ttf") || name.endsWith(".otf")
            if (isFont) {
                viewModel.installUserFont(uri)
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

    var seekTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var gestureTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var gestureTrickplayVisible by remember { mutableStateOf(false) }
    var tvTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(itemId) {
        if (viewModel.isBackgroundCasting) {
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
            activity?.let {
                val window = it.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isWindowFocused = rememberUpdatedState(windowInfo.isWindowFocused)
    LaunchedEffect(activity) {
        snapshotFlow { isWindowFocused.value }.distinctUntilChanged().collect { focused ->
            if (focused) {
                activity?.let { act ->
                    val window = act.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
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
        activity?.let {
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val currentlyInPip = viewModel.pipController.isInPipMode.value
            val isBgCasting = viewModel.isCastConnected && viewModel.castIsPlaying.value &&
                viewModel.backgroundCastingEnabled
            val restoreOrientation = if (isTv)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (isBgCasting && !currentlyInPip) {
                activity?.let {
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = restoreOrientation
                        it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        // Restore OS-default brightness when leaving the player; otherwise a
                        // gesture-set level persists on the host window after the screen exits.
                        val layout = it.window.attributes
                        layout.screenBrightness =
                            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        it.window.attributes = layout
                        val window = it.window
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                activity?.let { act ->
                    if (!act.isDestroyed && !act.isFinishing) {
                        FrameRateMatcher.restoreOriginalMode(act)
                    }
                }
                playerViewRef = null
                viewModel.detachForBackgroundCast()
            } else if (!currentlyInPip) {
                activity?.let {
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = restoreOrientation
                        it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        // Restore OS-default brightness when leaving the player; otherwise a
                        // gesture-set level persists on the host window after the screen exits.
                        val layout = it.window.attributes
                        layout.screenBrightness =
                            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        it.window.attributes = layout
                        val window = it.window
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                activity?.let { act ->
                    if (!act.isDestroyed && !act.isFinishing) {
                        FrameRateMatcher.restoreOriginalMode(act)
                    }
                }
                playerViewRef = null
                viewModel.release()
            }
        }
    }

    LaunchedEffect(uiState.isPlaying, uiState.keepScreenOnDuringVideo) {
        activity?.let {
            if (!it.isDestroyed && !it.isFinishing) {
                if (uiState.isPlaying && uiState.keepScreenOnDuringVideo) {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }



    LaunchedEffect(uiState.frameRateMatching, uiState.refreshRateMode, uiState.videoFrameRate) {
        if (uiState.frameRateMatching && uiState.refreshRateMode != com.raulshma.jellyplay.core.model.RefreshRateMode.OFF && uiState.videoFrameRate != null) {
            val videoStream = uiState.mediaStreams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.VIDEO }
            activity?.let {
                if (!it.isDestroyed && !it.isFinishing) {
                    FrameRateMatcher.matchFrameRate(
                        activity = it,
                        frameRate = uiState.videoFrameRate,
                        targetWidth = videoStream?.width,
                        targetHeight = videoStream?.height,
                        mode = uiState.refreshRateMode,
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.rememberBrightness) {
        // -1f (BRIGHTNESS_OVERRIDE_NONE) is the "user hasn't set a level" sentinel;
        // 0.5f is a legitimate brightness a user can pick, so it must not be used
        // as the guard. Re-applies the saved level on recreate/resume.
        if (uiState.rememberBrightness && uiState.brightnessLevel >= 0f) {
            activity?.let { act ->
                if (!act.isDestroyed && !act.isFinishing) {
                    val layout = act.window.attributes
                    layout.screenBrightness = uiState.brightnessLevel
                    act.window.attributes = layout
                }
            }
        }
    }

    // The system resets window.attributes.screenBrightness to the OS default on
    // ON_PAUSE/ON_STOP (e.g. screen-off, app switch), and the LaunchedEffect above
    // only re-fires when the rememberBrightness *flag* changes — not on plain
    // foregrounding. Re-apply the saved level on every ON_RESUME so the user's
    // chosen brightness survives navigation away and back.
    val brightnessLevel = uiState.brightnessLevel
    val rememberBrightness = uiState.rememberBrightness
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(activity, rememberBrightness, brightnessLevel, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                rememberBrightness && brightnessLevel >= 0f
            ) {
                activity?.let { act ->
                    if (!act.isDestroyed && !act.isFinishing) {
                        val layout = act.window.attributes
                        layout.screenBrightness = brightnessLevel
                        act.window.attributes = layout
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
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
    val isCastConnected by viewModel.isConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val isCastConnecting by viewModel.isConnectingFlow.collectAsStateWithLifecycle(initialValue = false)
    val castIsPlaying by viewModel.castIsPlaying.collectAsStateWithLifecycle(initialValue = false)
    val castDuration by viewModel.castDurationMs.collectAsStateWithLifecycle(initialValue = 0L)
    val castVolume by viewModel.castVolumeFlow.collectAsStateWithLifecycle(initialValue = 1f)

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
    val currentMediaSource = uiState.currentMediaSource
    val mediaStreams = uiState.mediaStreams
    val aspectRatio = uiState.aspectRatio
    val detectedAspectRatio = uiState.detectedAspectRatio

    val toggleOrientation: () -> Unit = remember(activity, uiState.defaultOrientation) {
        {
            activity?.let { act ->
                val current = act.requestedOrientation
                val isPortrait = current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                    current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                // Resolve the configured default landscape mode so the toggle is symmetric:
                // portrait ↔ default-landscape, always returning to the user's preferred
                // landscape rather than drifting between LANDSCAPE and SENSOR_LANDSCAPE.
                val defaultLandscape = when (uiState.defaultOrientation) {
                    OrientationMode.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                act.requestedOrientation = if (isPortrait) defaultLandscape
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    val syncPlayIgnoreWait by viewModel.syncPlayIgnoreWait.collectAsStateWithLifecycle()

    LaunchedEffect(isCastConnected, uiState.defaultOrientation) {
        activity?.let {
            if (!it.isDestroyed && !it.isFinishing) {
                if (isTv) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (isCastConnected) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                } else {
                    delay(400)
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = when (uiState.defaultOrientation) {
                            OrientationMode.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            OrientationMode.SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            OrientationMode.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            OrientationMode.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }
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

    LaunchedEffect(aspectRatio, detectedAspectRatio, engine) {
        val effectiveRatio = if (aspectRatio == AspectRatio.AUTO) {
            detectedAspectRatio ?: AspectRatio.FIT
        } else {
            aspectRatio
        }

        val resizeMode = when (effectiveRatio) {
            AspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatio.RATIO_16_9, AspectRatio.RATIO_4_3, AspectRatio.RATIO_21_9 ->
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectRatio.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val targetRatio = effectiveRatio.ratio
        engine?.setAspectRatio(resizeMode, targetRatio)
    }

    val playMethod = uiState.playMethod
    val subtitleStyle = uiState.subtitleStyle
    val nextEpisode = uiState.nextEpisode
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

    val doPlay: () -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        {
            playbackIntended = true
            if (isInSyncPlaySession) viewModel.syncPlayTogglePlayPause()
            else if (isCastConnected) viewModel.castPlay()
            else viewModel.resumePlayback()
        }
    }
    val doPause: () -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        {
            playbackIntended = false
            if (isInSyncPlaySession) viewModel.syncPlayTogglePlayPause()
            else if (isCastConnected) viewModel.castPause()
            else engine?.pause()
        }
    }
    val doSeekTo: (Long) -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        { ms ->
            if (isInSyncPlaySession) viewModel.syncPlaySeekTo(ms)
            else if (isCastConnected) viewModel.castSeekTo(ms)
            else viewModel.seekTo(ms)
        }
    }
    val doSeekBack: () -> Unit = remember(engine, uiState.seekDurationMs, doSeekTo, isCastConnected) {
        {
            val pos = viewModel.playerEngineRef?.currentPositionMs ?: 0L
            val target = (pos - uiState.seekDurationMs).coerceAtLeast(0)
            doSeekTo(target)
        }
    }
    val doSeekForward: () -> Unit = remember(engine, uiState.seekDurationMs, doSeekTo, isCastConnected) {
        {
            val pos = viewModel.playerEngineRef?.currentPositionMs ?: 0L
            val dur = viewModel.playerEngineRef?.durationMs ?: 0L
            // For live streams dur is 0 until resolved, which previously pinned every
            // forward seek to 0. Skip the upper clamp when there is no known duration;
            // the engine clamps on its own at seek time. Mirrors the gesture path.
            val target = if (dur <= 0L) {
                (pos + uiState.seekDurationMs).coerceAtLeast(0L)
            } else {
                (pos + uiState.seekDurationMs).coerceAtMost(dur)
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
    val currentSeekDurationMs by rememberUpdatedState(uiState.seekDurationMs)
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
        uiState.swipeSeekMaxMs,
        isCastConnected,
        castVolume,
        doSeekTo,
    ) {
        GestureSeekController(
            scope = scope,
            getEngine = { engine },
            getSwipeSeekMaxMs = { uiState.swipeSeekMaxMs },
            isCastConnected = { isCastConnected },
            getCastVolume = { castVolume },
            readWindowBrightness = { activity?.window?.attributes?.screenBrightness ?: -1f },
            writeWindowBrightness = { newBrightness ->
                activity?.let { act ->
                    val layout = act.window.attributes
                    layout.screenBrightness = newBrightness
                    act.window.attributes = layout
                }
            },
            restoreWindowBrightness = { restored ->
                activity?.let { act ->
                    if (!act.isDestroyed && !act.isFinishing) {
                        val layout = act.window.attributes
                        layout.screenBrightness =
                            if (restored >= 0f) restored
                            else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        act.window.attributes = layout
                    }
                }
            },
            readStreamVolume = {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (am != null) {
                    am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) to
                        am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                } else 0 to 0
            },
            writeStreamVolume = { newVol ->
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                am?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
            },
            doSeekTo = doSeekTo,
            saveBrightness = viewModel::saveBrightness,
            setCastVolume = viewModel::setCastVolume,
        )
    }
    val brightnessOverlay by gestureController.brightnessOverlay.collectAsStateWithLifecycle()
    val volumeOverlay by gestureController.volumeOverlay.collectAsStateWithLifecycle()
    val gestureSeekPositionMs by gestureController.seekPositionMs.collectAsStateWithLifecycle()
    val gestureDeltaMs by gestureController.deltaMs.collectAsStateWithLifecycle()
    val isGestureSeeking by gestureController.isSeeking.collectAsStateWithLifecycle()
    val dismissSheet: () -> Unit = remember { { currentSheet = PlayerSheet.None } }

    // Shared confirmation haptic for discrete player actions (seek commit,
    // play/pause toggle, segment skip). Reuses the same View performHapticFeedback
    // path and hapticsEnabled gate as the gesture-bound haptic below, so a single
    // preference governs all player haptics.
    val performConfirmHaptic: () -> Unit = remember(activity, viewModel) {
        {
            if (viewModel.hapticsEnabled) {
                activity?.let { act ->
                    val view = act.window.decorView
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                        @Suppress("DEPRECATION")
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        }
    }

    if (isCastConnected) {
        CompanionDashboard(
            title = title,
            subtitle = subtitle,
            overview = uiState.overview,
            people = uiState.people,
            lyricsLines = uiState.lyricsLines,
            artworkUrl = uiState.artworkUrl,
            isPlaying = isPlaying,
            castPositionFlow = viewModel.castPositionMs,
            durationMs = duration,
            volume = castVolume,
            isConnecting = isCastConnecting,
            audioTracks = uiState.audioTracks,
            subtitleTracks = uiState.subtitleTracks,
            episodes = uiState.seasonEpisodes,
            onPlayPause = doTogglePlayPause,
            onSeekBack = doSeekBack,
            onSeekForward = doSeekForward,
            onSeekTo = doSeekTo,
            onVolumeChange = { vol -> viewModel.setCastVolume(vol) },
            onDisconnect = { viewModel.onCastDisconnected(); viewModel.castManagerField.disconnect(context) },
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
                                    keyEvent.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_SPACE
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
                                val keyCode = keyEvent.nativeKeyEvent.keyCode
                                userInteractionCount++
                                viewModel.onUserInteraction()
                                when (keyCode) {
                                    NativeKeyEvent.KEYCODE_SPACE,
                                    NativeKeyEvent.KEYCODE_MEDIA_PLAY,
                                    NativeKeyEvent.KEYCODE_MEDIA_PAUSE,
                                    NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                        doTogglePlayPause()
                                        performConfirmHaptic()
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_DPAD_RIGHT,
                                    NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                    NativeKeyEvent.KEYCODE_L -> {
                                        doSeekForward()
                                        performConfirmHaptic()
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_DPAD_LEFT,
                                    NativeKeyEvent.KEYCODE_MEDIA_REWIND,
                                    NativeKeyEvent.KEYCODE_J -> {
                                        doSeekBack()
                                        performConfirmHaptic()
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_DPAD_UP,
                                    NativeKeyEvent.KEYCODE_VOLUME_UP -> {
                                        adjustStreamMusicVolume(context, up = true)
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_DPAD_DOWN,
                                    NativeKeyEvent.KEYCODE_VOLUME_DOWN -> {
                                        adjustStreamMusicVolume(context, up = false)
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_F,
                                    NativeKeyEvent.KEYCODE_F1, NativeKeyEvent.KEYCODE_F2,
                                    NativeKeyEvent.KEYCODE_F3, NativeKeyEvent.KEYCODE_F4 -> {
                                        toggleOrientation()
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_M -> {
                                        viewModel.toggleMute()
                                        showControls = true
                                        true
                                    }
                                    NativeKeyEvent.KEYCODE_ESCAPE,
                                    NativeKeyEvent.KEYCODE_BACK -> {
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
                .pointerInput(uiState.gesturesEnabled, isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    if (!uiState.gesturesEnabled) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            viewModel.onUserInteraction()
                            if (uiState.isHoldSpeedActive) {
                                viewModel.stopHoldSpeed()
                            } else {
                                showControls = !showControls
                            }
                        },
                        onLongPress = {
                            viewModel.onUserInteraction()
                            if (uiState.holdSpeedEnabled) viewModel.startHoldSpeed()
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
                .pointerInput(uiState.gesturesEnabled, isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    if (!uiState.gesturesEnabled) return@pointerInput
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
            val currentEngine = engine
            if (currentEngine != null) {
                // Effective zoom = pinch zoom × TV baseline zoom. Computed once
                // so the video graphicsLayer and the zoom-gated subtitle logic
                // share the exact same value (no drift). > 1 means the video is
                // scaled/cropped, which is when subtitles would move off-screen.
                val tvBaselineZoom = if (isTv && uiState.tvZoomModePercent != 0f) {
                    1f + (uiState.tvZoomModePercent / 100f)
                } else 1f
                // Suppress zoom while in PiP: the pinch-zoomed crop has no meaning in
                // the floating window, and restoring on exit is automatic since the
                // underlying videoZoom state is untouched.
                val effectiveZoom = if (isInPipMode) 1f else videoZoom * tvBaselineZoom
                val zoomed = effectiveZoom > 1f
                key(currentEngine) {
                    AndroidView(
                        factory = { ctx ->
                            val view = currentEngine.createSurfaceView(ctx)
                            lastAppliedSubtitleStyle = uiState.subtitleStyle
                            viewModel.applySubtitleStyleToView(view)
                            playerViewRef = view
                            view
                        },
                        update = { view ->
                            val currentStyle = uiState.subtitleStyle
                            if (lastAppliedSubtitleStyle != currentStyle) {
                                lastAppliedSubtitleStyle = currentStyle
                                viewModel.applySubtitleStyleToView(view)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = effectiveZoom
                                scaleY = effectiveZoom
                            }
                            // Track the surface's window bounds so the Activity can
                            // supply a source-rect hint for a seamless PiP enter
                            // animation. Stop tracking once in PiP (the system
                            // renders the window then).
                            .onGloballyPositioned { coords ->
                                if (!isInPipMode) {
                                    val r = coords.boundsInWindow()
                                    viewModel.updatePipSourceRect(
                                        android.graphics.Rect(
                                            r.left.toInt(), r.top.toInt(),
                                            r.right.toInt(), r.bottom.toInt(),
                                        )
                                    )
                                }
                            },
                    )

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
                                    text = stringResource(R.string.player_audio_only_on),
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
                            // ExoPlayer: a sibling FrameLayout host the engine
                            // reparents its native SubtitleView / AssSubtitleView
                            // into. Full styling/fidelity is preserved (native
                            // rendering, just relocated). Lifetime follows
                            // key(currentEngine): onRelease detaches the host
                            // before the engine releases, so no subtitle view
                            // orphans in a host the engine no longer feeds.
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.FrameLayout(ctx).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                    }.also { currentEngine.setExternalSubtitleHost(it) }
                                },
                                onRelease = { currentEngine.setExternalSubtitleHost(null) },
                                // Sibling of the zoomed video — explicitly NOT in a
                                // graphicsLayer, so it stays pinned to the screen.
                                modifier = Modifier.fillMaxSize(),
                            )
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
                                MpvSubtitleOverlay(
                                    cue = liveCue,
                                    style = uiState.subtitleStyle,
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
                indicatorSide = uiState.gestureIndicatorSide,
                gesturesEnabled = uiState.gesturesEnabled && !isScreenLocked,
                swipeSeekMaxMs = uiState.swipeSeekMaxMs,
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
                onHapticPulse = remember(activity, viewModel) {
                    {
                        if (viewModel.hapticsEnabled) {
                            activity?.let { act ->
                                val view = act.window.decorView
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                } else {
                                    @Suppress("DEPRECATION")
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
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
                visible = uiState.isHoldSpeedActive,
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
                visible = uiState.trickplayOnSeekGesture && gestureTrickplayVisible,
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
                    countdownSeconds = uiState.autoPlayCountdownSec,
                    autoplayEnabled = uiState.videoAutoplayNext,
                    onPlayNext = { viewModel.playNextEpisode() },
                    onCancel = { viewModel.cancelAutoplay() },
                    onToggleAutoplay = { viewModel.setVideoAutoplayNext(!uiState.videoAutoplayNext) },
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
                val usePin = uiState.usePinForPlayerLock && uiState.hasPin
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

            if (uiState.showVideoStats) {
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
                    playMethod = uiState.playMethod,
                    streamingQuality = uiState.preferredPlayerType.name,
                    playerType = uiState.preferredPlayerType.name,
                    decoderMode = uiState.decoderMode.displayName,
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

            val hasEpisodes = uiState.seriesSeasons.isNotEmpty() && uiState.seasonEpisodes.isNotEmpty()
            val episodeBrowserEnabled = uiState.videoEpisodeBrowserEnabled
            // Previous/Next center-button availability. Derived from the
            // adjacency snapshot fetchAdjacentEpisodes writes alongside
            // nextEpisode, so these stay consistent with the up-next overlay.
            val hasPreviousEpisode = uiState.previousEpisode != null
            val hasNextEpisode = uiState.nextEpisode != null

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
                viewModel.loadRemoteSubtitles()
                viewModel.loadSubtitleCultures()
                viewModel.loadConfiguredSubtitleProviders()
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            // Overflow "Subtitles" entry opens the hub on the Get tab (the
            // former "Get Subtitles" entry point's most useful landing spot).
            val onSubtitleHubClick by remember { mutableStateOf({
                // Reset search/cultures state from any previous item before
                // loading fresh data, so stale results don't leak across items.
                viewModel.resetSubtitleManagerState()
                viewModel.loadRemoteSubtitles()
                viewModel.loadSubtitleCultures()
                viewModel.loadConfiguredSubtitleProviders()
                currentSheet = PlayerSheet.SubtitleHub
            }) }
            val onChapterClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Chapter }) }
            val onInfoClick by remember { mutableStateOf({ currentSheet = PlayerSheet.PlaybackInfo }) }
            val onAspectRatioClick by remember { mutableStateOf({ currentSheet = PlayerSheet.AspectRatio }) }
            val onDialogueBoostClick by remember { mutableStateOf({ viewModel.toggleDialogueBoost() }) }
            val onDialogueBoostStrengthChange by remember { mutableStateOf({ strength: EffectStrength -> viewModel.setDialogueBoostStrength(strength) }) }
            val onNightModeClick by remember { mutableStateOf({ viewModel.toggleNightMode() }) }
            val onNightModeStrengthChange by remember { mutableStateOf({ strength: EffectStrength -> viewModel.setNightModeStrength(strength) }) }
            val onAVSyncClick by remember { mutableStateOf({ currentSheet = PlayerSheet.AVSync }) }
            val onDecoderClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Decoder }) }
            // uiState is a `by collectAsStateWithLifecycle()` delegate, so
            // `uiState.audioPassthrough` is read at invocation time — no key
            // needed and the lambda never goes stale.
            val onPassthroughClick by remember { mutableStateOf({ viewModel.setAudioPassthrough(!uiState.audioPassthrough) }) }
            val onEpisodesClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Episodes }) }
            val onSyncPlayClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SyncPlay }) }
            val onPipClick by remember(onEnterPip) { mutableStateOf({ onEnterPip() }) }
            val onMuteClick by remember { mutableStateOf({ viewModel.toggleMute() }) }
            val onVideoStatsClick by remember { mutableStateOf({ viewModel.toggleVideoStats() }) }
            val onQualityClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Quality }) }
            val onPlaybackModeClick by remember { mutableStateOf({ currentSheet = PlayerSheet.PlaybackMode }) }
            val onAudioNormalizationClick by remember { mutableStateOf({ viewModel.toggleAudioNormalization() }) }
            val onAudioNormalizationModeChange by remember { mutableStateOf({ mode: AudioNormalizationMode -> viewModel.setAudioNormalizationMode(mode) }) }
            val onChannelMixClick by remember { mutableStateOf({ viewModel.toggleChannelMix() }) }
            val onChannelMixModeChange by remember { mutableStateOf({ mode: ChannelMixMode -> viewModel.setChannelMixMode(mode) }) }
            val onSleepTimerClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SleepTimer }) }
            val onVideoFilterClick by remember { mutableStateOf({ currentSheet = PlayerSheet.VideoFilter }) }
            // Capture the current video frame via PixelCopy on the engine's
            // surface view. Backend-agnostic: all three engines render to a
            // SurfaceView, which only PixelCopy (not View.drawToBitmap) can read.
            // The titleHint seeds the MediaStore filename. Result surfaces as a
            // snackbar with the saved path, or a share intent is offered.
            val onScreenshotClick: () -> Unit = remember {
                {
                    val view = playerViewRef
                    if (view != null) {
                        scope.launch { snackbarHostState.showSnackbar("Capturing frame…", duration = SnackbarDuration.Short) }
                        com.raulshma.jellyplay.feature.player.video.ScreenshotSaver.capture(
                            surfaceView = view,
                            titleHint = uiState.title,
                        ) { result ->
                            scope.launch {
                                val msg = when (result) {
                                    is com.raulshma.jellyplay.feature.player.video.ScreenshotSaver.Result.Saved ->
                                        "Frame saved to Pictures/JellyPlay (${result.width}×${result.height})"
                                    is com.raulshma.jellyplay.feature.player.video.ScreenshotSaver.Result.Failed ->
                                        "Capture failed: ${result.reason}"
                                }
                                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
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
                nightModeEnabled = uiState.nightModeEnabled,
                nightModeStrength = uiState.nightModeStrength,
                audioPassthrough = uiState.audioPassthrough,
                segments = uiState.segments,
                resumePositionMs = resumePositionMs,
                playMethod = uiState.playMethod,
                hdrType = uiState.hdrType,
                mediaStreams = uiState.mediaStreams,
                audioTracks = uiState.audioTracks,
                isConnectionMetered = uiState.isConnectionMetered,
                subtitleDelayMs = uiState.subtitleStyle.offsetMs,
                showPlaybackMetadata = uiState.showPlaybackMetadata,
                showClock = uiState.showClock,
                showTimeRemaining = uiState.showTimeRemaining,
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
                syncPlayGroupName = uiState.syncPlayGroupName,
                syncPlayParticipantCount = uiState.syncPlayParticipantCount,
                isSyncPlaySynced = uiState.isSyncPlaySynced,
                isSyncPlaySyncing = uiState.isSyncPlaySyncing,
                showVideoStats = uiState.showVideoStats,
                onVideoStatsClick = onVideoStatsClick,
                streamingQuality = uiState.streamingQuality,
                playbackMode = uiState.playbackMode,
                onQualityClick = onQualityClick,
                onPlaybackModeClick = onPlaybackModeClick,
                audioNormalizationMode = uiState.audioNormalizationMode,
                audioNormalizationEnabled = uiState.audioNormalizationEnabled,
                channelMixMode = uiState.channelMixMode,
                channelMixEnabled = uiState.channelMixEnabled,
                supportsAudioNormalization = uiState.engineCapabilities.supportsAudioNormalization,
                supportsChannelMixing = uiState.engineCapabilities.supportsChannelMixing,
                supportsLiveQualitySwitch = uiState.engineCapabilities.supportsLiveQualitySwitch,
                onAudioNormalizationClick = onAudioNormalizationClick,
                onAudioNormalizationModeChange = onAudioNormalizationModeChange,
                onChannelMixClick = onChannelMixClick,
                onChannelMixModeChange = onChannelMixModeChange,
                sleepTimerActive = uiState.sleepTimerActive,
                sleepTimerEndOfEpisode = uiState.sleepTimerEndOfEpisode,
                sleepTimerRemainingFlow = viewModel.sleepTimerRemainingMs,
                onSleepTimerClick = onSleepTimerClick,
                supportsVideoFilters = uiState.engineCapabilities.supportsVideoFilters,
                videoFiltersActive = uiState.videoEffects != com.raulshma.jellyplay.core.model.VideoEffectsConfig(),
                onVideoFilterClick = onVideoFilterClick,
                supportsScreenshot = uiState.engineCapabilities.supportsScreenshot,
                onScreenshotClick = onScreenshotClick,
                abRepeatActive = uiState.abRepeat.isActive,
                onAbRepeatToggle = { viewModel.setAbRepeatEnabled(!uiState.abRepeat.enabled) },
                onAbRepeatSetA = { viewModel.setAbRepeatPointA() },
                onAbRepeatSetB = { viewModel.setAbRepeatPointB() },
                onAbRepeatClear = { viewModel.clearAbRepeat() },
                audioOnly = uiState.audioOnly,
                onToggleAudioOnly = { viewModel.toggleAudioOnly() },
                onLockClick = onLockClick,
                onControlsFocusChange = onControlsFocusChange,
                onOverflowMenuChange = onOverflowMenuChange,
                castManager = viewModel.castManagerField,
                modifier = Modifier.fillMaxSize(),
            )
            } // end PlayerDarkTheme (control bars)

            AnimatedVisibility(
                visible = !isTv && uiState.trickplayEnabled && showControls && isSeeking,
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
                isSeeking && uiState.trickplayEnabled && uiState.trickplayInfo != null,
                uiState.trickplayInfo,
            )
        }
            .conflate()
            .distinctUntilChanged()
            .collect { (pos, shouldFetch, _) ->
                if (shouldFetch) {
                    val bitmap = viewModel.getTrickplayThumbnail(pos)
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
                isGestureSeeking && uiState.trickplayOnSeekGesture,
                uiState.trickplayInfo,
            )
        }
            .conflate()
            .distinctUntilChanged()
            .collect { (pos, shouldFetch, trickplayInfo) ->
                if (shouldFetch) {
                    gestureTrickplayVisible = true
                    gestureTrickplayBitmap = if (trickplayInfo != null) {
                        viewModel.getTrickplayThumbnail(pos)
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
            val timeout = if (isTv) uiState.controlsTimeoutMs * 2 else uiState.controlsTimeoutMs
            delay(timeout)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                viewModel.castSessionEvents.collect { event ->
                    when (event) {
                        is CastSessionEvent.Connected -> viewModel.castToDevice()
                        is CastSessionEvent.Disconnected -> viewModel.onCastDisconnected()
                    }
                }
            }
            launch {
                viewModel.syncPlayNotifications.collect { message ->
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
        sleepTimerRemainingFlow = viewModel.sleepTimerRemainingMs,
        doSeekTo = doSeekTo,
        viewModel = viewModel,
        itemId = itemId,
        syncPlayIgnoreWait = syncPlayIgnoreWait,
        onLoadLocalSubtitle = {
            localSubtitleLauncher.launch(
                arrayOf(
                    "application/x-subrip",
                    "text/vtt",
                    "text/plain",
                    "text/x-ssa",
                    "application/ttml+xml",
                )
            )
        },
        onPickFont = {
            fontPickerLauncher.launch(arrayOf("*/*"))
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
        text = stringResource(R.string.player_video_aspect_auto, detectedAspectRatio?.displayName ?: ""),
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
    val context = LocalContext.current

    when (val sheet = currentSheet) {
        is PlayerSheet.Speed -> {
            SpeedPickerSheet(
                currentSpeed = uiState.playbackSpeed,
                onSelect = { viewModel.setPlaybackSpeed(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Audio -> {
            TrackPickerSheet(
                title = stringResource(R.string.player_audio),
                tracks = uiState.audioTracks,
                onSelect = { viewModel.selectAudioTrack(it) },
                onReset = if (uiState.hasAudioOverride) { { viewModel.resetAudioTrack() } } else null,
                onDismiss = dismissSheet,
                footer = if (uiState.seriesId != null) {
                    {
                        // Per-series audio-language preference toggle. Saving
                        // remembers the currently-selected track's language for
                        // every episode of this series; toggling off forgets it.
                        RememberPreferenceToggle(
                            label = stringResource(R.string.player_video_remember_audio_language),
                            checked = uiState.hasSeriesAudioPref,
                            onToggle = { remember ->
                                val lang = if (remember) {
                                    uiState.audioTracks.firstOrNull { it.isSelected && it.index >= 0 }?.language
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
                viewModel.loadRemoteSubtitles()
                viewModel.loadSubtitleCultures()
                viewModel.loadConfiguredSubtitleProviders()
            }
            SubtitleHubSheet(
                initialTab = com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTab.TRACKS,
                onDismiss = dismissSheet,
                // Tracks tab
                subtitleTracks = uiState.subtitleTracks,
                onSelectSubtitleTrack = { viewModel.selectSubtitleTrack(it) },
                onResetSubtitleTrack = if (uiState.hasSubtitleOverride) {
                    { viewModel.resetSubtitleTrack() }
                } else null,
                tracksFooter = if (uiState.seriesId != null) {
                    {
                        // Per-series subtitle preference toggle. With a real track
                        // selected it saves that track's language + role so every
                        // episode restores the right same-language track; with the
                        // "Off" row selected it saves a "subtitles off" intent so
                        // every episode loads with subs off. Toggling off forgets
                        // whichever intent was saved.
                        val selectedOff = uiState.subtitleTracks
                            .firstOrNull { it.isSelected && it.index < 0 } != null
                        val label = if (selectedOff || uiState.hasSeriesSubtitleOffPref) {
                            stringResource(R.string.player_video_remember_subtitles_off)
                        } else {
                            stringResource(R.string.player_video_remember_subtitle_language)
                        }
                        RememberPreferenceToggle(
                            label = label,
                            checked = uiState.hasSeriesSubtitlePref,
                            onToggle = { remember ->
                                val sel = uiState.subtitleTracks.firstOrNull { it.isSelected }
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
                downloadSubtitles = uiState.remoteSubtitles,
                isDownloading = uiState.isLoadingRemoteSubtitles,
                onDownload = { viewModel.downloadSubtitle(it) },
                onLoadLocalFile = onLoadLocalSubtitle,
                searchResults = uiState.searchedSubtitles,
                isSearching = uiState.isSearchingSubtitles,
                hasSearched = uiState.hasSearchedSubtitles,
                searchError = uiState.subtitleSearchError,
                cultures = uiState.subtitleCultures,
                defaultLanguage = uiState.defaultSearchLanguage,
                onSearch = { viewModel.searchRemoteSubtitles(it) },
                onDownloadSearched = { viewModel.downloadSubtitle(it) },
                providerSearchResults = uiState.providerSearchResults,
                providerSearchErrors = uiState.providerSearchErrors,
                configuredProviders = uiState.configuredSubtitleProviders,
                onSearchAllProviders = { viewModel.searchAllSubtitleProviders(it) },
                onDownloadProviderSubtitle = { viewModel.downloadProviderSubtitle(it) },
                downloadingSubtitles = uiState.downloadingSubtitles,
                // "Use" affordance: the hub switches to its Tracks tab itself;
                // this callback is a no-op placeholder for the host.
                onUseSubtitle = {},
                isUploading = uiState.isUploadingSubtitle,
                onUpload = { uri, fileName, language, isForced, isHearingImpaired ->
                    viewModel.uploadSubtitle(uri, fileName, language, isForced, isHearingImpaired)
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
            PlayerModalBottomSheet(
                onDismissRequest = dismissSheet,
                sheetState = rememberModalBottomSheetState(),
            ) {
                PlaybackInfoOverlay(
                    mediaSource = uiState.currentMediaSource,
                    mediaStreams = uiState.mediaStreams,
                    playMethod = uiState.playMethod,
                    isConnectionMetered = uiState.isConnectionMetered,
                    hdrType = uiState.hdrType,
                    playerType = uiState.preferredPlayerType.name,
                    decoderMode = uiState.decoderMode.name,
                    aspectRatio = uiState.aspectRatio.name,
                    nightModeEnabled = uiState.nightModeEnabled,
                    nightModeStrength = uiState.nightModeStrength,
                    dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                    dialogueBoostStrength = uiState.dialogueBoostStrength,
                    audioPassthrough = uiState.audioPassthrough,
                    audioTracks = uiState.audioTracks,
                    subtitleTracks = uiState.subtitleTracks,
                    playbackSpeed = uiState.playbackSpeed,
                    audioDelayMs = uiState.audioDelayMs,
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
                currentRatio = uiState.aspectRatio,
                detectedRatio = uiState.detectedAspectRatio,
                onSelect = { viewModel.setAspectRatio(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.AVSync -> {
            AVSyncSheet(
                currentAudioDelayMs = uiState.audioDelayMs,
                onAudioDelayChange = { viewModel.setAudioDelay(it) },
                onDismiss = dismissSheet,
                audioDelaySupported = uiState.engineCapabilities.supportsAudioDelay,
            )
        }
        is PlayerSheet.Decoder -> {
            DecoderPickerSheet(
                currentMode = uiState.decoderMode,
                onSelect = { viewModel.setDecoderMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Episodes -> {
            EpisodePickerSheet(
                seasons = uiState.seriesSeasons,
                episodes = uiState.seasonEpisodes,
                currentSeasonId = uiState.currentSeasonId,
                currentEpisodeId = itemId,
                isLoading = uiState.isLoadingEpisodes,
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
            SyncPlayPlayerSheet(
                groupName = uiState.syncPlayGroupName ?: "Group",
                participantCount = uiState.syncPlayParticipantCount,
                isSynced = uiState.isSyncPlaySynced,
                isPlaying = uiState.isPlaying,
                ignoreWait = syncPlayIgnoreWait,
                repeatMode = uiState.syncPlayRepeatMode,
                shuffleMode = uiState.syncPlayShuffleMode,
                onRepeatModeChange = { viewModel.setSyncPlayRepeatMode(it) },
                onShuffleModeChange = { viewModel.setSyncPlayShuffleMode(it) },
                onTogglePlayPause = { viewModel.syncPlayTogglePlayPause() },
                onStop = { viewModel.syncPlayStop() },
                onLeave = {
                    viewModel.leaveSyncPlay()
                    onSheetChange(PlayerSheet.None)
                },
                onIgnoreWaitChange = { viewModel.syncPlaySetIgnoreWait(it) },
                 onDismiss = dismissSheet,
             )
         }
        is PlayerSheet.Quality -> {
            QualityPickerSheet(
                currentQuality = uiState.streamingQuality,
                adaptiveBitrateEnabled = uiState.adaptiveBitrateEnabled,
                onToggleAdaptiveBitrate = { viewModel.setAdaptiveBitrateEnabled(it) },
                onSelect = { viewModel.setStreamingQuality(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackMode -> {
            PlaybackModeSheet(
                currentMode = uiState.playbackMode,
                onSelect = { viewModel.setPlaybackMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SleepTimer -> {
            SleepTimerSheetBinder(
                isActive = uiState.sleepTimerActive,
                isEndOfEpisodeMode = uiState.sleepTimerEndOfEpisode,
                lastUsedDurationMs = uiState.sleepTimerLastUsedDurationMs,
                sleepTimerRemainingFlow = sleepTimerRemainingFlow,
                onSelectDuration = { viewModel.startSleepTimer(it) },
                onSelectEndOfEpisode = { viewModel.startSleepTimerEndOfEpisode() },
                onCancel = { viewModel.cancelSleepTimer() },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.VideoFilter -> {
            VideoFilterSheet(
                currentEffects = uiState.videoEffects,
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
