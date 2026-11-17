package com.raulshma.jellyplay.feature.admin.transcodes

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.PollSpec
import com.raulshma.jellyplay.core.concurrency.RefreshCycle
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.JellyPlayCapabilityGate
import com.raulshma.jellyplay.core.data.session.JellyPlayCapabilityState
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode
import com.raulshma.jellyplay.core.network.api.JellyPlayTranscodesRoutes
import com.raulshma.jellyplay.core.ui.viewmodel.ConfirmationHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.flow.StateFlow

/**
 * The admin transcodes monitor's gate — plugin AVAILABLE **and** the
 * `transcodes` feature key present **and** the user's per-feature toggle on
 * (the capability registry is the ONLY gating mechanism; the shared
 * [JellyPlayCapabilityGate] verdict, the toggle riding [JellyPlayFeatureGate],
 * the ONE per-feature seam). Admin-ness is enforced upstream by the admin
 * area's AdminRouteContainer — the existing admin gate.
 */

/** UI resolution of a session's `playMethod` wire string. */
enum class TranscodePlayMethod { DIRECT, TRANSCODE, OTHER }

@Immutable
data class TranscodeRow(
    val sessionId: String,
    val userName: String,
    val deviceName: String,
    val itemName: String,
    val isTranscoding: Boolean,
    val playMethod: TranscodePlayMethod,
    /** The raw `playMethod` wire string, for OTHER fallback rendering. */
    val playMethodRaw: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    /** Human-formatted [JellyPlayActiveTranscode.videoBitrate] (bps), or null. */
    val bitrateLabel: String?,
    /**
     * Why the server is re-encoding, named flag-bit strings as the plugin
     * decomposed them — shown while [isTranscoding], empty otherwise.
     */
    val transcodeReasons: List<String>,
    val isPaused: Boolean,
)

/**
 * The transcodes monitor's load phase — the sealed machine that replaced the
 * former `isLoading` / `isRefreshing` / `error` flag trio plus the separate
 * nullable-state `rows` list (convention §1): those three flags could
 * co-derive unreachable combinations (`isLoading` beside a shown `error`, an
 * `isRefreshing` no render ever read) and the row list's nullabilityencoding
 * ("no rows yet" vs "poll answered empty") rode the same soup. The rows now
 * live ON the phase ([Loaded] / [Refreshing]), so a failed face can no longer
 * coexist with a visible list.
 */
@Immutable
sealed interface TranscodesLoadPhase {
    /**
     * The gate is unresolved ([JellyPlayCapabilityState.Unknown], pre-probe) or
     * the gate-on initial load is in flight — the full-screen spinner. While
     * the gate is OFF the screen renders the gate face and this phase is
     * unobserved (no load ever ran, so it stays here).
     */
    data object Loading : TranscodesLoadPhase

    /**
     * A manual refresh is in flight — the pre-refresh [staleRows] stay visible
     * underneath (the refresh-failure idiom: a failed refresh keeps the stale
     * list). Raised ONLY from [Loaded]; a retry from [Failed] keeps the error
     * face until the fetch settles, matching the former behavior where
     * `isRefreshing` never displaced a shown error.
     */
    data class Refreshing(val staleRows: List<TranscodeRow>) : TranscodesLoadPhase

    /**
     * The last fetch settled — the gate-on initial load, a poll tick, a
     * manual refresh or a cancel-reload. A poll/refresh success resolving a
     * previously shown [Failed] face into content is the phase's honest
     * behavior: the former boolean soup left the stale `error` flag stranded
     * under fresh rows (the screen kept showing the error face), a
     * combination this machine cannot encode.
     */
    data class Loaded(val rows: List<TranscodeRow>) : TranscodesLoadPhase

    /** The gate-on initial load failed — the only surfaced error; refresh/poll retries silently. */
    data class Failed(val message: String?) : TranscodesLoadPhase
}

@Immutable
data class TranscodesState(
    val gate: JellyPlayCapabilityState = JellyPlayCapabilityState.Unknown,
    val loadPhase: TranscodesLoadPhase = TranscodesLoadPhase.Loading,
    /**
     * True while a cancel request is in flight — the dialog's confirmLoading +
     * guard. Genuinely two-state (one request's in-flight fact, read by the
     * [ConfirmationHost] settle rule), not a lifecycle — deliberately a
     * boolean, not a phase arm.
     */
    val isCancelling: Boolean = false,
)

/**
 * Backs the admin transcodes monitor (Route.JellyPlayTranscodes): the live
 * list of the companion plugin's active transcodes with a cancel action.
 *
 * Gating — admin && plugin AVAILABLE && `transcodes` feature. The admin half
 * is the admin area's existing AdminRouteContainer gate (this screen is only
 * reachable through it); the plugin half is the shared [JellyPlayCapabilityGate]
 * reading [JellyPlayPluginStatusStore] ("the capability registry
 * is the ONLY gating mechanism"): no transcode fetch is ever issued while the
 * gate is off, so a stock server's plugin-route 404 is never observed here.
 *
 * Refresh — a manual refresh plus a 5s auto-refresh cycle owned by the
 * screen's visibility (the refresh cycle module's registration):
 * `start()` from the screen's START, `stop()` on stop/dispose. Initial data
 * loads once the gate resolves; loop iterations are silent (a failed poll
 * keeps the stale list, mirroring DevicesViewModel's refresh), only the
 * initial load surfaces an error.
 *
 * The nullable-with-default [statusStore] mirrors SettingsViewModel's
 * plugin-seam ctor deps: the Koin factory passes the real single, direct
 * construction (tests) may omit it — a null store is simply gated off.
 */
class JellyPlayTranscodesViewModel(
    private val pluginApiClient: JellyPlayTranscodesRoutes,
    private val statusStore: JellyPlayPluginStatusStore? = null,
    /**
     * The per-feature gate seam (probe AND the user's toggle) over the store
     * above. Nullable-with-default (the SettingsViewModel pattern); without
     * it the probe-only availability keeps the pre-toggle behavior.
     */
    private val featureGate: JellyPlayFeatureGate? = null,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(TranscodesState())
    val uiState: StateFlow<TranscodesState> = _uiState.flow

    /**
     * The shared capability gate (the plugin capability gate module) — the
     * probe-once-while-UNKNOWN / re-arm-on-identity-reset / gate-reads-the-
     * store discipline. Null with the store:
     * direct construction (tests) without the plugin seam is gated off, final.
     */
    private val capabilityGate = statusStore?.let { JellyPlayCapabilityGate(it, scope) }

    /**
     * Cancel-confirmation host (DevicesViewModel's deleteConfirmation shape).
     * Settle arm: clears on BOTH outcomes — the dialog always closes on cancel
     * and the reload stays unconditional. [TranscodesState.isCancelling] is the
     * caller-owned in-flight fact it reads.
     */
    val cancelConfirmation = ConfirmationHost<TranscodeRow>()

    /**
     * Whether the initial gated load has been kicked off for the CURRENT
     * gate-on stretch (exactly once per gate-on). The gate collector CLEARS
     * this whenever the gate leaves Available — an identity reset / plugin
     * reinstall re-arms the store's probe, the next gate-on is a fresh
     * stretch, and the monitor must reload rather than show the first
     * visit's rows forever.
     */
    private var initialLoadStarted = false

    /**
     * The 5s gate-predicated auto-refresh cycle (the refresh cycle module),
     * registered once. Each iteration fetches only while the gate is on
     * ([RefreshCycle] gate round): a stock server (or a missing feature key)
     * never produces a plugin-route request. The screen binds the cycle to
     * visibility via [start]/[stop].
     */
    private val autoRefreshCycle = RefreshCycle(
        scope = scope,
        poll = PollSpec(intervalMs = REFRESH_INTERVAL_MS),
        gate = { _uiState.value.gate == JellyPlayCapabilityState.Available },
        fetch = { fetchTranscodes() },
    )

    init {
        observeGate()
    }

    /**
     * Screen-visibility hook: the gate's arm (one capabilities probe while
     * the store is still UNKNOWN — the identity reset in the store re-arms
     * the next visit), then the auto-refresh cycle. Safe to call on every
     * entry into START — a repeated call replaces the running loop
     * ([RefreshCycle.start]).
     */
    fun start() {
        capabilityGate?.arm()
        autoRefreshCycle.start()
    }

    /** Stops the auto-refresh cycle started by [start]. */
    fun stop() {
        autoRefreshCycle.stop()
    }

    /**
     * Manual refresh (scaffold action): silent on failure — the stale list
     * stays; success clears a shown error (settles the phase to
     * [TranscodesLoadPhase.Loaded]). Raised only from [TranscodesLoadPhase.Loaded]
     * as [TranscodesLoadPhase.Refreshing]; a retry from
     * [TranscodesLoadPhase.Failed] keeps the error face until the fetch settles.
     */
    fun refresh() {
        capabilityGate?.arm()
        if (_uiState.value.gate != JellyPlayCapabilityState.Available) return
        launch {
            val priorPhase = _uiState.value.loadPhase
            if (priorPhase is TranscodesLoadPhase.Loaded) {
                _uiState.update { it.copy(loadPhase = TranscodesLoadPhase.Refreshing(priorPhase.rows)) }
            }
            pluginApiClient.getActiveTranscodes()
                .onSuccess { rows ->
                    _uiState.update { it.copy(loadPhase = TranscodesLoadPhase.Loaded(rows.map(::toRow))) }
                }
                .onFailure {
                    // The refresh-failure idiom: restore the pre-refresh face —
                    // unless a concurrent writer already moved the phase past
                    // Refreshing on its own.
                    _uiState.update { s ->
                        if (s.loadPhase is TranscodesLoadPhase.Refreshing) s.copy(loadPhase = priorPhase) else s
                    }
                }
        }
    }

    /**
     * The gate collector — rides the shared capability gate's verdict. The
     * first transcode load rides the Available transition (never before — a
     * gated-off screen must not touch the plugin route), so the screen opens
     * on data rather than on a wasted 404 — and the load latch RE-ARMS
     * whenever the gate leaves Available, so a gate drop + re-gate-on
     * (identity reset / plugin reinstall) reloads.
     */
    private fun observeGate() {
        val gateFlow = capabilityGate?.state(JellyPlayPluginFeatures.Transcodes, featureGate)
        if (gateFlow == null) {
            // Direct-construction without the plugin seam: gated off, final.
            // The phase is unobserved here (the screen renders the gate face).
            _uiState.update { it.copy(gate = JellyPlayCapabilityState.Unavailable) }
            return
        }
        launch {
            gateFlow.collect { gate ->
                _uiState.update { it.copy(gate = gate) }
                if (gate == JellyPlayCapabilityState.Available) {
                    if (!initialLoadStarted) {
                        initialLoadStarted = true
                        loadTranscodes()
                    }
                } else {
                    // Gate dropped (Unknown after an identity reset, or
                    // Unavailable): re-arm the latch — the next Available
                    // transition is a fresh stretch and must reload.
                    initialLoadStarted = false
                }
            }
        }
    }

    /** The initial gated load — the only fetch whose failure surfaces an error. */
    private fun loadTranscodes() {
        launch {
            loadInto(
                start = { _uiState.update { it.copy(loadPhase = TranscodesLoadPhase.Loading) } },
                fetch = { pluginApiClient.getActiveTranscodes() },
                onSuccess = { rows ->
                    _uiState.update { it.copy(loadPhase = TranscodesLoadPhase.Loaded(rows.map(::toRow))) }
                },
                onFailure = { e ->
                    Log.e("JellyPlayTranscodes", "Failed to fetch active transcodes", e)
                    _uiState.update { it.copy(loadPhase = TranscodesLoadPhase.Failed(e.message)) }
                },
            )
        }
    }

    /**
     * Silent poll/reload fetch: a failure keeps the stale list (the
     * refresh-failure idiom — the phase is untouched), the next tick (or
     * cancel settle) retries; a success settles [TranscodesLoadPhase.Loaded].
     */
    private suspend fun fetchTranscodes() {
        pluginApiClient.getActiveTranscodes()
            .onSuccess { rows ->
                _uiState.update { it.copy(loadPhase = TranscodesLoadPhase.Loaded(rows.map(::toRow))) }
            }
            .onFailure { e ->
                Log.w("JellyPlayTranscodes", "Transcode fetch failed", e)
            }
    }

    // ── cancel flow ──

    /** Opens the cancel-confirm dialog for [row]. */
    fun showCancelDialog(row: TranscodeRow) = cancelConfirmation.show(row)

    /**
     * Refused while a cancel is in flight (host rule; [TranscodesState.isCancelling]
     * is the caller-owned fact) — the dialog stays open until the request settles.
     */
    fun dismissCancelDialog() = cancelConfirmation.dismiss(inFlight = _uiState.value.isCancelling)

    /**
     * Cancels the pending session. Settle arm: clears on BOTH outcomes — the
     * dialog always closed on cancel and the reload stayed unconditional
     * (DevicesViewModel's deleteDevice shape). A failed cancel re-shows the
     * session within one poll tick, which is the honest feedback.
     */
    fun cancelTranscode() {
        val row = cancelConfirmation.confirm(inFlight = _uiState.value.isCancelling) ?: return
        launch {
            _uiState.update { it.copy(isCancelling = true) }
            pluginApiClient.cancelTranscode(row.sessionId)
            _uiState.update { it.copy(isCancelling = false) }
            cancelConfirmation.clear()
            if (_uiState.value.gate == JellyPlayCapabilityState.Available) {
                fetchTranscodes()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    companion object {
        /** Auto-refresh cadence while the screen is visible. */
        internal const val REFRESH_INTERVAL_MS = 5_000L
    }
}

/**
 * Maps the plugin's wire transcode onto the screen row. `playMethod` is the
 * plugin's free string: any "direct" spelling resolves to DIRECT, any
 * "transcode" spelling to TRANSCODE, anything else (including absent) renders
 * as OTHER/raw. Codecs and bitrate ride along for every row — the screen shows
 * codecs only while [TranscodeRow.isTranscoding], per the contract that a
 * direct play has nothing to re-encode.
 */
internal fun toRow(transcode: JellyPlayActiveTranscode): TranscodeRow {
    val method = transcode.playMethod?.trim().orEmpty()
    val isTranscoding = method.contains("transcode", ignoreCase = true)
    val playMethod = when {
        method.contains("direct", ignoreCase = true) -> TranscodePlayMethod.DIRECT
        isTranscoding -> TranscodePlayMethod.TRANSCODE
        else -> TranscodePlayMethod.OTHER
    }
    return TranscodeRow(
        sessionId = transcode.sessionId,
        userName = transcode.userName.orEmpty(),
        deviceName = transcode.deviceName.orEmpty(),
        itemName = transcode.itemName.orEmpty(),
        isTranscoding = isTranscoding,
        playMethod = playMethod,
        playMethodRaw = transcode.playMethod?.trim()?.ifEmpty { null },
        videoCodec = transcode.videoCodec,
        audioCodec = transcode.audioCodec,
        bitrateLabel = transcode.videoBitrate?.let(::formatBitrate),
        transcodeReasons = transcode.transcodeReasons.orEmpty(),
        isPaused = transcode.isPaused,
    )
}

/**
 * Formats a bits-per-second bitrate for the row's subtitle — pure integer
 * math so the label is locale-independent and test-deterministic:
 * 8_000_000 → "8 Mbps", 4_500_000 → "4.5 Mbps", 800_000 → "800 kbps".
 */
internal fun formatBitrate(bitsPerSecond: Int): String {
    val value = bitsPerSecond.coerceAtLeast(0)
    return if (value >= 1_000_000) {
        // Half-a-tenth rounding: 4_500_000 → 45 tenths → "4.5 Mbps".
        val tenths = (value + 50_000) / 100_000
        if (tenths % 10 == 0) {
            "${tenths / 10} Mbps"
        } else {
            "${tenths / 10}.${tenths % 10} Mbps"
        }
    } else {
        "${(value + 500) / 1_000} kbps"
    }
}
