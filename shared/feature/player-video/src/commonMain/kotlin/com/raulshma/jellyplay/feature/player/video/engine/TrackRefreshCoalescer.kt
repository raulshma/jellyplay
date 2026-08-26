package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Default debounce window for [TrackRefreshCoalescer]. Long enough to absorb the
 * ~50 ms burst of track-list property changes mpv emits when a subtitle/audio
 * track is selected (select + the `sid`/`aid`/`track-list` observers all fire
 * within that window), short enough that the track picker UI updates without
 * perceptible lag.
 */
internal const val TRACK_REFRESH_DEBOUNCE_MS = 80L

/**
 * Coalesces a rapid burst of track-refresh requests into a single deferred
 * [onRefresh] invocation.
 *
 * mpv emits a cascade of property changes whenever a track is selected: the
 * caller's own `select-*` refresh plus the `sid`/`aid`/`track-list` observers,
 * all within ~50 ms. Previously each request performed its own synchronous
 * `getPropertyNode("track-list")` JNI read on the main looper, and that burst
 * was a primary driver of the MPV playback ANR (main-thread death spiral →
 * skipped frames → input-dispatch timeout).
 *
 * Each [request] cancels any pending refresh and (re)launches a coroutine that
 * waits [debounceMs] before invoking [onRefresh]. So N rapid requests collapse
 * into one [onRefresh] call once the burst settles. The refresh work itself
 * (e.g. reading the mpv track list) is expected to run inside [onRefresh] on an
 * appropriate dispatcher — this helper only owns the coalescing/delay.
 *
 * Extracted as a standalone class (mirroring [EnginePositionTicker]) so the
 * coalescing contract is unit-testable with a virtual-clock [TestScope]
 * without standing up a full [MpvPlayerEngine].
 *
 * @param scopeProvider returns the coroutine scope the debounced refresh runs
 *                  on. Provided as a lookup (not a captured value) so an engine
 *                  that recreates its scope on load()/release() always launches
 *                  against the live scope. Cancellation of that scope (e.g. on
 *                  engine release) drops any pending refresh.
 * @param onRefresh invoked once after the burst settles. Runs on the provided
 *                  scope's dispatcher unless it switches internally.
 * @param debounceMs coalesce window; see [TRACK_REFRESH_DEBOUNCE_MS].
 */
internal class TrackRefreshCoalescer(
    private val scopeProvider: () -> CoroutineScope,
    private val onRefresh: () -> Unit,
    private val debounceMs: Long = TRACK_REFRESH_DEBOUNCE_MS,
) {
    private var pending: Job? = null

    /**
     * Request a refresh. Cancels any not-yet-fired refresh and (re)starts the
     * debounce timer, so only the most recent request in a burst takes effect.
     */
    fun request() {
        pending?.cancel()
        pending = scopeProvider().launch {
            delay(debounceMs)
            onRefresh()
        }
    }

    /** Cancel any pending refresh without firing it. */
    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
