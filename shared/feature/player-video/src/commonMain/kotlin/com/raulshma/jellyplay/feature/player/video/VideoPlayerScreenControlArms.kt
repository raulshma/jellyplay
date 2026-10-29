package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.feature.player.video.session.ControlsSessionSlice

/**
 * The SheetControls / GestureControls arms the screen root cannot build
 * inline: VideoPlayerScreen's line-ceiling ratchet
 * (ControllerOwnershipTest.videoPlayerScreen_totalLineCeiling) keeps the
 * screen closed, and each fresh `{ ... }` passed inline there allocated a
 * new lambda per recomposition — the hoisting law documented at the
 * onPlayPause precedent. One remembered holder; every lambda captures only
 * invocation-time-stable handles (the ViewModel, the controls session
 * slice, the openSheet opener, the two screen-state closures). The A-B
 * repeat toggle reads the slice's abRepeat state at invocation time —
 * never keyed on its value — so the remembered lambda stays live as the
 * A/B markers flip.
 */
internal class PlayerScreenControlArms(
    val onSubtitleToggle: () -> Unit,
    val onSubtitleDelayClick: () -> Unit,
    val onAbRepeatToggle: () -> Unit,
    val onAbRepeatSetA: () -> Unit,
    val onAbRepeatSetB: () -> Unit,
    val onAbRepeatClear: () -> Unit,
    val onToggleAudioOnly: () -> Unit,
    val onMarkWatchedAndSkip: () -> Unit,
    val onMarkUnwatchedAndQuit: () -> Unit,
    val onRenderClick: () -> Unit,
    val onDeinterlaceCycle: () -> Unit,
    val onVersionClick: () -> Unit,
    val onQueueClick: () -> Unit,
    val onControlRowScrolled: () -> Unit,
)

@Composable
internal fun rememberPlayerScreenControlArms(
    viewModel: VideoPlayerViewModel,
    controls: ControlsSessionSlice,
    openSheet: (PlayerSheet) -> Unit,
    /** The screen-state closure the delay-overlay arm writes through. */
    setShowDelayOverlay: () -> Unit,
    /** The control-row interaction arm (the saveable interaction counter + the event). */
    onControlRowInteraction: () -> Unit,
): PlayerScreenControlArms = remember {
    PlayerScreenControlArms(
        onSubtitleToggle = { viewModel.onEvent(VideoPlayerUiEvent.ToggleSubtitles) },
        onSubtitleDelayClick = setShowDelayOverlay,
        onAbRepeatToggle = { controls.setAbRepeatEnabled(!controls.abRepeatState.value.enabled) },
        onAbRepeatSetA = { controls.setAbRepeatPointA() },
        onAbRepeatSetB = { controls.setAbRepeatPointB() },
        onAbRepeatClear = { controls.clearAbRepeat() },
        onToggleAudioOnly = { viewModel.onEvent(VideoPlayerUiEvent.ToggleAudioOnly) },
        onMarkWatchedAndSkip = { viewModel.onEvent(VideoPlayerUiEvent.MarkWatchedAndSkip) },
        onMarkUnwatchedAndQuit = { viewModel.onEvent(VideoPlayerUiEvent.MarkUnwatchedAndQuit) },
        onRenderClick = { openSheet(RenderSheetModule.sheet) },
        onDeinterlaceCycle = { viewModel.onEvent(VideoPlayerUiEvent.CycleDeinterlace) },
        onVersionClick = { openSheet(PlayerSheet.Version) },
        onQueueClick = { openSheet(PlayerSheet.Queue) },
        onControlRowScrolled = onControlRowInteraction,
    )
}
