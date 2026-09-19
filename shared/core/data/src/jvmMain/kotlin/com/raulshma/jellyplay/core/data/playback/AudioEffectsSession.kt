package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig

/**
 * The desktop audio queue manager's effects seam — the narrow port
 * [DesktopAudioQueueManager] needs from the effects stack it pushes onto
 * its engine: the live-mutation hook ([onEffectsChanged]), the per-track
 * ReplayGain context feed ([applyReplayGainForTrack], at the same two
 * sites Android's manager applies it — explicit play + advance), and the
 * engine-config fold ([snapshotConfig]) the manager re-pushes on every
 * change.
 *
 * Extracted when the manager relocated into this module (jvmMain): its
 * historical ctor param was the app-side `DesktopAudioEffectsManager`
 * (apps/desktop), which a core:data type cannot reference. That class is
 * still the production implementation — it now satisfies this interface —
 * and plain queue-semantics tests substitute a state-core-backed fake.
 *
 * JVM-only on purpose (jvmMain, not commonMain): Android's manager applies
 * effects through its media3 DSP internals and has no such push seam, so
 * the port has exactly one production shape.
 */
interface AudioEffectsSession {

    /** Live-mutation hook — the manager (re-)pushes [snapshotConfig] on fire. */
    var onEffectsChanged: (() -> Unit)?

    /** Per-track ReplayGain context (the core's setReplayGainContext). */
    fun applyReplayGainForTrack(trackGainDb: Float?, isShuffled: Boolean)

    /** The whole effects state machine folded into the engine config. */
    fun snapshotConfig(): AudioEffectsConfig
}
