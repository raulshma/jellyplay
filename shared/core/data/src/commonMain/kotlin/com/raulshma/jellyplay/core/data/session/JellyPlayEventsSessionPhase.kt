package com.raulshma.jellyplay.core.data.session

/**
 * The lifecycle phases of [JellyPlayEventsSessionController] — the ONE enum
 * owning the session machine's transitions (the recorded Tier-3 finding: the
 * phases used to be ENCODED BY which of the controller's three `Job?` slots
 * were live, with the stop/start races on the null-checks held together by
 * comment discipline alone). The jobs still exist, but they are the phase's
 * EXECUTION now, not its truth: the controller's guards read the phase, and
 * every phase write goes through [JellyPlayEventsSessionMachine]'s total
 * transition table (pure, jvmTest-pinned in `JellyPlayEventsSessionMachineTest`).
 *
 * The variant set is verified from the code's resting shapes (it extends the
 * recorded survey's sketch with the two states the sketch missed):
 *
 *  - `Frozen` — [JellyPlayEventsSessionController.start] has not run: no auth
 *    collector exists (the Koin/app-startup consumers arm it exactly once per
 *    process);
 *  - `Unauthenticated` — the auth collector is armed, no signed-in session has
 *    been serviced yet (the brief post-start resting shape; a sign-out with
 *    nothing up stays here);
 *  - `ProbePending` — an auth-true edge is being serviced: the gate collector
 *    is launched, the plugin probe in flight;
 *  - `PluginUnavailable` — the probe resolved non-AVAILABLE and the gate
 *    collector exited: the session is armed but plugin-less until the next
 *    auth edge (the recorded sketch's missing resting state);
 *  - `GateClosed` — probe AVAILABLE, the gate collector rides the reactive
 *    feature gate, the events stream is NOT running (toggle off, or the
 *    pre-first-emission window);
 *  - `EventsRunning` — the events stream + event-mapping job is live under an
 *    open gate;
 *  - `TornDown` — sign-out tore a session down (gate collector cancelled,
 *    both repositories stopped); a re-auth restarts from here.
 *
 * INTERNAL to the module: the controller exposes no phase surface — the
 * machine is the internal truth that replaces the job-liveness encoding.
 */
internal enum class JellyPlayEventsSessionPhase {
    Frozen,
    Unauthenticated,
    ProbePending,
    PluginUnavailable,
    GateClosed,
    EventsRunning,
    TornDown,
}

/**
 * The pure transition table of the [JellyPlayEventsSessionPhase] machine —
 * total functions from (current phase, event) to the next phase, each
 * byte-equal to the code position that writes it in the controller
 * (`JellyPlayEventsSessionMachineTest` pins the full table). The controller is
 * the executor: the cancels, the repository stops, and the launches happen at
 * the same call sites as before, in the same order — only the TRUTH they used
 * to encode changes hands.
 */
internal object JellyPlayEventsSessionMachine {

    /**
     * [JellyPlayEventsSessionController.start]: arms the machine exactly once.
     * `Frozen` is the only admission (the former `sessionJob?.isActive`
     * idempotence — a re-`start` from any later phase is the same no-op the
     * live-job check delivered); the first auth edge lands the true resting
     * phase.
     */
    fun onStart(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        if (current == JellyPlayEventsSessionPhase.Frozen) {
            JellyPlayEventsSessionPhase.Unauthenticated
        } else {
            current
        }

    /**
     * The auth-true edge ([JellyPlayEventsSessionController.startSession]):
     * cancel-then-launch — whatever session face existed is replaced by the
     * probe window. Legal from every post-start phase (a re-auth after
     * `TornDown`/`PluginUnavailable` is the restart; a true edge over an
     * open session cannot arise from a conflated StateFlow, and the executor's
     * cancel-then-launch makes it harmless if it ever does).
     */
    fun onAuthTrue(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        JellyPlayEventsSessionPhase.ProbePending

    /**
     * The auth-false edge ([JellyPlayEventsSessionController.stopSession], the
     * teardown): a false edge with no session face ever serviced stays
     * `Unauthenticated` (nothing was torn — the executor still runs its
     * idempotent repository stops, byte-identically); from any
     * session-carrying phase the edge is the recorded `TornDown`.
     */
    fun onAuthFalse(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        if (current == JellyPlayEventsSessionPhase.Unauthenticated) {
            JellyPlayEventsSessionPhase.Unauthenticated
        } else {
            JellyPlayEventsSessionPhase.TornDown
        }

    /**
     * The probe resolved AVAILABLE: the gate collector is armed on the
     * reactive feature gate with the events stream not yet up.
     */
    fun onProbeAvailable(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        JellyPlayEventsSessionPhase.GateClosed

    /**
     * The probe resolved non-AVAILABLE: the gate collector exits; the armed
     * session waits plugin-less for the next auth edge.
     */
    fun onProbeUnavailable(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        JellyPlayEventsSessionPhase.PluginUnavailable

    /**
     * The gate opened (probe AVAILABLE AND the `events` toggle on): the
     * events run job is (re-)launched — replace-never-stack. Idempotent over
     * `EventsRunning` (a re-true emission re-enters the same phase).
     */
    fun onGateOpened(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        JellyPlayEventsSessionPhase.EventsRunning

    /**
     * The gate closed (the toggle went off): the events run job is cancelled
     * and the events repository stopped; the gate collector stays armed.
     */
    fun onGateClosed(current: JellyPlayEventsSessionPhase): JellyPlayEventsSessionPhase =
        JellyPlayEventsSessionPhase.GateClosed
}
