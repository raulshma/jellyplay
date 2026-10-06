package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The live half of the settings-sync engine (ADR 0010 §4, the client-side
 * counterpart of the plugin contract's "Subscribe to `settings/stream`; on
 * `settings.changed` → re-pull"): keeps ONE settings SSE connection running
 * while the user's sync toggle is on, folding each `settings.changed` /
 * `settings.reset` frame into [ProfileSyncRepository.requestSync] — the
 * engine's own gates (opt-in, availability probe, mutex) stay authoritative;
 * this class only decides WHEN a cycle fires without user action.
 *
 * Lifecycle rides the AND of the auth edge and the enabled edge of
 * [ProfileSyncRepository.state] (reactively): enabling sync while signed in
 * opens the stream, disabling — or the identity reset on sign-out/user
 * switch, which re-arms `enabled = false` inside the repository — closes it.
 * No connection exists for a user who never opted in, and the device-scoped
 * toggle surviving sign-out never keeps a doomed connect loop alive.
 *
 * Reconnects ride the shared [SseReconnectLoop] — the [com.raulshma.jellyplay.core.network.WebSocketBackoffPolicy]
 * law over SSE: exponential (doubling) backoff capped at 60s, reset on a
 * connected frame, plus an availability + `settings-sync` registry re-probe
 * per attempt — a stock server never gets an SSE connect attempt, only a
 * probe per backoff tick, and a plugin installed mid-session lights the
 * stream up without a re-auth. Bursts of frames (a
 * batch push echoes one `changed` per device) collapse through the upstream
 * [debounce] into one follow-up cycle — the cycle itself runs OUTSIDE any
 * cancellable per-frame window, so a frame arriving mid-cycle cannot kill it
 * (collectLatest would); the echo cycle terminates (nothing dirty →
 * pull-only converge), so there is no loop.
 */
class JellyPlayLiveResyncConnector(
    private val apiClient: JellyPlayPluginApiClient,
    private val syncRepository: ProfileSyncRepository,
    private val statusStore: JellyPlayPluginStatusStore,
    /**
     * The SSE connect needs a session; the sync toggle is device-scoped and
     * survives sign-out, so the stream arms on the auth-true AND enabled
     * edge (the events controller's auth-edge rule, not a re-built gate).
     */
    private val authRepository: com.raulshma.jellyplay.core.data.repository.AuthRepository,
    private val scope: CoroutineScope,
) {

    /** The process-lifetime edge collector; owned by [start]. */
    private var lifecycleJob: Job? = null

    /** The CURRENT stream loop, owned by the enabled edge. */
    private var streamJob: Job? = null

    /** Arms the edge collector. Idempotent (the createdAtStart idiom). */
    fun start() {
        if (lifecycleJob?.isActive == true) return
        lifecycleJob = scope.launch {
            combine(
                syncRepository.state.map { it.enabled },
                authRepository.isAuthenticated,
            ) { enabled, authenticated -> enabled && authenticated }
                .distinctUntilChanged()
                .collect { live ->
                    if (live) startStream() else stopStream()
                }
        }
    }

    fun stop() {
        lifecycleJob?.cancel()
        lifecycleJob = null
        stopStream()
    }

    private fun startStream() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch { runStream() }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    private suspend fun runStream() {
        val reconnect = SseReconnectLoop()
        while (streamJob?.isActive == true) {
            try {
                // Probe before connect: an opted-in user on a stock server must
                // not open a doomed SSE attempt every tick — one probe per
                // backoff period instead, and the re-probe still lights a
                // mid-session plugin install up. The registry's `settings-sync`
                // key gates alongside the probe (the ONE gating mechanism,
                // ADR 0010 §6) — an admin-disabled sync wave closes the gate.
                if (!statusStore.ensureAvailable() ||
                    !statusStore.hasFeature(com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.SettingsSync)
                ) {
                    throw StreamGateClosed()
                }
                apiClient.settingsStream()
                    .filter { it.event == EVENT_CHANGED || it.event == EVENT_RESET }
                    .debounce(DEBOUNCE_MILLIS)
                    .collect {
                        reconnect.onConnected()
                        syncRepository.requestSync()
                    }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.d(TAG, "settings stream dropped: ${t.message}")
            }

            if (streamJob?.isActive != true) break
            reconnect.awaitRetryDelay()
        }
    }

    /** The probe-before-connect gate closing: no SSE attempt this tick. */
    private class StreamGateClosed : RuntimeException("plugin unavailable")

    private companion object {
        const val TAG = "JellyPlayLiveResync"
        const val EVENT_CHANGED = "settings.changed"
        const val EVENT_RESET = "settings.reset"

        /** Burst-collapse window before a frame becomes a re-sync cycle. */
        const val DEBOUNCE_MILLIS = 500L
    }
}
