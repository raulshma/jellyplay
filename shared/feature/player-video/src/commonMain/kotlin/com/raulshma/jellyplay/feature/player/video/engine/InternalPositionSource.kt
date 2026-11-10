package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.flow.Flow

/**
 * Capability contract: an engine that exposes ONE shared, replay-1 position
 * flow for INTERNAL consumers (in-process session adapters and any future
 * engine-module reader). The public [MediaEngine.positionFlow] stays a cold
 * flow by contract — every collector spawns its own [EnginePositionTicker]
 * there — so each additional internal collector riding the public flow would
 * multiply ticker wake-ups (and the per-tick readback work the tick bodies
 * do) by the collector count. Internal consumers take [internalPositionFlow]
 * instead: one upstream ticker per engine instance, fanned out with replay 1.
 * A capability interface (not a concrete-engine type test) so an engine can
 * opt in without extending the reloadable base — consumers type-test the
 * capability, never the concrete adapter.
 */
interface InternalPositionSource {
    /**
     * The shared internal position stream. Replay 1 so a late subscriber sees
     * the last playhead immediately; the upstream runs only while subscribers
     * exist (see the implementation's [kotlinx.coroutines.flow.SharingStarted] choice).
     */
    val internalPositionFlow: Flow<Long>
}
