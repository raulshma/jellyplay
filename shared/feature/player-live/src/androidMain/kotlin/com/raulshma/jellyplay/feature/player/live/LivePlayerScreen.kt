package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.message.asText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.raulshma.jellyplay.core.designsystem.theme.PlayerDarkTheme
import com.raulshma.jellyplay.core.model.DpadControl
import com.raulshma.jellyplay.core.ui.player.findActivity
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsExit
import com.raulshma.jellyplay.core.ui.player.playerTopControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerTopControlsExit
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.DpadAction
import com.raulshma.jellyplay.core.ui.tv.input.DpadKeyHandlerScope
import com.raulshma.jellyplay.core.ui.tv.input.dpadKeyHandler
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.live.components.ChannelZapToast
import com.raulshma.jellyplay.feature.player.live.components.LiveChannelListSheet
import com.raulshma.jellyplay.feature.player.live.components.LiveErrorBanner
import com.raulshma.jellyplay.feature.player.live.components.LivePlayerBottomBar
import com.raulshma.jellyplay.feature.player.live.components.LivePlayerTopBar
import com.raulshma.jellyplay.feature.player.live.components.LiveRecordSheet
import com.raulshma.jellyplay.feature.player.live.components.LiveStreamOptionSheet
import com.raulshma.jellyplay.feature.player.live.engine.Media3LivePlayerEngine
import com.raulshma.jellyplay.feature.player.video.PlayerOrientationLock
import com.raulshma.jellyplay.feature.player.video.PlayerTvFocusGrabEffect
import com.raulshma.jellyplay.feature.player.video.PlayerTvKeySinkEffect
import com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromeBackVerdict
import com.raulshma.jellyplay.feature.player.video.chrome.LIVE_SEEK_STEP_MS
import com.raulshma.jellyplay.feature.player.video.chrome.liveWindowRefreshLoop
import com.raulshma.jellyplay.feature.player.video.chrome.rememberPlayerChromeController
import com.raulshma.jellyplay.feature.player.video.chrome.seekBackTargetMs
import com.raulshma.jellyplay.feature.player.video.chrome.seekForwardTargetMs
import com.raulshma.jellyplay.feature.player.video.chrome.shouldRestoreHostWindowOnDispose
import com.raulshma.jellyplay.feature.player.video.rememberPlayerWindowOps
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputDispatcher
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.core.model.PlayerAction

private const val ZAP_TOAST_MS = 3_000L

/**
 * Which bottom sheet (if any) is open over the live player. The more-menu
 * can open either the channel list or the stream-option picker; the record
 * button opens the record sheet.
 */
private enum class LiveSheet { Channels, StreamOption, Record }

@Composable
@UnstableApi
fun LivePlayerScreen(
    channelId: String,
    channelName: String,
    audioStreamIndex: Int?,
    subtitleStreamIndex: Int?,
    onBack: () -> Unit,
    viewModel: LiveTvPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Error-message resolution seam (player-live conveyor): the shared VM
    // stores unresolved LivePlayerMessage values (no Context in commonMain) —
    // collapse to the localized string here, where LiveErrorBanner renders.
    val errorMessageText = state.errorMessage?.asText()

    // One-shot screen events (the VOD SessionEvent collector shape): record/
    // cancel feedback resolves Resource values with the collecting
    // composition's locale and forwards through the app-wide bus — the VM no
    // longer touches the Android-only UserMessageBus/UiText machinery — and
    // the VM's PiP-dismiss collector fires ClosePlayer when the engine died
    // inside the PiP window, so the player leaves instead of lingering
    // behind the closing window.
    val messageBus = LocalUserMessageBus.current
    LaunchedEffect(messageBus) {
        viewModel.events.collect { event ->
            when (event) {
                is LivePlayerEvent.Message ->
                    when (val message = event.message) {
                        is UiMessage.Resource ->
                            messageBus.info(getString(message.res, *message.args.toTypedArray()))
                        is UiMessage.Raw -> messageBus.error(message.text)
                    }
                LivePlayerEvent.ClosePlayer -> onBack()
            }
        }
    }
    var activeSheet by remember { mutableStateOf<LiveSheet?>(null) }
    var zapToastChannelId by remember { mutableStateOf<String?>(null) }
    val isTv = LocalTvMode.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // Host-window seam (the VOD screen's PlayerWindowOps): system bars,
    // keep-screen-on and the orientation lock ride the shared ops instead of
    // raw WindowCompat/FLAG_ calls.
    val windowOps = rememberPlayerWindowOps()

    // The chrome module (the GLOSSARY "Player chrome module"): the overlay
    // visibility, the auto-hide timer, the BACK hide-or-exit ladder and the
    // focus-grab arming — the live screen's former parallel copies of all
    // four (the gate mapping, the ladder, the arm set) are deleted; the
    // module's defaults carry them. Live maps onto the shared auto-hide gate
    // with NO new parameter: constant false for the concepts the live chrome
    // lacks — seek-gesture suppression, overflow menu, focused-chrome
    // suppression (a focused live control never suppresses the hide, TV
    // included; the contract test's liveScreen_shape case pins exactly this
    // mapping). The visibility state stays plain `remember` (persistVisible
    // = false), the same lifetime the screen-local flag had.
    val chrome = rememberPlayerChromeController(
        isTv = isTv,
        controlsTimeoutMs = { state.controlsTimeoutMs },
        isSheetOpen = { activeSheet != null },
    )

    // The input-dispatch module's instance (the GLOSSARY "Input dispatch
    // module"): live's D-pad surface resolves its arm gates through the
    // persisted mapping — a disabled/NONE row silences the live arm, a
    // rebound row routes where live can express it. The map is read at
    // EVENT time through the state delegate; the rebound executor covers
    // exactly the actions live can express (the zap family has no
    // PlayerAction — its arms stay the fixed defaults behind the enable
    // gate).
    val currentOnBack by rememberUpdatedState(onBack)
    val inputDispatcher = remember {
        PlayerInputDispatcher(
            getMap = { state.inputMap },
            executeAction = { action ->
                when (action) {
                    PlayerAction.TOGGLE_PLAY_PAUSE -> {
                        viewModel.onEvent(LiveTvPlayerUiEvent.TogglePlayPause)
                        true
                    }
                    PlayerAction.TOGGLE_MUTE -> {
                        viewModel.onEvent(LiveTvPlayerUiEvent.ToggleMute)
                        true
                    }
                    PlayerAction.TOGGLE_CONTROLS -> {
                        chrome.toggle()
                        true
                    }
                    PlayerAction.EXIT_PLAYER -> {
                        currentOnBack()
                        true
                    }
                    else -> false
                }
            },
        )
    }

    // Initialize playback once when the screen enters. A *changed* channelId
    // on a live screen instance (the host PlayerActivity's onNewIntent args
    // swap — e.g. picking another channel from the browse UI while in PiP)
    // must stop-and-retune first: the VM is activity-scoped and its
    // `initialized` latch would otherwise no-op, keeping the old channel
    // playing under the new route.
    var tunedChannelId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(channelId) {
        if (tunedChannelId != null && tunedChannelId != channelId) viewModel.stop()
        tunedChannelId = channelId
        viewModel.onEvent(LiveTvPlayerUiEvent.Initialize(channelId, audioStreamIndex, subtitleStreamIndex))
    }

    // Release the engine when the screen leaves composition — not just on
    // activity destroy. The live VM is activity-scoped (nav3 entries here have
    // no per-entry ViewModelStore owner), so without this a back-press leaves
    // the ExoPlayer alive and audio keeps playing in the background.
    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    // Phone (non-TV): consume the system/gesture back so a predictive-back
    // gesture becomes a normal synchronous pop instead of a simultaneous
    // target+initial compose. The shared SaveableStateHolder crashes with
    // "Key ChannelDetail(...) was used multiple times" when the revealed
    // detail route and the player both compose against it during the gesture.
    // TV uses the D-pad onBack handler below instead.
    if (!isTv) {
        JellyPlayBackHandler(enabled = true) {
            // The chrome module's ONE back ladder (sheet → close, overlay →
            // hide, else exit) — the TV BACK arm below runs the same verdict,
            // so the two dispatch paths can never diverge.
            when (chrome.backVerdict()) {
                PlayerChromeBackVerdict.CloseSheet -> activeSheet = null
                PlayerChromeBackVerdict.HideControls -> chrome.hide()
                PlayerChromeBackVerdict.SummonControls -> {}
                PlayerChromeBackVerdict.Exit -> onBack()
            }
        }
    }

    // Immersive fullscreen + orientation lock + keep-screen-on — through the
    // shared player-video window seam (AndroidPlayerWindowOps wraps the exact
    // WindowCompat insets-controller / FLAG_KEEP_SCREEN_ON calls these
    // effects used to inline; the orientation constants ride the
    // PlayerOrientationLock vocabulary).
    DisposableEffect(activity) {
        windowOps.hideSystemBars()
        windowOps.lockOrientation(
            if (isTv) PlayerOrientationLock.TV_LANDSCAPE
            else PlayerOrientationLock.USER_LANDSCAPE,
        )
        windowOps.setKeepScreenOn(true)

        onDispose {
            // The PiP-transition teardown gate (the shared player-contract
            // decision; VOD's PlayerWindowSessionEffects dispose folds the
            // same gate inline): a screen torn down behind a PiP window —
            // launching other content from browse — must NOT re-show the
            // system bars or reset the orientation over PlayerActivity's
            // DELIBERATE PiP-entry bar-show (the relayout that anchors the
            // gesture-nav handle at the bottom).
            if (shouldRestoreHostWindowOnDispose(currentlyInPip = windowOps.isInPipMode)) {
                // restoreOnPlayerExit bundles the host-window teardown the
                // screen used to do inline: unlock orientation, clear
                // FLAG_KEEP_SCREEN_ON, re-show the system bars (plus the
                // brightness/display-mode restores live never armed — no-ops
                // here). Restore the browse host's default orientation
                // unconditionally: the captured "original" could itself be a
                // landscape lock pushed by an earlier screen, which would
                // chain the leak; MainActivity never legitimately needs a
                // non-default orientation outside a player.
                windowOps.restoreOnPlayerExit(PlayerOrientationLock.UNSPECIFIED)
            }
        }
    }

    // Re-hide system bars when the window regains focus (PiP/multiwindow return).
    DisposableEffect(activity) {
        windowOps.hideSystemBars()
        onDispose {}
    }

    // Position ticker — drives the seek bar + at-live-edge while playing.
    // The live engine is media3's PULL model — it only republishes position/
    // duration/live-edge when `refreshLiveWindow()` is called — so a poll
    // cadence is required (no engine flow ticks on its own). The loop's
    // gating semantics are the shared player-contract `liveWindowRefreshLoop`;
    // consumers of the refreshed flows all render inside the
    // AnimatedVisibility chrome and leave composition when it hides, so only
    // poll while the overlay is visible. isAtLiveEdge stays correct while
    // hidden via the engine's playback-state listener.
    LaunchedEffect(state.isPlaying, chrome.visible) {
        liveWindowRefreshLoop(active = { state.isPlaying && chrome.visible }) {
            viewModel.onEvent(LiveTvPlayerUiEvent.RefreshPosition)
        }
    }

    // Controls auto-hide: delay sourced from the user's
    // `videoControlsTimeoutMs` preference (mirrors the VOD player). The timer
    // itself is the chrome module's — this effect only re-arms it on the same
    // keys the former inline loop keyed (channel change restarts the window;
    // the gate re-reads the open sheet at arm time, exactly as before).
    LaunchedEffect(chrome.visible, state.currentIndex, state.controlsTimeoutMs) {
        chrome.scheduleAutoHide()
    }

    // Zap toast visibility: shows on channel change, hides after 3s.
    LaunchedEffect(state.currentIndex) {
        zapToastChannelId = state.currentChannel?.id
        delay(ZAP_TOAST_MS)
        if (zapToastChannelId == state.currentChannel?.id) zapToastChannelId = null
    }

    // TV: own focus from entry. The player root's D-pad handlers only fire while the
    // root holds focus; when the error banner shows, its retry button takes over
    // (mirrors the VOD player's overlay-priority focus chain).
    val playerFocusRequester = remember { FocusRequester() }
    val errorRetryFocusRequester = remember { FocusRequester() }
    // Whether ANYTHING under the player Box holds focus (banner retry button
    // included) — the grab seam's re-assert watch, fed by the Box's
    // onFocusChanged below.
    var liveLayerHoldsFocus by remember { mutableStateOf(false) }
    LaunchedEffect(isTv, state.errorMessage) {
        if (isTv) {
            if (state.errorMessage != null) {
                errorRetryFocusRequester.tryRequestFocus("tv_live_error")
            } else {
                playerFocusRequester.tryRequestFocus("tv_live_player")
            }
        }
    }
    // The TV D-pad arm set, built through the input-dispatch module's ONE
    // dispatcher (zap-family enable gates + the SELECT rebind ladder ride the
    // persisted map) and remembered — the Box's focused-dispatch handler and
    // the key-sink fallback below consume this ONE instance, so the two
    // dispatch paths can never diverge and the sink's compiled handler stops
    // being rebuilt per recomposition. The route-arg captures are remember
    // keys: an onNewIntent retune swaps them and the arms must follow.
    val liveDpadConfig = remember(chrome, inputDispatcher, audioStreamIndex, subtitleStreamIndex) {
        liveDpadKeyConfig(
            isSheetOpen = { activeSheet != null },
            dispatcher = inputDispatcher,
            onChannelUp = {
                viewModel.onEvent(LiveTvPlayerUiEvent.ChannelUp(audioStreamIndex, subtitleStreamIndex))
                chrome.hide()
            },
            onChannelDown = {
                viewModel.onEvent(LiveTvPlayerUiEvent.ChannelDown(audioStreamIndex, subtitleStreamIndex))
                chrome.hide()
            },
            onSelect = {
                if (activeSheet != null) {
                    activeSheet = null
                } else {
                    chrome.toggle()
                }
                true
            },
            onBack = {
                when (chrome.backVerdict()) {
                    PlayerChromeBackVerdict.CloseSheet -> activeSheet = null
                    PlayerChromeBackVerdict.HideControls -> chrome.hide()
                    PlayerChromeBackVerdict.SummonControls -> {}
                    PlayerChromeBackVerdict.Exit -> currentOnBack()
                }
                true
            },
            onMenu = {
                // MENU — last dead key: first press summons the
                // overlay. Already-up stays unconsumed (unmapped key, pre-fix
                // inertness).
                if (!chrome.visible) {
                    chrome.show()
                    true
                } else false
            },
        )
    }
    // TV focus grab + re-assert (parity with the VOD screen): this
    // screen had NO hide-edge re-grab at all — when the overlay auto-hid (or a
    // sheet closed, or the error banner unmounted), nothing re-grabbed the root
    // Box and every D-pad key died until something else happened to take
    // focus. The grab runs whenever the layer SHOULD own focus — the chrome
    // module's verdict (controls hidden + sheet closed) with the error banner
    // as this screen's one extra claimant — and the re-assert recovers any
    // later loss, both with the 3-frame retry.
    if (isTv) {
        PlayerTvFocusGrabEffect(
            focusRequester = playerFocusRequester,
            layerComposed = chrome.shouldLayerOwnFocus(state.errorMessage != null),
            targetHoldsFocus = liveLayerHoldsFocus,
        )
    }
    // TV key-sink fallback (parity with the VOD screen): publish
    // the EXACT arm set the Box's D-pad handler runs to the platform bridge,
    // so keys that reach nothing — no Compose focus target left, PlayerActivity's
    // dispatchKeyEvent finding nothing consumed — still zap/toggle/hide.
    if (isTv) {
        PlayerTvKeySinkEffect(
            isSheetOpen = activeSheet != null,
            isOverlayScreenActive = false,
            layerHoldsFocus = liveLayerHoldsFocus,
            onResetLayerFocus = { liveLayerHoldsFocus = false },
            config = liveDpadConfig,
        )
    }

    PlayerDarkTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                // BEFORE the focus target it observes (the grab seam's
                // ordering contract): feeds [liveLayerHoldsFocus] with the
                // subtree's hasFocus — banner retry button included.
                .onFocusChanged { if (isTv) liveLayerHoldsFocus = it.hasFocus }
                .focusable(true)
                .then(
                    if (!isTv) Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            // Toggle chrome on tap when the channel sheet is closed.
                            // Tapping also restarts the auto-hide timer via the
                            // chrome visibility.
                            if (activeSheet == null) chrome.toggle()
                        }
                    } else Modifier,
                )
                // Same [liveDpadConfig] the key-sink fallback interprets —
                // one arm set, two dispatch paths.
                .dpadKeyHandler(liveDpadConfig),
        ) {
            // Surface layer
            val media3Player = remember(state.engineState) {
                (viewModel.engineForRendering() as? Media3LivePlayerEngine)?.media3Player
            }
            if (media3Player != null) {
                // PiP aspect-ratio feed: forward the decoded video's
                // dimensions so the host PlayerActivity shapes the PiP window
                // to the content instead of its 16:9 fallback. Remembered +
                // DisposableEffect-removed: the old factory-resident
                // anonymous listener stacked a new registration on every
                // surface recreation while the VM-scoped engine lived, so each
                // onVideoSizeChanged dispatched once per dead surface too.
                val videoSizeListener = remember(media3Player) {
                    object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            viewModel.onVideoSizeChanged(
                                videoSize.width,
                                videoSize.height,
                            )
                        }
                    }
                }
                DisposableEffect(media3Player) {
                    media3Player.addListener(videoSizeListener)
                    onDispose { media3Player.removeListener(videoSizeListener) }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = media3Player
                            useController = false
                            // TV-key hardening (the VOD EngineVideoSurface's
                            // focus twin): media3's PlayerView is focusable by
                            // default and a View-level focus owner outside Compose
                            // swallows every D-pad key before Compose's dispatch
                            // runs. isFocusable=false is the portable suppression
                            // (the View#descendantFocusability API is removed from
                            // the API-37 android.jar); the surface takes no key or
                            // touch interaction, so free.
                            isFocusable = false
                            isFocusableInTouchMode = false
                            // Buffering is rendered by our own full-screen
                            // LiveErrorBanner (M3 Expressive LoadingIndicator);
                            // disable Media3's native spinner to avoid doubles.
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    },
                )
            }

            val currentChannel = state.currentChannel

            // Chrome layer — top bar
            AnimatedVisibility(
                visible = chrome.visible && currentChannel != null,
                enter = playerTopControlsEnter(),
                exit = playerTopControlsExit(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                if (currentChannel != null) {
                    val currentProgram = state.currentProgram
                    LivePlayerTopBar(
                        channel = currentChannel,
                        logoUrl = viewModel.logoUrlFor(currentChannel),
                        isMuted = state.isMuted,
                        playMethod = state.playMethod,
                        // Record affordance only when a program is
                        // airing (no program → nothing to record). Red-tinted
                        // while a timer is already scheduled on the program.
                        isRecording = currentProgram?.let {
                            !it.timerId.isNullOrEmpty() || !it.seriesTimerId.isNullOrEmpty()
                        } ?: false,
                        canRecord = currentProgram != null,
                        onBack = onBack,
                        onMute = { viewModel.onEvent(LiveTvPlayerUiEvent.ToggleMute) },
                        onRecord = { activeSheet = LiveSheet.Record },
                    )
                }
            }

            // Chrome layer — bottom bar
            AnimatedVisibility(
                visible = chrome.visible,
                enter = playerBottomControlsEnter(),
                exit = playerBottomControlsExit(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // High-frequency DVR-window values are collected here (not via
                // the screen-root uiState) so the 500 ms position tick
                // recomposes only this bar, mirroring the VOD leaf-collected
                // position flows.
                val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
                val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
                val canSeek = durationMs > 0L && !state.isAtLiveEdge
                LivePlayerBottomBar(
                    isPlaying = state.isPlaying,
                    canSeek = canSeek,
                    isAtLiveEdge = state.isAtLiveEdge,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPause = { viewModel.onEvent(LiveTvPlayerUiEvent.TogglePlayPause) },
                    onSeekBack = {
                        // Shared step-seek policy (the VOD screen's math):
                        // floor at zero, no upper clamp on the back path.
                        viewModel.onEvent(
                            LiveTvPlayerUiEvent.SeekWithinDvr(seekBackTargetMs(positionMs, LIVE_SEEK_STEP_MS)),
                        )
                    },
                    onSeekForward = {
                        // Same policy's forward path: cap at the DVR window's
                        // duration when it is known (the no-duration arm
                        // passes through unchanged — pure-live streams).
                        viewModel.onEvent(
                            LiveTvPlayerUiEvent.SeekWithinDvr(
                                seekForwardTargetMs(positionMs, LIVE_SEEK_STEP_MS, durationMs),
                            ),
                        )
                    },
                    onPlayFromStart = { viewModel.onEvent(LiveTvPlayerUiEvent.PlayFromStart) },
                    onChannelUp = { viewModel.onEvent(LiveTvPlayerUiEvent.ChannelUp(audioStreamIndex, subtitleStreamIndex)) },
                    onChannelDown = { viewModel.onEvent(LiveTvPlayerUiEvent.ChannelDown(audioStreamIndex, subtitleStreamIndex)) },
                    onMore = { activeSheet = LiveSheet.StreamOption },
                    onChannels = { activeSheet = LiveSheet.Channels },
                    onSeek = { viewModel.onEvent(LiveTvPlayerUiEvent.SeekWithinDvr(it)) },
                    onSeekToLiveEdge = { viewModel.onEvent(LiveTvPlayerUiEvent.SeekToLiveEdge) },
                )
            }

            // Zap toast
            AnimatedVisibility(
                visible = zapToastChannelId != null && currentChannel != null,
                enter = playerTopControlsEnter(),
                exit = playerTopControlsExit(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
            ) {
                if (currentChannel != null) {
                    ChannelZapToast(
                        channel = currentChannel,
                        currentProgram = state.currentProgram,
                        logoUrl = viewModel.logoUrlFor(currentChannel),
                    )
                }
            }

            // Sheets
            when (activeSheet) {
                LiveSheet.Channels -> {
                    LiveChannelListSheet(
                        channels = state.channels,
                        currentChannelId = state.currentChannel?.id,
                        favorites = state.favorites,
                        lastChannelId = state.lastChannelId,
                        logoUrlFor = viewModel::logoUrlFor,
                        onChannelSelected = { id ->
                            viewModel.onEvent(LiveTvPlayerUiEvent.SelectChannelById(id))
                            chrome.hide()
                        },
                        onToggleFavorite = { viewModel.onEvent(LiveTvPlayerUiEvent.ToggleFavorite(it)) },
                        onDismiss = { activeSheet = null },
                    )
                }
                LiveSheet.StreamOption -> {
                    LiveStreamOptionSheet(
                        currentOption = state.liveStreamOption,
                        onSelect = { viewModel.onEvent(LiveTvPlayerUiEvent.SetLiveStreamOption(it)) },
                        onDismiss = { activeSheet = null },
                    )
                }
                LiveSheet.Record -> {
                    LiveRecordSheet(
                        program = state.currentProgram,
                        onRecordOnce = { viewModel.onEvent(LiveTvPlayerUiEvent.RecordCurrentProgramOnce) },
                        onRecordSeries = { viewModel.onEvent(LiveTvPlayerUiEvent.RecordCurrentProgramSeries) },
                        onCancelTimer = { viewModel.onEvent(LiveTvPlayerUiEvent.CancelCurrentProgramTimer) },
                        onCancelSeries = { viewModel.onEvent(LiveTvPlayerUiEvent.CancelCurrentProgramSeries) },
                        onDismiss = { activeSheet = null },
                    )
                }
                null -> {}
            }

            // Error / loading
            if (state.isBuffering || state.errorMessage != null) {
                LiveErrorBanner(
                    isBuffering = state.isBuffering,
                    errorMessage = errorMessageText,
                    errorDetail = state.errorDetail,
                    currentOption = state.liveStreamOption,
                    onRetry = { viewModel.onEvent(LiveTvPlayerUiEvent.Retry(audioStreamIndex, subtitleStreamIndex)) },
                    onRetryWithOption = { viewModel.onEvent(LiveTvPlayerUiEvent.SetLiveStreamOption(it)) },
                    onBack = onBack,
                    retryFocusRequester = errorRetryFocusRequester,
                )
            }
        }
    }
}

/**
 * The live TV D-pad arm set — the inline arms the Box's [dpadKeyHandler] ran
 * before the extraction (channel zap acts on KeyUp), shared with the
 * key-sink fallback via [PlayerTvKeySinkEffect] so the two dispatch paths
 * can never diverge.
 *
 * The input-dispatch module rides the persisted map: the zap arms
 * ([DpadControl.UP]/[DpadControl.DOWN]) have no [com.raulshma.jellyplay.core.model.PlayerAction]
 * to name, so their CURRENT live behavior IS their default binding — the map
 * resolution gates them on the row's enable state (a disabled/NONE row
 * silences the zap, a rebound row leaves the zap untouched), while SELECT
 * resolves through the shared discrete-arm ladder (default
 * [com.raulshma.jellyplay.core.model.PlayerAction.TOGGLE_CONTROLS] — exactly
 * today's toggle; a rebound row routes to live's own executor, which covers
 * the actions live can express). BACK keeps its fixed ladder (it IS this
 * screen's TV back pipeline — falling through would strand a live TV viewer)
 * and MENU stays unmapped.
 *
 * The edge guards are DECLARED, not "verbatim as at HEAD": at HEAD the arms
 * never ran on TV at all ([dpadKeyHandler] is a no-op without a
 * [LocalTvMode] provider, which PlayerActivity only started supplying with
 * this fix — the no-guard shape was unobservable). With the arms live, the
 * no-guard shape is a bug: SELECT toggling on BOTH edges of one press
 * is a net no-op (the KeyDown summons the overlay, the KeyUp 32 ms later
 * hides it again), so SELECT/BACK act on the first KeyDown only and ignore
 * KeyRepeat (a held key must not re-toggle or walk the back ladder).
 */
internal fun liveDpadKeyConfig(
    isSheetOpen: () -> Boolean,
    dispatcher: PlayerInputDispatcher,
    onChannelUp: () -> Unit,
    onChannelDown: () -> Unit,
    onSelect: () -> Boolean,
    onBack: () -> Boolean,
    onMenu: () -> Boolean,
): DpadKeyHandlerScope.() -> Unit {
    return {
        onKeyEvent(DpadAction.Up) { key ->
            when {
                isSheetOpen() || !dispatcher.isControlEnabled(DpadControl.UP) -> false
                key.isKeyUp -> {
                    onChannelUp()
                    true
                }
                else -> false
            }
        }
        onKeyEvent(DpadAction.Down) { key ->
            when {
                isSheetOpen() || !dispatcher.isControlEnabled(DpadControl.DOWN) -> false
                key.isKeyUp -> {
                    onChannelDown()
                    true
                }
                else -> false
            }
        }
        // BACK acts on the first KeyDown only (no KeyUp re-fire, no KeyRepeat):
        // this arm IS the TV back ladder (the screen composes no
        // JellyPlayBackHandler on TV), and an unguarded arm fired on BOTH edges
        // of one press — KeyDown hid the overlay, the KeyUp 32 ms later read it
        // hidden and fell to onBack(): one BACK press hid the chrome AND exited
        // the player.
        onKeyEvent(DpadAction.Select) { key ->
            if (key.isKeyDown && key.repeatCount == 0) {
                dispatcher.discreteArm(key, DpadControl.SELECT, PlayerAction.TOGGLE_CONTROLS, legacy = onSelect)
            } else {
                false
            }
        }
        onKeyEvent(DpadAction.Back) { key -> if (key.isKeyDown && key.repeatCount == 0) onBack() else false }
        onKeyEvent(DpadAction.Menu) { key -> if (key.isKeyDown && key.repeatCount == 0) onMenu() else false }
    }
}
