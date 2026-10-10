package com.raulshma.jellyplay.feature.admin.transcodes

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.isAvailableOrProbe
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode
import com.raulshma.jellyplay.core.network.api.JellyPlayTranscodesRoutes
import com.raulshma.jellyplay.core.ui.viewmodel.ConfirmationHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * The admin transcodes monitor's gate — plugin AVAILABLE **and** the
 * `transcodes` feature key present **and** the user's per-feature toggle on
 * (ADR 0010: the capability registry is the ONLY gating mechanism; the toggle
 * rides [JellyPlayFeatureGate], the ONE per-feature seam). Admin-ness is
 * enforced upstream by the admin area's AdminRouteContainer — the existing
 * admin gate, deliberately not re-built here.
 */
enum class TranscodesGate { Unknown, Available, Unavailable }

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

@Immutable
data class TranscodesState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val gate: TranscodesGate = TranscodesGate.Unknown,
    val rows: List<TranscodeRow> = emptyList(),
    /** True while a cancel request is in flight — the dialog's confirmLoading + guard. */
    val isCancelling: Boolean = false,
)

/**
 * Backs the admin transcodes monitor (Route.JellyPlayTranscodes): the live
 * list of the companion plugin's active transcodes with a cancel action.
 *
 * Gating — admin && plugin AVAILABLE && `transcodes` feature. The admin half
 * is the admin area's existing AdminRouteContainer gate (this screen is only
 * reachable through it); the plugin half flows from [JellyPlayPluginStatusStore]
 * per ADR 0010 ("the capability registry is the ONLY gating mechanism"): no
 * transcode fetch is ever issued while the gate is off, so a stock server's
 * plugin-route 404 is never observed here.
 *
 * Refresh — a manual refresh plus a 5s auto-refresh loop owned by the screen's
 * visibility (the settings screen's startSessionAutoRefresh/stop pattern):
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
     * Cancel-confirmation host (DevicesViewModel's deleteConfirmation shape).
     * Settle arm: clears on BOTH outcomes — the dialog always closes on cancel
     * and the reload stays unconditional. [TranscodesState.isCancelling] is the
     * caller-owned in-flight fact it reads.
     */
    val cancelConfirmation = ConfirmationHost<TranscodeRow>()

    /** Whether the initial gated load has been kicked off (exactly once per gate-on). */
    private var initialLoadStarted = false

    private var autoRefreshJob: Job? = null

    init {
        observeGate()
    }

    /**
     * Starts the 5s auto-refresh loop. Idempotent; safe to call on every entry
     * into START. Each iteration fetches only while the gate is on — a stock
     * server (or a missing feature key) never produces a plugin-route request.
     */
    fun start() {
        refreshPluginStatus()
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                if (_uiState.value.gate == TranscodesGate.Available) {
                    fetchTranscodes()
                }
            }
        }
    }

    /** Stops the auto-refresh loop started by [start]. */
    fun stop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /** Manual refresh (scaffold action): silent on failure — the stale list stays; success clears a shown error. */
    fun refresh() {
        refreshPluginStatus()
        if (_uiState.value.gate != TranscodesGate.Available) return
        launch {
            _uiState.update { it.copy(isRefreshing = true) }
            pluginApiClient.getActiveTranscodes()
                .onSuccess { rows ->
                    _uiState.update {
                        it.copy(rows = rows.map(::toRow), isRefreshing = false, error = null)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
        }
    }

    /**
     * One capabilities probe — the store never re-probes on its own
     * (UNKNOWN → one refresh; AVAILABLE/UNAVAILABLE stay), the identity reset
     * in the store re-arms the next visit. Same discipline as the settings
     * screen's sync section.
     */
    private fun refreshPluginStatus() {
        val store = statusStore ?: return
        launch {
            if (store.status.value == JellyPlayPluginStatus.UNKNOWN) {
                store.refresh()
            }
        }
    }

    /**
     * The gate collector. The first transcode load rides the Available
     * transition (never before — a gated-off screen must not touch the
     * plugin route), so the screen opens on data rather than on a wasted 404.
     *
     * Availability is the ONE gate seam ([JellyPlayFeatureGate.isAvailable] —
     * probe AND the user's `transcodes` toggle, so a switch-off tears the
     * screen down reactively); without the seam the probe-only combine keeps
     * the pre-toggle behavior.
     */
    private fun observeGate() {
        val store = statusStore
        if (store == null) {
            // Direct-construction without the plugin seam: gated off, final.
            _uiState.update { it.copy(gate = TranscodesGate.Unavailable, isLoading = false) }
            return
        }
        val available: kotlinx.coroutines.flow.Flow<Boolean> =
            featureGate.isAvailableOrProbe(store, JellyPlayPluginFeatures.Transcodes)
        launch {
            combine(store.status, available) { status, gateOpen ->
                when {
                    gateOpen -> TranscodesGate.Available
                    status == JellyPlayPluginStatus.UNKNOWN -> TranscodesGate.Unknown
                    else -> TranscodesGate.Unavailable
                }
            }.collect { gate ->
                _uiState.update {
                    it.copy(
                        gate = gate,
                        isLoading = if (gate == TranscodesGate.Unknown) it.isLoading else false,
                    )
                }
                if (gate == TranscodesGate.Available && !initialLoadStarted) {
                    initialLoadStarted = true
                    loadTranscodes()
                }
            }
        }
    }

    /** The initial gated load — the only fetch whose failure surfaces an error. */
    private fun loadTranscodes() {
        launch {
            loadInto(
                start = { _uiState.update { it.copy(isLoading = true, error = null) } },
                fetch = { pluginApiClient.getActiveTranscodes() },
                onSuccess = { rows ->
                    _uiState.update {
                        it.copy(rows = rows.map(::toRow), isLoading = false)
                    }
                },
                onFailure = { e ->
                    Log.e("JellyPlayTranscodes", "Failed to fetch active transcodes", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                },
            )
        }
    }

    /**
     * Silent poll/reload fetch: a failure keeps the stale list (the
     * refresh-failure idiom), the next tick (or cancel settle) retries.
     */
    private suspend fun fetchTranscodes() {
        pluginApiClient.getActiveTranscodes()
            .onSuccess { rows ->
                _uiState.update { it.copy(rows = rows.map(::toRow), isLoading = false) }
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
            if (_uiState.value.gate == TranscodesGate.Available) {
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
