package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * Countdown / chapter-boundary sleep timer for the reader. Timed arms tick
 * a 1 s `delay` loop on [scope] (NOT wall-clock subtraction — the loop must
 * ride virtual time so tests advance it); END_OF_CHAPTER arms hold no
 * ticker and fire from [onChapterLabel], which the ViewModel feeds every
 * `relocated` chapter label. Arming snapshot: the label seen at start (or
 * the first label to arrive if none was known yet) is the ARMED chapter —
 * only a genuinely different label afterwards fires; repeated relocations
 * inside one chapter (page turns) re-report the same label and never fire.
 *
 * On fire the timer resets itself and invokes [onFired] exactly once; a
 * fresh [start] while running restarts (cancel + arm), matching the audio
 * player's sleep-timer semantics. Compose-free by controller convention.
 */
internal class ReaderSleepTimer(
    private val scope: CoroutineScope,
    private val onFired: () -> Unit,
) {

    private val _state = MutableStateFlow(ReaderSleepTimerState())
    val state: StateFlow<ReaderSleepTimerState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** The chapter label the END_OF_CHAPTER arm sits on (null until one arrives). */
    private var armedChapterLabel: String? = null

    /** The last label [onChapterLabel] saw — the arm source when no location exists yet. */
    private var lastChapterLabel: String? = null

    fun start(option: ReaderSleepOption) {
        cancelTicker()
        when (option) {
            is ReaderSleepOption.Timed -> {
                val durationMillis = option.minutes * MILLIS_PER_MINUTE
                _state.value = ReaderSleepTimerState(
                    option = option,
                    remainingMillis = durationMillis,
                    running = true,
                )
                ticker = scope.launch {
                    var remaining = durationMillis
                    while (remaining > 0L) {
                        delay(TICK_MILLIS)
                        remaining = (remaining - TICK_MILLIS).coerceAtLeast(0L)
                        _state.value = _state.value.copy(remainingMillis = remaining)
                    }
                    fire()
                }
            }
            ReaderSleepOption.EndOfChapter -> {
                armedChapterLabel = lastChapterLabel
                _state.value = ReaderSleepTimerState(option = option, running = true)
            }
        }
    }

    fun cancel() {
        cancelTicker()
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
        cancelTicker()
        armedChapterLabel = null
        _state.value = ReaderSleepTimerState()
        onFired()
    }

    private fun cancelTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
