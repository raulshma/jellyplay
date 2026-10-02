package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepCountdownClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One sleep-timer arm: a countdown in whole minutes, or the chapter
 * boundary. (5 / 15 / 30 / 60 — mirrored off the audio player's option
 * shape but reader-scaled; books read slower than episodes listen.)
 */
internal sealed interface ReaderSleepOption {
    data class Timed(val minutes: Int) : ReaderSleepOption

    data object EndOfChapter : ReaderSleepOption
}

/**
 * The sleep timer's live slice: `option`/`remainingMillis` are non-null only
 * while [running] (EndOfChapter carries no countdown — the chapter label
 * display stands in). Reset to the idle default on fire and on cancel.
 */
internal data class ReaderSleepTimerState(
    val option: ReaderSleepOption? = null,
    val remainingMillis: Long? = null,
    val running: Boolean = false,
)

/**
 * Countdown / chapter-boundary sleep timer for the reader. The timed
 * countdown rides core:data's [SleepCountdown] core (the fold home of the
 * audio/video players' former managers — this class previously mirrored the
 * audio shape with its own 1 s `delay` loop) with the
 * [SleepCountdownClock.perTick] step clock: remaining advances exactly one
 * tick per loop wake, so the countdown still rides pure `delay` — coroutine
 * virtual time drives it and the tests' `advanceTimeBy` pins keep passing
 * (the former ticker's contract, now via the injected tick source). The core
 * ticks at [TICK_MILLIS] and fades nothing (`fadeOutDurationMs = 0`); its
 * expiry resets the reader slice and invokes [onFired] exactly once.
 *
 * END_OF_CHAPTER arms hold no ticker and fire from [onChapterLabel], which
 * the ViewModel feeds every `relocated` chapter label. Arming snapshot: the
 * label seen at start (or the first label to arrive if none was known yet) is
 * the ARMED chapter — only a genuinely different label afterwards fires;
 * repeated relocations inside one chapter (page turns) re-report the same
 * label and never fire.
 *
 * A fresh [start] while running restarts (cancel + arm), matching the audio
 * player's sleep-timer semantics. Compose-free by controller convention.
 */
internal class ReaderSleepTimer(
    scope: CoroutineScope,
    private val onFired: () -> Unit,
) {

    /** The countdown core — 1 s step clock, no fade ramp (reader option shape is whole minutes). */
    private val countdown = SleepCountdown(
        clock = SleepCountdownClock.perTick(TICK_MILLIS),
        scope = scope,
        tickMillis = TICK_MILLIS,
    )

    private val _state = MutableStateFlow(ReaderSleepTimerState())
    val state: StateFlow<ReaderSleepTimerState> = _state.asStateFlow()

    /** The chapter label the END_OF_CHAPTER arm sits on (null until one arrives). */
    private var armedChapterLabel: String? = null

    /** The last label [onChapterLabel] saw — the arm source when no location exists yet. */
    private var lastChapterLabel: String? = null

    init {
        // Mirror the core countdown into the reader slice while a timed arm
        // runs (the guard drops the reset emission that rides the core's own
        // expiry — fire() below owns that write). Expiry: the core invokes
        // fire() exactly once, after resetting itself.
        scope.launch {
            countdown.sleepTimerRemainingMs.collect { remaining ->
                val current = _state.value
                if (current.running && current.option is ReaderSleepOption.Timed) {
                    _state.value = current.copy(remainingMillis = remaining)
                }
            }
        }
        countdown.setOnTimerExpired { fire() }
    }

    fun start(option: ReaderSleepOption) {
        when (option) {
            is ReaderSleepOption.Timed -> {
                val durationMillis = option.minutes * MILLIS_PER_MINUTE
                countdown.startSleepTimer(durationMs = durationMillis, fadeOutDurationMs = 0L)
                _state.value = ReaderSleepTimerState(
                    option = option,
                    remainingMillis = durationMillis,
                    running = true,
                )
            }
            ReaderSleepOption.EndOfChapter -> {
                countdown.cancelSleepTimer()
                armedChapterLabel = lastChapterLabel
                _state.value = ReaderSleepTimerState(option = option, running = true)
            }
        }
    }

    fun cancel() {
        countdown.cancelSleepTimer()
        armedChapterLabel = null
        _state.value = ReaderSleepTimerState()
    }

    /** The VM feeds every relocation's chapter label; drives the END_OF_CHAPTER arm. */
    fun onChapterLabel(label: String) {
        if (label.isBlank()) return
        lastChapterLabel = label
        val current = _state.value
        if (!current.running || current.option != ReaderSleepOption.EndOfChapter) return
        val armed = armedChapterLabel
        if (armed == null) {
            // Armed before any relocation: this first label becomes the armed
            // chapter (firing on it would end the timer on the spot).
            armedChapterLabel = label
            return
        }
        if (label != armed) fire()
    }

    private fun fire() {
        armedChapterLabel = null
        _state.value = ReaderSleepTimerState()
        onFired()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
