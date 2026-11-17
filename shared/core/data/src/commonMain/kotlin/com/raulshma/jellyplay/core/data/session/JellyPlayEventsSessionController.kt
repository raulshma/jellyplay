package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.notification.JellyPlayNewMediaNotifier
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.JellyPlayEventsRepository
import com.raulshma.jellyplay.core.data.repository.JellyPlayPluginEvent
import com.raulshma.jellyplay.core.data.repository.JellyPushRepository
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The lifecycle owner of the companion-plugin's live events face (ADR 0010):
 * keeps [JellyPlayEventsRepository] running for exactly one signed-in
 * session — starts on the auth-true edge, stops on sign-out — and maps the
 * decoded events onto the app's existing user surfaces.
 *
 * Lifecycle model is the [HomeSession]/[SessionCacheRegistry] idiom rather
 * than a per-shell coordinator: this is an application-scope singleton whose
 * collector runs on the SAME shared application scope the events repository's
 * stream job uses (Koin `createdAtStart`, one framework for singleton
 * collectors — no new scope invented), so Android and desktop shells are
 * covered by one wiring instead of duplicated MainViewModel /
 * DesktopShellServices starts.
 *
 * Gating (ADR 0010 — the registry is the ONLY gate): on each auth-true edge
 * the controller probes [JellyPlayPluginStatusStore.refresh], then collects
 * the [JellyPlayFeatureGate] — probe AVAILABLE AND the
 * [JellyPlayPluginFeatures.Events] key present AND the user's per-feature
 * toggle on. The gate is REACTIVE: the stream (+ device registration via
 * [JellyPlayEventsRepository.start], itself idempotent) starts on the open
 * edge and stops on the closed edge, so switching the `events` toggle in
 * settings tears the SSE session down (and back up) mid-session without a
 * re-auth. A plugin installed mid-session lights up on the next auth edge,
 * or through the stream loop's own re-probe once started.
 *
 * Event surfaces:
 *  - [JellyPlayPluginEvent.NewMedia] → the platform's local-notification
 *    seam ([JellyPlayNewMediaNotifier] — Android's tray path; desktop has no
 *    notifier registered and logs instead);
 *  - [JellyPlayPluginEvent.Broadcast] → the [JellyPlayBroadcastMessenger]
 *    seam (bridged to the shared UserMessageBus app-side — snackbar on
 *    phone/desktop, one-shot, server-supplied text);
 *  - SessionStarted / PlaybackStarted / UserLockedOut / Unknown → log only
 *    for now (no consumer surface yet).
 *
 * Lifecycle truth is the [JellyPlayEventsSessionPhase] machine
 * ([JellyPlayEventsSessionMachine] — the pure transition table, jvmTest-pinned)
 * since the recorded Tier-3 migration: the phases used to be ENCODED BY which
 * of the three `Job?` slots were live, with the stop/start races on the
 * null-checks held together by comment discipline. The jobs below are the
 * phase's EXECUTION now — the guards read the phase, and every phase write
 * sits at the same code position whose side effects it describes, so what
 * runs, when, and the cancellation order are byte-identical.
 */
class JellyPlayEventsSessionController(
    private val authRepository: AuthRepository,
    private val eventsRepository: JellyPlayEventsRepository,
    private val statusStore: JellyPlayPluginStatusStore,
    /** The per-feature gate — probe AND the user's `events` toggle (the ONE seam). */
    private val featureGate: JellyPlayFeatureGate,
    /** One-shot user-message seam (bridged to core/ui's UserMessageBus app-side). */
    private val broadcastMessenger: JellyPlayBroadcastMessenger?,
    /** Platform tray-notification seam; null where no local-notification surface exists. */
    private val newMediaNotifier: JellyPlayNewMediaNotifier?,
    /**
     * The push face (the plugin's push support): armed on the session's auth
     * edge, INDEPENDENT of the `events` gate — its own `push` gate collector
     * (ADR 0010: one feature key per feature, the registry is the only gate)
     * decides whether a registration happens, so a user with `events` off
     * and `push` on still arms push. Cancelled only here, on sign-out.
     */
    private val pushRepository: JellyPushRepository,
    scope: CoroutineScope,
) {

    private val appScope = scope

    /**
     * The session machine's phase — the ONE truth (the jobs below are its
     * execution, not its truth). Written only through
     * [JellyPlayEventsSessionMachine]'s transitions, each at the code position
     * whose side effects it describes. Writers: [start] (once per process,
     * possibly off-main — the app startup prewarm) and the auth collector
     * ([startSession]/[stopSession] thereafter) — the same single-writer
     * discipline the former job slots had. REVIEW FIX (defect cohort):
     * `@Volatile` — the former truth-check read `sessionJob?.isActive`,
     * which rode the Job's thread-safe internals; a plain var written from
     * [start]'s caller thread and read/written inside the app-scope
     * collectors needs the visibility guarantee to keep that comparison
     * honest.
     */
    @Volatile
    private var phase: JellyPlayEventsSessionPhase = JellyPlayEventsSessionPhase.Frozen

    /**
     * The process-lifetime auth collector's handle (the `Frozen`→armed edge's
     * execution). Never cancelled and never read for truth — the phase guard
     * in [start] is the idempotence now; the collector lives and dies with
     * the application scope, as before.
     */
    private var sessionJob: Job? = null

    /**
     * The CURRENT signed-in session's gate collector (the
     * [JellyPlayEventsSessionPhase.ProbePending] /
     * [JellyPlayEventsSessionPhase.GateClosed] /
     * [JellyPlayEventsSessionPhase.EventsRunning] execution). Cancelled by
     * [stopSession] and replaced (cancel-then-launch) by a re-auth, so
     * sessions never stack duplicate gate collectors.
     */
    private var sessionGateJob: Job? = null

    /**
     * The start-stream + event-mapping job (the
     * [JellyPlayEventsSessionPhase.EventsRunning] execution), owned by the
     * gate: launched on every open edge, cancelled (with
     * [JellyPlayEventsRepository.stop]) on every closed edge — the toggle-off
     * teardown path.
     */
    private var eventsRunJob: Job? = null

    /**
     * Arms the auth-edge collector on the application scope. Safe to call
     * repeatedly (idempotent — [JellyPlayEventsSessionPhase.Frozen] is the
     * only admission, the former live-job check; the Koin `createdAtStart`
     * wiring calls this exactly once per process).
     */
    fun start() {
        if (phase != JellyPlayEventsSessionPhase.Frozen) return
        phase = JellyPlayEventsSessionMachine.onStart(phase)
        sessionJob = appScope.launch {
            // StateFlow already conflates + dedupes — distinctUntilChanged here
            // is a no-op the compiler flags (and this module fails on warnings).
            authRepository.isAuthenticated
                .collect { authenticated ->
                    if (authenticated) startSession() else stopSession()
                }
        }
    }

    /**
     * One signed-in session: probe, then ride the [JellyPlayFeatureGate]
     * reactively — stream + event mapping while the gate reads open (probe
     * AVAILABLE + `events` toggle on), teardown the moment it reads closed.
     * The gate collector job IS the session's owner handle, so
     * [stopSession] cancels the collector and the stream together.
     */
    private fun startSession() {
        // The auth-true edge: the phase leaves whatever session face it was in
        // and rides the probe window (the cancel-then-launch below is its
        // execution — same cancels, same order, as before).
        phase = JellyPlayEventsSessionMachine.onAuthTrue(phase)
        sessionGateJob?.cancel()
        eventsRunJob?.cancel()
        eventsRunJob = null
        sessionGateJob = appScope.launch {
            statusStore.refresh()
            if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) {
                phase = JellyPlayEventsSessionMachine.onProbeUnavailable(phase)
                return@launch
            }

            // Push arms with the SESSION, not the events gate: its repository
            // rides its OWN `push` gate (ADR 0010 — one key per feature), so
            // a user with `events` off and `push` on still registers. The
            // plain device registration below POSTs with the push field
            // omitted (the server keeps its push state), and the two POSTs
            // are upserts of the same device record — whichever order they
            // land in, the push half survives.
            pushRepository.start()
            // Probe AVAILABLE: the gate collector is armed on the reactive
            // gate, the stream not yet up.
            phase = JellyPlayEventsSessionMachine.onProbeAvailable(phase)

            featureGate.isAvailable(JellyPlayPluginFeatures.Events).collect { available ->
                if (available) {
                    // Replace (never stack) — only reachable after a closed
                    // edge cancelled the previous run.
                    eventsRunJob?.cancel()
                    eventsRunJob = launch {
                        eventsRepository.start()
                        eventsRepository.events.collect(::present)
                    }
                    phase = JellyPlayEventsSessionMachine.onGateOpened(phase)
                } else {
                    eventsRunJob?.cancel()
                    eventsRunJob = null
                    eventsRepository.stop()
                    phase = JellyPlayEventsSessionMachine.onGateClosed(phase)
                }
            }
        }
    }

    /**
     * Sign-out (the auth-false edge): the recorded teardown — gate collector
     * cancelled, both repositories stopped, phase `TornDown` (a false edge
     * with nothing ever serviced keeps `Unauthenticated`; the executor's
     * stops run idempotently either way, byte-identically).
     */
    private fun stopSession() {
        phase = JellyPlayEventsSessionMachine.onAuthFalse(phase)
        sessionGateJob?.cancel()
        sessionGateJob = null
        eventsRunJob?.cancel()
        eventsRunJob = null
        eventsRepository.stop()
        pushRepository.stop()
    }

    /** Event → surface mapping. See the class KDoc for the table. */
    private fun present(event: JellyPlayPluginEvent) {
        when (event) {
            is JellyPlayPluginEvent.NewMedia ->
                if (newMediaNotifier != null) {
                    newMediaNotifier.notifyNewMedia(event)
                } else {
                    Log.d(TAG, "New media push (no notifier on this platform): ${event.title}")
                }

            is JellyPlayPluginEvent.Broadcast -> {
                val text = if (event.body.isBlank()) event.title else "${event.title}: ${event.body}"
                if (broadcastMessenger != null) {
                    broadcastMessenger.showBroadcast(text)
                } else {
                    Log.d(TAG, "Broadcast (no messenger on this shell): $text")
                }
            }

            // No consumer surface yet — log-only by design.
            is JellyPlayPluginEvent.SessionStarted ->
                Log.d(TAG, "Session started: ${event.username}")
            is JellyPlayPluginEvent.PlaybackStarted ->
                Log.d(TAG, "Playback started: ${event.username}")
            is JellyPlayPluginEvent.UserLockedOut ->
                Log.d(TAG, "User locked out: ${event.username}")
            is JellyPlayPluginEvent.Unknown ->
                Log.d(TAG, "Unknown plugin event: ${event.event}")
        }
    }

    private companion object {
        const val TAG = "JellyPlayEventsSession"
    }
}
