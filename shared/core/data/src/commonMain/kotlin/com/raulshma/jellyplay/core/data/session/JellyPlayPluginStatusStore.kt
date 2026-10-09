package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ONE owner of "is the jellyfin-plugin-jellyplay companion plugin
 * available on the active server?" — the [PlaybackReportingStatusStore]
 * pattern re-instantiated for the plugin contract: one capabilities probe,
 * one [StateFlow] pair, session-reset on identity change.
 *
 * Semantics:
 *  - initial [JellyPlayPluginStatus.UNKNOWN];
 *  - [refresh] publishes AVAILABLE + the capabilities payload on success,
 *    UNAVAILABLE on any failure — a 404 (plugin absent) and a network error
 *    both gate features off for this session; callers re-probe on the next
 *    explicit refresh (settings screen) or the next identity transition;
 *  - consumers gate on `status.value == AVAILABLE` and on [features] —
 *    never on per-endpoint 404s.
 *
 * Contract-version rule: a payload whose contractVersion falls outside the
 * versions this client understands is treated as UNAVAILABLE — the client
 * refuses contracts it does not understand (the plugin's docs/CONTRACT.md
 * versioning rule, client side).
 */
class JellyPlayPluginStatusStore(
    private val apiClient: JellyPlayCapabilitiesRoutes,
    private val sessionCacheRegistry: SessionCacheRegistry,
) {

    private val _status = MutableStateFlow(JellyPlayPluginStatus.UNKNOWN)

    /** Plugin availability as of the last [refresh]. */
    val status: StateFlow<JellyPlayPluginStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow<JellyPlayCapabilities?>(null)

    /** The capabilities payload of the last successful refresh (null before that). */
    val capabilities: StateFlow<JellyPlayCapabilities?> = _capabilities.asStateFlow()

    private val _features = MutableStateFlow<Set<String>>(emptySet())

    /** The live feature set (empty while unavailable or before the first probe). */
    val features: StateFlow<Set<String>> = _features.asStateFlow()

    init {
        sessionCacheRegistry.registerAction(OWNER) {
            _status.value = JellyPlayPluginStatus.UNKNOWN
            _capabilities.value = null
            _features.value = emptySet()
        }
    }

    /** One-shot convenience gate over [features]; reactive consumers collect [features] instead. */
    fun hasFeature(feature: String): Boolean = _features.value.contains(feature)

    /**
     * The imperative probe ladder every plugin-backed entry point shares: true
     * when the last probe said AVAILABLE; otherwise one re-probe (a plugin
     * installed mid-session lights up without a re-auth) and the re-probe's
     * verdict. Callers pair this with [hasFeature] — never per-endpoint 404s
     * (ADR 0010 §1). The home row gates are the one deliberate divergence:
     * they re-probe only from UNKNOWN, because home refreshes far more often
     * than a session controller starts.
     */
    suspend fun ensureAvailable(): Boolean {
        if (_status.value == JellyPlayPluginStatus.AVAILABLE) return true
        refresh()
        return _status.value == JellyPlayPluginStatus.AVAILABLE
    }

    suspend fun refresh() {
        val caps = apiClient.getCapabilities().getOrNull()
        if (caps != null && caps.contractVersion in KNOWN_CONTRACT_VERSIONS) {
            _capabilities.value = caps
            _features.value = caps.features.toSet()
            _status.value = JellyPlayPluginStatus.AVAILABLE
        } else {
            _capabilities.value = null
            _features.value = emptySet()
            _status.value = JellyPlayPluginStatus.UNAVAILABLE
        }
    }

    private companion object {
        const val OWNER = "jellyplay-plugin-status"

        /** Contract majors this client understands (docs/CONTRACT.md). */
        val KNOWN_CONTRACT_VERSIONS = 1..1
    }
}
