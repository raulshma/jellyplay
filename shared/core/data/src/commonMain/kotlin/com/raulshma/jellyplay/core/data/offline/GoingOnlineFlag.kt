package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The going-online busy flag's entire choreography, so it has exactly one
 * owner beside the offline→online transition it decorates. The Home refresh
 * handshake and the app-shell ViewModels used to each keep a hand-synced
 * copy (own raise on the toggle, own clear path); every stuck-spinner bug
 * this class's guarantees encode came from those copies drifting.
 *
 * Guarantees:
 *  * [arm] raises the flag AND starts the fallback watchdog. Call it before
 *    kicking off the async going-online write, so the spinner precedes the
 *    mode flip. It raises unconditionally: both clears below are always
 *    reachable (the watchdog is an unconditional deadline, not a
 *    mode-guarded one), so an arm can never strand the flag — not even
 *    against an already-ONLINE flow.
 *  * Primary clear: the [offlineMode] flow emitting ONLINE — whichever way
 *    the transition resolves (the toggle's preference write landing, or an
 *    external/auto reconnect overtaking it). This also makes the flag immune
 *    to a hung post-toggle fetch: it clears at the emission, before any
 *    fetch starts, so the fetch's own deadline/watchdog need not save it.
 *  * Fallback clear: [timeoutMs] after [arm], if the flag is still up.
 *    The production case is the lost preference write: the toggle is a
 *    fire-and-forget write on the manager's own scope; if that write is
 *    lost, no ONLINE emission ever fires and this watchdog is the only
 *    clear left. The unconditional deadline is also what lets [arm] raise
 *    without checking the current mode — a mode-value guard (refuse when
 *    already ONLINE) would misread the pre-derivation cold-start window
 *    (both managers derive the persisted mode asynchronously over an
 *    ONLINE-initialized flow) and silently skip the spinner there.
 *  * Re-arming replaces the watchdog, so a second going-online attempt gets
 *    its own full window.
 *
 * Shared verbatim by the Android and desktop [OfflineModeManager]
 * implementations; the constructor timeout is a test seam only (production
 * always uses the default).
 */
class GoingOnlineFlag(
    /** The owning manager's scope — the collector and watchdog die with it. */
    private val scope: CoroutineScope,
    /** The mode flow whose ONLINE emission is the flag's primary clear. */
    private val offlineMode: StateFlow<OfflineMode>,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    private val _goingOnline = MutableStateFlow(false)
    val goingOnline: StateFlow<Boolean> = _goingOnline.asStateFlow()

    // Fallback timer for an arm whose preference write never lands — see
    // the class KDoc's fallback-clear guarantee.
    private var watchdogJob: Job? = null

    init {
        scope.launch {
            offlineMode.collect { if (it == OfflineMode.ONLINE) clear() }
        }
    }

    /**
     * The toggle's arm gate: arms the flag only when the store snapshot
     * says the initiating toggle actually goes online. Snapshot says
     * manual-offline → the toggle's direction is going online — arm BEFORE
     * the async preference write so the Go Online spinners start
     * immediately. Arming on the snapshot, not the caller's mode guess,
     * keeps an OFFLINE_AUTO toggle (which goes FURTHER offline) from
     * parking a spinner no emission will ever clear. The snapshot also
     * makes the arm safe against a mode flow still reading ONLINE: a
     * manual-offline snapshot proves a derived OFFLINE_MANUAL emission is
     * still due (pre-derivation default, or the toggle's own write landing
     * next), so the clears are reachable from the very first toggle.
     */
    fun armIfGoingOnline(snapshotSaysManualOffline: Boolean) {
        if (snapshotSaysManualOffline) arm()
    }

    /**
     * Raises the flag and (re-)arms the watchdog. Only [armIfGoingOnline]
     * calls this in production; tests use it to drive the choreography
     * directly.
     */
    internal fun arm() {
        _goingOnline.value = true
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(timeoutMs)
            // Unconditional deadline: the ONLINE-emission path owns the clear
            // whenever the mode landed (clear() also cancels this job), so a
            // flag still up here is a transition that never resolved — or an
            // arm against an already-ONLINE flow. Either way the spinner
            // must not outlive the window.
            if (_goingOnline.value) {
                _goingOnline.value = false
            }
        }
    }

    /**
     * Force-clears the flag and stands down the watchdog. The ONLINE-emission
     * collector is the production caller; keeping this private ensures no
     * outside writer can second-guess the single-owner choreography.
     */
    private fun clear() {
        _goingOnline.value = false
        watchdogJob?.cancel()
        watchdogJob = null
    }

    companion object {
        /**
         * Matches the deadline the Home reconnect handshake puts on its
         * post-toggle fetch: the user waits at most this long on a lost
         * going-online write before the spinner gives up.
         */
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
