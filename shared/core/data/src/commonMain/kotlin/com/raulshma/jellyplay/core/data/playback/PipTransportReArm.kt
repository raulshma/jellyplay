package com.raulshma.jellyplay.core.data.playback

/**
 * THE one re-arm + assignment path both player hosts (`player-video`,
 * `player-live`) share for [PipController.pipTransport] — previously two
 * verbatim registerPipTransport copies, one per host ViewModel, each carrying
 * its own explanation of why the bridge needs re-arming.
 *
 * Re-arm is a LIFECYCLE requirement, not paranoia (the rationale both copies
 * used to duplicate): both player ViewModels are Activity-scoped (Nav3 has no
 * per-entry ViewModelStore here, so the instance is reused across media and
 * `init` never re-runs on a screen re-entry), while teardown —
 * stop()/release() → [PipController.reset] — nulls the transport. Whoever
 * re-arms (VOD: VM init plus every load via `SessionLifecycleHooks.
 * rearmTransports`; live: every engine creation in `ensureEngine`), the bridge
 * must be re-armed after every teardown or the PiP window's remote controls go
 * dead: the host Activity dispatches remote actions through the nullable
 * transport and cannot restore it itself.
 *
 * [pip] is nullable because live's constructor takes `PipController?`
 * (platforms without PiP bind null there); a null controller is a no-op.
 *
 * The per-action MAPPING stays host-owned: [handler] receives each
 * [PipAction] and owns the routing (engine resolution and its drop policy,
 * SyncPlay/cast funnels, channel zaps — see each host's registerPipTransport).
 * This helper owns only the null-guard + assignment choreography, so the two
 * hosts cannot drift on the re-arm mechanics while their action semantics
 * stay free to differ (VOD routes through its funnels, live commands the
 * engine directly and zaps channels).
 */
fun reArmPipTransport(pip: PipController?, handler: (PipAction) -> Unit) {
    if (pip == null) return
    pip.pipTransport = PipTransport { action -> handler(action) }
}
