package com.raulshma.jellyplay.feature.player.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the "Still watching?" confirm overlay's prompt lifecycle (feature 1.3)
 * — the cluster extracted from [VideoPlayerViewModel] (the
 * [EpisodeContinuationController] shape): the prompt StateFlow and the
 * show / continue / stop / tick choreography, moved verbatim.
 *
 * The pure decisions stay beside it: [StillWatchingGate] (when the overlay
 * should be raised — the VM's end-of-playback episode arm and the session's
 * hours arm consult it) and [StillWatchingPromptState] (the countdown fold).
 * This controller is the stateful half — callers decide WHEN and call
 * [show]; it owns what Continue/Stop/expiry then DO, over narrow constructor
 * lambdas (no [VideoPlayerUiState] handle — the god-count ratchet is
 * unmoved). Pinned by [StillWatchingControllerTest].
 */
internal class StillWatchingController(
    /**
     * The up-next countdown duration — the prompt's auto-dismiss budget
     * (the VM's `autoplay.autoPlayCountdownSec` mirror).
     */
    private val getCountdownSeconds: () -> Int,
    /** The user proved present — resets the unattended streak. */
    private val onUserInteraction: () -> Unit,
    /** The episode arm's Continue action: advance to the next episode. */
    private val playNextEpisode: () -> Unit,
    /** The hours arm's Continue action: resume the (session-paused) engine. */
    private val resumePlayback: () -> Unit,
    /** Stop's engine half: pause the active engine. */
    private val pauseEngine: () -> Unit,
    /**
     * Stop's autoplay half: the Up Next overlay's cancel funnel — the
     * decision clock AND its uiState mirror flip together.
     */
    private val cancelAutoplay: () -> Unit,
) {

    private val _prompt = MutableStateFlow<StillWatchingPromptState?>(null)

    /** The overlay's state; `null` = hidden. The screen collects this at the overlay tier. */
    val prompt: StateFlow<StillWatchingPromptState?> = _prompt.asStateFlow()

    /** Raises the confirm overlay with the up-next countdown as its auto-dismiss budget. */
    fun show(reason: StillWatchingReason) {
        _prompt.value = StillWatchingPromptState.forReason(
            reason = reason,
            countdownSeconds = getCountdownSeconds(),
        )
    }

    /**
     * "Still watching?" → Continue: the counter resets and the requested arm
     * proceeds — the episode arm advances to the next episode, the hours arm
     * resumes the (session-paused) engine. A missing reason (defensive) rides
     * the hours arm's resume, matching the pre-extraction body.
     */
    fun onContinue() {
        val reason = _prompt.value?.reason
        _prompt.value = null
        onUserInteraction()
        when (reason) {
            StillWatchingReason.EPISODE_COUNT -> playNextEpisode()
            StillWatchingReason.HOURS_IDLE, null -> resumePlayback()
        }
    }

    /**
     * "Still watching?" → Stop (and the overlay's expiry route): pause and
     * cancel autoplay — no answer means stop autoplaying.
     */
    fun onStop() {
        _prompt.value = null
        pauseEngine()
        cancelAutoplay()
    }

    /** The overlay's once-per-second auto-dismiss tick; expiry is a Stop. */
    fun onTick() {
        val current = _prompt.value ?: return
        val next = current.tick()
        if (next == null) {
            onStop()
        } else {
            _prompt.value = next
        }
    }

    /**
     * Defensive clear on an item switch — a prompt can never survive into
     * the next item (its own Continue/Stop arms clear it first; this catches
     * a racing load). The VM's `resetForNewItem` hook pokes this.
     */
    fun resetForItem() {
        _prompt.value = null
    }
}
